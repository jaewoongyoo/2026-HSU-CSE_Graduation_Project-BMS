"""Canonical artifact 내용 진단 — fit_standard_curve가 0개로 실패하는 원인 추적."""

from __future__ import annotations

import sys
from collections import Counter
from pathlib import Path

import pyarrow.parquet as pq

AI_ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = AI_ROOT / "data" / "interim" / "canonical_nasa_calce_v1"

print(f"[info] artifact_dir = {ARTIFACT_DIR}")
print(f"[info] exists = {ARTIFACT_DIR.exists()}")
for name in ("metadata.parquet", "sequences.parquet", "splits.parquet"):
    path = ARTIFACT_DIR / name
    print(f"[info] {name}: exists={path.exists()}, size={path.stat().st_size if path.exists() else 'N/A'}")

metadata_rows = pq.read_table(ARTIFACT_DIR / "metadata.parquet").to_pylist()
split_rows = pq.read_table(ARTIFACT_DIR / "splits.parquet").to_pylist()

print(f"\n[stats] total metadata rows: {len(metadata_rows)}")
print(f"[stats] total split rows:    {len(split_rows)}")

# dataset_id 분포
dataset_id_counts = Counter(row["dataset_id"] for row in metadata_rows)
print(f"\n[stats] dataset_id counts: {dict(dataset_id_counts)}")

# sequence_phase 분포
phase_counts = Counter(row["sequence_phase"] for row in metadata_rows)
print(f"[stats] sequence_phase counts: {dict(phase_counts)}")

# split 분포
split_by_source = {row["source_id"]: row["split"] for row in split_rows}
split_counts = Counter(split_by_source.values())
print(f"[stats] split counts: {dict(split_counts)}")

# nasa + discharge + soh_ratio 존재 조건별 단계적 개수
nasa_rows = [r for r in metadata_rows if r["dataset_id"] == "nasa"]
nasa_discharge = [r for r in nasa_rows if r["sequence_phase"] == "discharge"]
nasa_dis_soh = [r for r in nasa_discharge if r["soh_ratio"] is not None]
nasa_dis_soh_base = [r for r in nasa_dis_soh if r["baseline_capacity_ah"] is not None]
nasa_dis_soh_base_train = [
    r for r in nasa_dis_soh_base if split_by_source.get(r["source_id"]) == "train"
]

print("\n[nasa filter funnel]")
print(f"  dataset_id == 'nasa':                            {len(nasa_rows)}")
print(f"  + sequence_phase == 'discharge':                 {len(nasa_discharge)}")
print(f"  + soh_ratio is not None:                         {len(nasa_dis_soh)}")
print(f"  + baseline_capacity_ah is not None:              {len(nasa_dis_soh_base)}")
print(f"  + split == 'train':                              {len(nasa_dis_soh_base_train)}")

if nasa_rows:
    print("\n[sample nasa row] first nasa metadata row keys/values:")
    sample = nasa_rows[0]
    for key, value in sample.items():
        print(f"  {key} = {value!r}")

if nasa_dis_soh_base:
    sr = nasa_dis_soh_base[0]
    sid = sr["source_id"]
    print(f"\n[sample cycle sequence] source_id={sid}")
    seq_rows = pq.read_table(
        ARTIFACT_DIR / "sequences.parquet",
        columns=["source_id", "time_s", "voltage_v", "current_a"],
    ).to_pylist()
    matching = [r for r in seq_rows if r["source_id"] == sid]
    print(f"  sequence row count: {len(matching)}")
    if matching:
        print(f"  first row: {matching[0]}")
        print(f"  last row:  {matching[-1]}")

# split 수가 0이면 전체 split 분포도 같이 확인
if nasa_dis_soh_base and split_counts.get("train", 0) == 0:
    print("\n[warn] 'train' split이 없습니다. split 값들:")
    print(f"  distinct split values: {set(split_by_source.values())}")
