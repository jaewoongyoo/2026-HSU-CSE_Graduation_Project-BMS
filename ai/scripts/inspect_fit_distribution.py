"""fit_x_values, fit_soh_values의 분포를 확인해 피팅이 왜 RMSE 0.23인지 본다."""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

AI_ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = AI_ROOT / "data" / "interim" / "canonical_nasa_calce_v1"

metadata_rows = pq.read_table(ARTIFACT_DIR / "metadata.parquet").to_pylist()
seq_rows = pq.read_table(ARTIFACT_DIR / "sequences.parquet").to_pylist()
split_rows = pq.read_table(ARTIFACT_DIR / "splits.parquet").to_pylist()

split_by_source_id = {row["source_id"]: row["split"] for row in split_rows}

selected_metadata = [
    row
    for row in metadata_rows
    if row["dataset_id"] == "nasa"
    and split_by_source_id.get(row["source_id"]) == "train"
    and row["soh_ratio"] is not None
    and row["baseline_capacity_ah"] is not None
    and row["sequence_phase"] == "charge"
]

sequences_by_source_id: dict[str, list[dict]] = defaultdict(list)
for row in seq_rows:
    sequences_by_source_id[row["source_id"]].append(row)

metadata_by_cell: dict[tuple[str, str], list[dict]] = defaultdict(list)
for meta_row in selected_metadata:
    key = (meta_row["dataset_id"], meta_row["cell_id"])
    metadata_by_cell[key].append(meta_row)


def _integrate(cycle_rows: list[dict]) -> float:
    cycle_rows = sorted(cycle_rows, key=lambda r: float(r["time_s"]))
    if len(cycle_rows) < 2:
        return 0.0
    times = np.array([float(r["time_s"]) for r in cycle_rows])
    volts = np.array([float(r["voltage_v"]) for r in cycle_rows])
    currs = np.array([float(r["current_a"]) for r in cycle_rows])
    charge_currs = np.where(currs > 0.0, currs, 0.0)
    return float(np.trapezoid(volts * charge_currs, times)) / 3600.0


print(f"[cells] {len(metadata_by_cell)}개 cell")
print()
print(f"{'cell':<15} {'cycles':<8} {'soh_min':<10} {'soh_max':<10} {'x_max':<10} {'baseline_ah':<12}")
print("-" * 70)

all_soh = []
all_x = []
for (dataset_id, cell_id), rows in sorted(metadata_by_cell.items(), key=lambda kv: kv[0][1]):
    rows.sort(key=lambda r: r["cycle_index"] if r["cycle_index"] is not None else 0)
    baseline_ah = float(rows[0]["baseline_capacity_ah"])
    nominal_wh = baseline_ah * 3.7
    cumulative = 0.0
    cell_soh = []
    cell_x = []
    for meta in rows:
        sid = meta["source_id"]
        cyc_rows = sequences_by_source_id.get(sid, [])
        e_wh = _integrate(cyc_rows)
        if e_wh <= 0.0:
            continue
        cumulative += e_wh
        x = cumulative / nominal_wh
        soh = float(meta["soh_ratio"])
        if 0.0 < soh <= 1.2 and x >= 0.0:
            cell_soh.append(soh)
            cell_x.append(x)
            all_soh.append(soh)
            all_x.append(x)
    if cell_soh:
        print(f"{cell_id:<15} {len(cell_soh):<8} {min(cell_soh):<10.4f} {max(cell_soh):<10.4f} {max(cell_x):<10.2f} {baseline_ah:<12.4f}")

print()
print(f"[total] {len(all_x)} fit points")
print(f"[x] min={min(all_x):.2f}, median={np.median(all_x):.2f}, max={max(all_x):.2f}, p95={np.percentile(all_x, 95):.2f}")
print(f"[soh] min={min(all_soh):.4f}, median={np.median(all_soh):.4f}, max={max(all_soh):.4f}")
print()
print("[soh distribution]")
bins = [0.0, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2]
hist, _ = np.histogram(all_soh, bins=bins)
for i in range(len(bins) - 1):
    bar = "#" * int(hist[i] / max(hist) * 40)
    print(f"  {bins[i]:.2f} ~ {bins[i+1]:.2f}: {hist[i]:5d} {bar}")
