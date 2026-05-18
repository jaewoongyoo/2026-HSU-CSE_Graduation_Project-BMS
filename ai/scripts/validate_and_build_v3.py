"""
RDS raw 데이터 검증 및 V3 피처 생성 스크립트.

입력:
    data/raw/real_observations/raw_sessions.parquet
    data/raw/real_observations/raw_telemetry.parquet

출력:
    data/interim/real_observations/sessions_v3.parquet   -- 유효 세션 메타데이터
    data/interim/real_observations/telemetry_v3.parquet  -- 검증된 텔레메트리 + V3 피처

세션 레벨 필터:
    - elapsed_max >= 10분 (600,000ms)
    - soc_start < 80%
    - soc_end < 90%
    - soc_delta > 0 (SOC 상승 세션만)
    - current_mean > 50mA

행 레벨 필터:
    - current_ma < -1000mA 행 제거

V3 피처:
    - power_w                    = voltage * current_ma / 1000
    - cumulative_energy_wh       = 트라포조이드 적분 (10초 이상 갭은 구간 분리)
    - effective_output_power_w   = power_w * 0.85
    - estimated_output_current_5v_a = effective_output_power_w / 5.0

실행:
    .venv/bin/python scripts/validate_and_build_v3.py
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from soh_service.preprocessing.observation import (
    compute_cumulative_energy_wh,
    compute_effective_output_power_series,
    compute_estimated_output_current_series,
    compute_power_series,
)

RAW_DIR = AI_ROOT / "data" / "raw" / "real_observations"
OUT_DIR = AI_ROOT / "data" / "interim" / "real_observations"

MIN_DURATION_MS = 10 * 60 * 1000   # 10분
MAX_SOC_START = 80.0
MAX_SOC_END = 90.0
MIN_SOC_DELTA = 0.0
MIN_CURRENT_MEAN_MA = 50.0
CURRENT_OUTLIER_THRESHOLD_MA = -1000.0
GAP_THRESHOLD_MS = 10_000.0         # 10초


def _compute_v3_features(df: pd.DataFrame) -> pd.DataFrame:
    """단일 세션 데이터프레임에 V3 피처 4개를 추가해서 반환."""
    df = df.copy()

    voltage_v = df["voltage"].tolist()
    current_a = (df["current_ma"] / 1000.0).tolist()

    power_w = compute_power_series(voltage_v, current_a)
    df["power_w"] = power_w

    # cumulative_energy_wh: 10초 이상 갭 구간은 분리해서 적분
    elapsed_s = (df["elapsed_ms"] / 1000.0).tolist()
    cumulative_wh = _cumulative_energy_with_gap_split(elapsed_s, power_w)
    df["cumulative_energy_wh"] = cumulative_wh

    effective_power = compute_effective_output_power_series(power_w)
    df["effective_output_power_w"] = effective_power

    df["estimated_output_current_5v_a"] = compute_estimated_output_current_series(effective_power)

    return df


def _cumulative_energy_with_gap_split(
    time_values_s: list[float],
    power_values_w: list[float],
) -> list[float]:
    """트라포조이드 적분. GAP_THRESHOLD_MS 이상 간격은 해당 구간 기여를 0으로 처리."""
    gap_threshold_s = GAP_THRESHOLD_MS / 1000.0
    result = [0.0]

    for i in range(1, len(time_values_s)):
        dt = time_values_s[i] - time_values_s[i - 1]
        if dt >= gap_threshold_s:
            # 갭 구간 — 에너지 기여 없이 이전 누적값 유지
            result.append(result[-1])
        else:
            increment_wh = (power_values_w[i - 1] + power_values_w[i]) * 0.5 * dt / 3600.0
            result.append(result[-1] + increment_wh)

    return result


def validate_and_build(dry_run: bool = False) -> None:
    sessions_path = RAW_DIR / "raw_sessions.parquet"
    telemetry_path = RAW_DIR / "raw_telemetry.parquet"

    for p in (sessions_path, telemetry_path):
        if not p.exists():
            print(f"파일 없음: {p}")
            sys.exit(1)

    sessions = pd.read_parquet(sessions_path)
    telemetry = pd.read_parquet(telemetry_path)

    print(f"원본: {len(sessions)}개 세션, {len(telemetry)}행 텔레메트리")

    # 세션별 텔레메트리 통계 계산
    stats = (
        telemetry.sort_values("elapsed_ms")
        .groupby("session_id")
        .agg(
            rows=("elapsed_ms", "count"),
            elapsed_max=("elapsed_ms", "max"),
            soc_start=("soc", "first"),
            soc_end=("soc", "last"),
            current_mean=("current_ma", "mean"),
        )
        .reset_index()
    )
    stats["soc_delta"] = stats["soc_end"] - stats["soc_start"]

    # 세션 레벨 필터
    valid_mask = (
        (stats["elapsed_max"] >= MIN_DURATION_MS) &
        (stats["soc_start"] < MAX_SOC_START) &
        (stats["soc_end"] < MAX_SOC_END) &
        (stats["soc_delta"] > MIN_SOC_DELTA) &
        (stats["current_mean"] > MIN_CURRENT_MEAN_MA)
    )
    valid_ids = stats.loc[valid_mask, "session_id"].tolist()

    print(f"\n[세션 레벨 필터]")
    for sid in stats["session_id"]:
        row = stats[stats["session_id"] == sid].iloc[0]
        reasons = []
        if row["elapsed_max"] < MIN_DURATION_MS:
            reasons.append(f"duration {row['elapsed_max']/60000:.1f}분 < 10분")
        if row["soc_start"] >= MAX_SOC_START:
            reasons.append(f"soc_start {row['soc_start']}% >= 80%")
        if row["soc_end"] >= MAX_SOC_END:
            reasons.append(f"soc_end {row['soc_end']}% >= 90%")
        if row["soc_delta"] <= MIN_SOC_DELTA:
            reasons.append(f"soc_delta {row['soc_delta']:.1f} <= 0")
        if row["current_mean"] <= MIN_CURRENT_MEAN_MA:
            reasons.append(f"current_mean {row['current_mean']:.0f}mA <= 50mA")
        status = "✅" if not reasons else "❌ " + ", ".join(reasons)
        print(f"  session_id={sid}: {status}")

    print(f"\n유효 세션: {len(valid_ids)}개 {valid_ids}")

    if not valid_ids:
        print("유효 세션 없음. 종료.")
        return

    # 유효 세션 텔레메트리만 추출
    tel = telemetry[telemetry["session_id"].isin(valid_ids)].copy()

    # 행 레벨 필터: current_ma < -1000mA 제거
    before = len(tel)
    tel = tel[tel["current_ma"] >= CURRENT_OUTLIER_THRESHOLD_MA]
    removed = before - len(tel)
    print(f"\n[행 레벨 필터] current_ma < {CURRENT_OUTLIER_THRESHOLD_MA:.0f}mA 제거: {removed}행")

    # V3 피처 계산 (세션별)
    print("\n[V3 피처 계산]")
    frames: list[pd.DataFrame] = []
    for sid in valid_ids:
        df = tel[tel["session_id"] == sid].sort_values("elapsed_ms").copy()
        df = _compute_v3_features(df)
        total_wh = df["cumulative_energy_wh"].iloc[-1]
        print(f"  session_id={sid}: {len(df)}행, 총 에너지={total_wh:.4f}Wh")
        frames.append(df)

    telemetry_v3 = pd.concat(frames, ignore_index=True)
    sessions_v3 = sessions[sessions["id"].isin(valid_ids)].copy()

    print(f"\n결과: {len(sessions_v3)}개 세션, {len(telemetry_v3)}행")

    if dry_run:
        print("\n[dry-run] 저장 생략")
        return

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    sessions_v3.to_parquet(OUT_DIR / "sessions_v3.parquet", index=False)
    telemetry_v3.to_parquet(OUT_DIR / "telemetry_v3.parquet", index=False)
    print(f"\n저장 완료: {OUT_DIR}")
    print(f"  sessions_v3.parquet : {len(sessions_v3)}개 세션")
    print(f"  telemetry_v3.parquet: {len(telemetry_v3)}행")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="저장 없이 결과만 출력")
    args = parser.parse_args()
    validate_and_build(dry_run=args.dry_run)


if __name__ == "__main__":
    main()
