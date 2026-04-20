"""canonical sequence에서 전압/전류/시간 값의 실제 범위를 확인한다."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

AI_ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = AI_ROOT / "data" / "interim" / "canonical_nasa_calce_v1"

metadata_rows = pq.read_table(ARTIFACT_DIR / "metadata.parquet").to_pylist()
seq_rows = pq.read_table(ARTIFACT_DIR / "sequences.parquet").to_pylist()

# 첫 번째 cell의 첫 사이클만 뽑아서 값 확인
first_meta = metadata_rows[0]
first_source_id = first_meta["source_id"]

matching = [r for r in seq_rows if r["source_id"] == first_source_id]
print(f"[sample cell] source_id = {first_source_id}")
print(f"[sample cell] cell_id = {first_meta['cell_id']}")
print(f"[sample cell] cycle_index = {first_meta['cycle_index']}")
print(f"[sample cell] baseline_capacity_ah = {first_meta['baseline_capacity_ah']:.4f}")
print(f"[sample cell] sequence rows: {len(matching)}")

if matching:
    time_s = np.array([r["time_s"] for r in matching])
    voltage_v = np.array([r["voltage_v"] for r in matching])
    current_a = np.array([r["current_a"] for r in matching])

    print("\n[time_s]")
    print(f"  min = {time_s.min():.4f}, max = {time_s.max():.4f}, duration = {time_s.max() - time_s.min():.2f}s")
    print("\n[voltage_v]")
    print(f"  min = {voltage_v.min():.4f}, max = {voltage_v.max():.4f}, mean = {voltage_v.mean():.4f}")
    print("\n[current_a]")
    print(f"  min = {current_a.min():.4f}, max = {current_a.max():.4f}, mean = {current_a.mean():.4f}, abs_mean = {np.abs(current_a).mean():.4f}")

    # 첫 10개, 마지막 5개 샘플
    print("\n[first 5 rows]")
    for r in matching[:5]:
        print(f"  t={r['time_s']:.2f}s  V={r['voltage_v']:.4f}  I={r['current_a']:.4f}")
    print("\n[last 3 rows]")
    for r in matching[-3:]:
        print(f"  t={r['time_s']:.2f}s  V={r['voltage_v']:.4f}  I={r['current_a']:.4f}")

    # 적분 결과
    power_w = voltage_v * np.abs(current_a)
    energy_ws = float(np.trapezoid(power_w, time_s))
    energy_wh = energy_ws / 3600.0
    print(f"\n[integrated] energy = {energy_wh:.4f} Wh")

    baseline_ah = float(first_meta["baseline_capacity_ah"])
    nominal_wh = baseline_ah * 3.7
    print(f"[ratio] energy / nominal = {energy_wh / nominal_wh:.4f}  (정상이면 1 사이클은 ~1.0 근처)")
