"""
도메인 갭 분석 스크립트 (Level 1~3).

입력:
    data/interim/canonical_nasa_calce_v1/sequences.parquet  -- Lab (NASA+CALCE)
    data/interim/real_observations/telemetry_v3.parquet     -- Real

출력:
    artifacts/reports/domain_gap/level1_distributions.png
    artifacts/reports/domain_gap/level2_curves.png
    artifacts/reports/domain_gap/level3_windows.png
    artifacts/reports/domain_gap/metrics.json

실행:
    .venv/bin/python scripts/run_domain_gap.py
    .venv/bin/python scripts/run_domain_gap.py --levels 1 2   # 특정 레벨만
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy.stats import wasserstein_distance

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from soh_service.preprocessing.observation import (
    compute_cumulative_energy_wh,
    compute_effective_output_power_series,
    compute_estimated_output_current_series,
    compute_power_series,
)

LAB_SEQUENCES = AI_ROOT / "data" / "interim" / "canonical_nasa_calce_v1" / "sequences.parquet"
REAL_TELEMETRY = AI_ROOT / "data" / "interim" / "real_observations" / "telemetry_v3.parquet"
OUT_DIR = AI_ROOT / "artifacts" / "reports" / "domain_gap"

V3_FEATURES = [
    "power_w",
    "cumulative_energy_wh",
    "effective_output_power_w",
    "estimated_output_current_5v_a",
]

WINDOW_STEPS = 30
RESAMPLE_INTERVAL_S = 20.0


# ──────────────────────────────────────────────
# 데이터 로드
# ──────────────────────────────────────────────

def load_lab_data() -> pd.DataFrame:
    """canonical sequences → V3 피처 계산 후 반환 (sample_index 단위)."""
    seq = pd.read_parquet(LAB_SEQUENCES)
    seq = seq[seq["raw_phase_hint"] == "charge"].copy()

    frames: list[pd.DataFrame] = []
    for sid, group in seq.groupby("sample_index"):
        g = group.sort_values("time_s").copy()
        if len(g) < 5:
            continue

        time_s = g["time_s"].tolist()
        voltage_v = g["voltage_v"].tolist()
        current_a = g["current_a"].tolist()

        power_w = compute_power_series(voltage_v, current_a)
        cumulative_wh = compute_cumulative_energy_wh(time_s, power_w)
        effective_w = compute_effective_output_power_series(power_w)
        output_a = compute_estimated_output_current_series(effective_w)

        g["power_w"] = power_w
        g["cumulative_energy_wh"] = cumulative_wh
        g["effective_output_power_w"] = effective_w
        g["estimated_output_current_5v_a"] = output_a
        frames.append(g)

    return pd.concat(frames, ignore_index=True)


def load_real_data() -> pd.DataFrame:
    return pd.read_parquet(REAL_TELEMETRY)


# ──────────────────────────────────────────────
# Level 1: V3 피처 분포 비교
# ──────────────────────────────────────────────

def run_level1(lab: pd.DataFrame, real: pd.DataFrame) -> dict[str, float]:
    """KDE 오버레이 + Wasserstein Distance. cumulative_energy_wh는 세션 최종값으로 비교."""
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle("Level 1 — V3 피처 분포 비교 (Lab vs Real)", fontsize=14)
    axes = axes.flatten()

    wasserstein: dict[str, float] = {}

    for ax, feat in zip(axes, V3_FEATURES):
        if feat == "cumulative_energy_wh":
            lab_vals = lab.groupby("sample_index")["cumulative_energy_wh"].last().values
            real_vals = real.groupby("session_id")["cumulative_energy_wh"].last().values
            xlabel = "총 누적 에너지 (Wh, 세션 최종값)"
        else:
            lab_vals = lab[feat].dropna().values
            real_vals = real[feat].dropna().values
            xlabel = feat

        wd = wasserstein_distance(lab_vals, real_vals)
        wasserstein[feat] = round(float(wd), 6)

        lo = min(np.percentile(lab_vals, 1), np.percentile(real_vals, 1))
        hi = max(np.percentile(lab_vals, 99), np.percentile(real_vals, 99))
        xs = np.linspace(lo, hi, 300)

        from scipy.stats import gaussian_kde
        kde_lab = gaussian_kde(lab_vals)
        kde_real = gaussian_kde(real_vals)

        ax.plot(xs, kde_lab(xs), label=f"Lab (n={len(lab_vals):,})", color="steelblue")
        ax.fill_between(xs, kde_lab(xs), alpha=0.2, color="steelblue")
        ax.plot(xs, kde_real(xs), label=f"Real (n={len(real_vals):,})", color="tomato")
        ax.fill_between(xs, kde_real(xs), alpha=0.2, color="tomato")

        ax.set_title(f"{feat}\nWasserstein={wd:.4f}", fontsize=10)
        ax.set_xlabel(xlabel, fontsize=8)
        ax.set_ylabel("밀도", fontsize=8)
        ax.legend(fontsize=8)

        stats_text = (
            f"Lab  mean={np.mean(lab_vals):.3f} std={np.std(lab_vals):.3f}\n"
            f"Real mean={np.mean(real_vals):.3f} std={np.std(real_vals):.3f}"
        )
        ax.text(0.97, 0.97, stats_text, transform=ax.transAxes,
                fontsize=7, va="top", ha="right",
                bbox=dict(boxstyle="round,pad=0.3", fc="white", alpha=0.7))

    plt.tight_layout()
    out_path = OUT_DIR / "level1_distributions.png"
    plt.savefig(out_path, dpi=150)
    plt.close()
    print(f"  저장: {out_path}")
    return wasserstein


# ──────────────────────────────────────────────
# Level 2: 충전 곡선 형태 비교
# ──────────────────────────────────────────────

def _normalize_session(
    time_s: np.ndarray,
    values: np.ndarray,
    n_points: int = 100,
) -> tuple[np.ndarray, np.ndarray]:
    t_norm = (time_s - time_s[0]) / (time_s[-1] - time_s[0] + 1e-9)
    xs = np.linspace(0, 1, n_points)
    ys = np.interp(xs, t_norm, values)
    return xs, ys


def run_level2(lab: pd.DataFrame, real: pd.DataFrame, n_lab_samples: int = 30) -> None:
    """시간 정규화 후 power_w / effective_output_power_w 곡선 오버레이."""
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle("Level 2 — 충전 곡선 형태 비교 (시간 정규화)", fontsize=13)

    curves = {
        "power_w": "파워뱅크 출력 전력 (W)",
        "effective_output_power_w": "유효 출력 전력 (W)",
    }

    rng = np.random.default_rng(42)
    lab_ids = lab["sample_index"].unique()
    if len(lab_ids) > n_lab_samples:
        lab_ids = rng.choice(lab_ids, size=n_lab_samples, replace=False)

    for ax, (feat, ylabel) in zip(axes, curves.items()):
        # Lab 곡선
        for sid in lab_ids:
            g = lab[lab["sample_index"] == sid].sort_values("time_s")
            if len(g) < 10:
                continue
            _, ys = _normalize_session(g["time_s"].values, g[feat].values)
            ax.plot(np.linspace(0, 1, 100), ys,
                    color="steelblue", alpha=0.15, linewidth=0.8)

        # Real 곡선
        real_ids = real["session_id"].unique()
        for sid in real_ids:
            g = real[real["session_id"] == sid].sort_values("elapsed_ms")
            if len(g) < 10:
                continue
            time_s = g["elapsed_ms"].values / 1000.0
            _, ys = _normalize_session(time_s, g[feat].values)
            ax.plot(np.linspace(0, 1, 100), ys,
                    color="tomato", alpha=0.8, linewidth=1.5,
                    label=f"Real session {sid}")

        from matplotlib.lines import Line2D
        handles = [
            Line2D([0], [0], color="steelblue", alpha=0.6, linewidth=1.5,
                   label=f"Lab (n={len(lab_ids)})"),
            Line2D([0], [0], color="tomato", linewidth=1.5,
                   label=f"Real (n={len(real_ids)})"),
        ]
        ax.legend(handles=handles, fontsize=8)
        ax.set_xlabel("정규화된 시간 (0=시작, 1=종료)", fontsize=9)
        ax.set_ylabel(ylabel, fontsize=9)
        ax.set_title(feat, fontsize=10)

    plt.tight_layout()
    out_path = OUT_DIR / "level2_curves.png"
    plt.savefig(out_path, dpi=150)
    plt.close()
    print(f"  저장: {out_path}")


# ──────────────────────────────────────────────
# Level 3: Window 단위 피처 분포 비교
# ──────────────────────────────────────────────

def _build_windows(
    time_s: np.ndarray,
    feature_matrix: np.ndarray,
    resample_s: float = RESAMPLE_INTERVAL_S,
    n_steps: int = WINDOW_STEPS,
) -> np.ndarray:
    """20s 리샘플링 후 비중첩 30-step 윈도우로 분할. shape: (n_windows, n_steps, n_features)"""
    t_start, t_end = time_s[0], time_s[-1]
    if t_end - t_start < resample_s * n_steps:
        return np.empty((0, n_steps, feature_matrix.shape[1]))

    t_grid = np.arange(t_start, t_end, resample_s)
    resampled = np.stack(
        [np.interp(t_grid, time_s, feature_matrix[:, i])
         for i in range(feature_matrix.shape[1])],
        axis=1,
    )

    n_windows = len(t_grid) // n_steps
    windows = []
    for i in range(n_windows):
        windows.append(resampled[i * n_steps: (i + 1) * n_steps])
    return np.array(windows) if windows else np.empty((0, n_steps, feature_matrix.shape[1]))


def run_level3(lab: pd.DataFrame, real: pd.DataFrame) -> dict[str, float]:
    """Window 단위 평균 분포 비교 (violin plot) + Wasserstein Distance."""
    feat_cols = [f for f in V3_FEATURES if f != "cumulative_energy_wh"]

    def extract_window_means(df: pd.DataFrame, time_col: str, group_col: str) -> pd.DataFrame:
        rows = []
        for gid, g in df.groupby(group_col):
            g = g.sort_values(time_col)
            time_s = (g[time_col].values / 1000.0 if time_col == "elapsed_ms"
                      else g[time_col].values)
            feat_mat = g[feat_cols].values
            windows = _build_windows(time_s, feat_mat)
            for w in windows:
                row = {f: float(w[:, i].mean()) for i, f in enumerate(feat_cols)}
                row[group_col] = gid
                rows.append(row)
        return pd.DataFrame(rows)

    lab_wins = extract_window_means(lab, "time_s", "sample_index")
    real_wins = extract_window_means(real, "elapsed_ms", "session_id")

    print(f"  Lab 윈도우: {len(lab_wins)}개 / Real 윈도우: {len(real_wins)}개")

    wasserstein: dict[str, float] = {}
    fig, axes = plt.subplots(1, len(feat_cols), figsize=(14, 5))
    fig.suptitle(
        f"Level 3 — Window 단위 평균 분포 비교\n"
        f"(20s 리샘플 × 30-step, Lab={len(lab_wins)}창 / Real={len(real_wins)}창)",
        fontsize=12,
    )

    for ax, feat in zip(axes, feat_cols):
        lab_vals = lab_wins[feat].dropna().values
        real_vals = real_wins[feat].dropna().values

        wd = wasserstein_distance(lab_vals, real_vals)
        wasserstein[feat] = round(float(wd), 6)

        data_to_plot = [lab_vals, real_vals]
        vp = ax.violinplot(data_to_plot, positions=[1, 2], showmedians=True)
        vp["bodies"][0].set_facecolor("steelblue")
        vp["bodies"][0].set_alpha(0.6)
        vp["bodies"][1].set_facecolor("tomato")
        vp["bodies"][1].set_alpha(0.6)

        ax.set_xticks([1, 2])
        ax.set_xticklabels(["Lab", "Real"], fontsize=9)
        ax.set_title(f"{feat}\nWasserstein={wd:.4f}", fontsize=9)
        ax.set_ylabel("window 평균", fontsize=8)

    plt.tight_layout()
    out_path = OUT_DIR / "level3_windows.png"
    plt.savefig(out_path, dpi=150)
    plt.close()
    print(f"  저장: {out_path}")
    return wasserstein


# ──────────────────────────────────────────────
# main
# ──────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--levels", nargs="+", type=int, default=[1, 2, 3],
        help="실행할 레벨 (기본: 1 2 3)",
    )
    args = parser.parse_args()

    for p in (LAB_SEQUENCES, REAL_TELEMETRY):
        if not p.exists():
            print(f"파일 없음: {p}")
            sys.exit(1)

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print("Lab 데이터 로드 중...")
    lab = load_lab_data()
    print(f"  Lab: {lab['sample_index'].nunique()}개 샘플, {len(lab):,}행")

    print("Real 데이터 로드 중...")
    real = load_real_data()
    print(f"  Real: {real['session_id'].nunique()}개 세션, {len(real):,}행")

    metrics: dict[str, dict] = {}

    if 1 in args.levels:
        print("\n[Level 1] V3 피처 분포 비교...")
        wd = run_level1(lab, real)
        metrics["level1_wasserstein"] = wd
        print(f"  Wasserstein Distance: {wd}")

    if 2 in args.levels:
        print("\n[Level 2] 충전 곡선 형태 비교...")
        run_level2(lab, real)

    if 3 in args.levels:
        print("\n[Level 3] Window 단위 피처 분포 비교...")
        wd3 = run_level3(lab, real)
        metrics["level3_wasserstein"] = wd3
        print(f"  Wasserstein Distance: {wd3}")

    metrics_path = OUT_DIR / "metrics.json"
    with open(metrics_path, "w") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)
    print(f"\n지표 저장: {metrics_path}")
    print("완료.")


if __name__ == "__main__":
    main()
