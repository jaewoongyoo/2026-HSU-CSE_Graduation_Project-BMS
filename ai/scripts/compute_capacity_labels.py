"""
field_measurements CSV 에서 label_capacity_ah 계산 후 컬럼 추가.

계산식 (UM34C 가이드 기준):
    label_capacity_ah = (measured_mwh_total / 0.9) / 3.7 / 1000

    - 0.9  : 파워뱅크 내부 셀 → USB 출력 변환 효율
    - 3.7  : 리튬이온 셀 공칭 전압 (V)
    - 1000 : mAh → Ah 변환

실행:
    python scripts/compute_capacity_labels.py
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import pandas as pd

AI_ROOT = Path(__file__).resolve().parents[1]
CSV_PATH = AI_ROOT / "data" / "external" / "field_measurements" / "powerbank_capacity_measurements.csv"

CONVERTER_EFFICIENCY = 0.9
NOMINAL_VOLTAGE_V = 3.7


def compute_label_capacity_ah(measured_mwh: float) -> float:
    return (measured_mwh / CONVERTER_EFFICIENCY) / NOMINAL_VOLTAGE_V / 1000


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="계산 결과만 출력, 파일 저장 안 함")
    args = parser.parse_args()

    if not CSV_PATH.exists():
        print(f"파일 없음: {CSV_PATH}")
        sys.exit(1)

    df = pd.read_csv(CSV_PATH)

    if "measured_mwh_total" not in df.columns or "powerbank_capacity_mah" not in df.columns:
        print("필수 컬럼 누락: measured_mwh_total, powerbank_capacity_mah")
        sys.exit(1)

    df["label_capacity_ah"] = df["measured_mwh_total"].apply(compute_label_capacity_ah).round(4)

    print(df[["model_name", "powerbank_capacity_mah", "measured_mwh_total", "label_capacity_ah"]].to_string(index=False))

    if args.dry_run:
        print("\n[dry-run] 저장 생략")
        return

    df.to_csv(CSV_PATH, index=False)
    print(f"\n저장 완료: {CSV_PATH}")


if __name__ == "__main__":
    main()
