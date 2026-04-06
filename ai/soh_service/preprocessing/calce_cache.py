"""Reusable parquet cache for CALCE xlsx cycle candidates."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as pq

from soh_service.datasets.calce import (
    CalceArchiveMemberFingerprint,
    CalceFileMetadata,
)

DEFAULT_CALCE_XLSX_CANDIDATE_CACHE_DIR = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "interim"
    / "calce_xlsx_candidate_cache_v1"
)
CALCE_XLSX_CANDIDATE_CACHE_VERSION = "v1"
CALCE_XLSX_CANDIDATE_CACHE_MANIFEST = "manifest.json"
CALCE_XLSX_CANDIDATE_CACHE_COMPRESSION = "snappy"
CALCE_CANDIDATE_CACHE_SCHEMA = pa.schema(
    [
        pa.field("candidate_index", pa.int64()),
        pa.field("cycle_index", pa.int64()),
        pa.field("start_time", pa.string()),
        pa.field("current_capacity_ah", pa.float64()),
        pa.field("quality_flags", pa.string()),
        pa.field("sample_index", pa.int64()),
        pa.field("time_s", pa.float64()),
        pa.field("voltage_v", pa.float64()),
        pa.field("current_a", pa.float64()),
        pa.field("temperature_c", pa.float64()),
        pa.field("step_index", pa.int64()),
    ]
)


@dataclass(frozen=True)
class CalceCycleCandidate:
    record_metadata: CalceFileMetadata
    cycle_index: int
    start_time: datetime | None
    time_values_s: tuple[float, ...]
    voltage_values_v: tuple[float, ...]
    current_values_a: tuple[float, ...]
    temperature_values_c: tuple[float | None, ...]
    step_indices: tuple[int | None, ...]
    current_capacity_ah: float | None
    quality_flags: tuple[str, ...]


def load_calce_xlsx_candidate_cache(
    metadata: CalceFileMetadata,
    fingerprint: CalceArchiveMemberFingerprint,
    cache_dir: str | Path | None = None,
) -> list[CalceCycleCandidate] | None:
    cache_path = resolve_calce_xlsx_candidate_cache_path(
        metadata=metadata,
        fingerprint=fingerprint,
        cache_dir=cache_dir,
    )
    if not cache_path.exists():
        return None

    table = pq.read_table(cache_path)
    rows = table.to_pylist()
    if not rows:
        return []

    grouped_rows: dict[int, list[dict[str, object]]] = {}
    for row in rows:
        candidate_index = int(row["candidate_index"])
        grouped_rows.setdefault(candidate_index, []).append(row)

    candidates: list[CalceCycleCandidate] = []
    for candidate_index in sorted(grouped_rows):
        candidate_rows = sorted(
            grouped_rows[candidate_index],
            key=lambda row: int(row["sample_index"]),
        )
        first_row = candidate_rows[0]
        candidates.append(
            CalceCycleCandidate(
                record_metadata=metadata,
                cycle_index=int(first_row["cycle_index"]),
                start_time=_deserialize_datetime(first_row["start_time"]),
                time_values_s=tuple(float(row["time_s"]) for row in candidate_rows),
                voltage_values_v=tuple(float(row["voltage_v"]) for row in candidate_rows),
                current_values_a=tuple(float(row["current_a"]) for row in candidate_rows),
                temperature_values_c=tuple(
                    None
                    if row["temperature_c"] is None
                    else float(row["temperature_c"])
                    for row in candidate_rows
                ),
                step_indices=tuple(
                    None if row["step_index"] is None else int(row["step_index"])
                    for row in candidate_rows
                ),
                current_capacity_ah=(
                    None
                    if first_row["current_capacity_ah"] is None
                    else float(first_row["current_capacity_ah"])
                ),
                quality_flags=_deserialize_quality_flags(first_row["quality_flags"]),
            )
        )
    return candidates


def write_calce_xlsx_candidate_cache(
    metadata: CalceFileMetadata,
    fingerprint: CalceArchiveMemberFingerprint,
    candidates: list[CalceCycleCandidate],
    cache_dir: str | Path | None = None,
) -> Path:
    resolved_cache_dir = resolve_calce_xlsx_candidate_cache_dir(cache_dir)
    cache_path = resolve_calce_xlsx_candidate_cache_path(
        metadata=metadata,
        fingerprint=fingerprint,
        cache_dir=resolved_cache_dir,
    )
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    _ensure_cache_manifest(resolved_cache_dir)

    schema = CALCE_CANDIDATE_CACHE_SCHEMA.with_metadata(
        _build_cache_schema_metadata(metadata, fingerprint)
    )
    table = _build_candidate_table(candidates, schema)
    pq.write_table(
        table,
        cache_path,
        compression=CALCE_XLSX_CANDIDATE_CACHE_COMPRESSION,
    )
    return cache_path


def resolve_calce_xlsx_candidate_cache_dir(
    cache_dir: str | Path | None = None,
) -> Path:
    if cache_dir is None:
        return DEFAULT_CALCE_XLSX_CANDIDATE_CACHE_DIR
    return Path(cache_dir)


def resolve_calce_xlsx_candidate_cache_path(
    metadata: CalceFileMetadata,
    fingerprint: CalceArchiveMemberFingerprint,
    cache_dir: str | Path | None = None,
) -> Path:
    resolved_cache_dir = resolve_calce_xlsx_candidate_cache_dir(cache_dir)
    cache_key = _build_cache_key(metadata, fingerprint)
    cache_name = hashlib.sha256(cache_key.encode("utf-8")).hexdigest()
    return resolved_cache_dir / "cache" / f"{cache_name}.parquet"


def _build_candidate_table(
    candidates: list[CalceCycleCandidate],
    schema: pa.Schema,
) -> pa.Table:
    rows: list[dict[str, object]] = []
    for candidate_index, candidate in enumerate(candidates):
        quality_flags = _serialize_quality_flags(candidate.quality_flags)
        start_time = _serialize_datetime(candidate.start_time)
        for sample_index in range(len(candidate.time_values_s)):
            rows.append(
                {
                    "candidate_index": candidate_index,
                    "cycle_index": candidate.cycle_index,
                    "start_time": start_time,
                    "current_capacity_ah": candidate.current_capacity_ah,
                    "quality_flags": quality_flags,
                    "sample_index": sample_index,
                    "time_s": candidate.time_values_s[sample_index],
                    "voltage_v": candidate.voltage_values_v[sample_index],
                    "current_a": candidate.current_values_a[sample_index],
                    "temperature_c": candidate.temperature_values_c[sample_index],
                    "step_index": candidate.step_indices[sample_index],
                }
            )

    if rows:
        return pa.Table.from_pylist(rows, schema=schema)
    return pa.Table.from_arrays(
        [pa.array([], type=field.type) for field in schema],
        schema=schema,
    )


def _build_cache_key(
    metadata: CalceFileMetadata,
    fingerprint: CalceArchiveMemberFingerprint,
) -> str:
    return "|".join(
        [
            "calce_xlsx_candidate_cache",
            CALCE_XLSX_CANDIDATE_CACHE_VERSION,
            metadata.archive_name,
            metadata.inner_path,
            str(fingerprint.crc),
            str(fingerprint.file_size),
            str(fingerprint.compress_size),
        ]
    )


def _build_cache_schema_metadata(
    metadata: CalceFileMetadata,
    fingerprint: CalceArchiveMemberFingerprint,
) -> dict[bytes, bytes]:
    return {
        b"cache_type": b"calce_xlsx_candidate_cache",
        b"cache_version": CALCE_XLSX_CANDIDATE_CACHE_VERSION.encode("utf-8"),
        b"archive_name": metadata.archive_name.encode("utf-8"),
        b"inner_path": metadata.inner_path.encode("utf-8"),
        b"cell_id": metadata.cell_id.encode("utf-8"),
        b"crc": str(fingerprint.crc).encode("utf-8"),
        b"file_size": str(fingerprint.file_size).encode("utf-8"),
        b"compress_size": str(fingerprint.compress_size).encode("utf-8"),
    }


def _ensure_cache_manifest(cache_dir: Path) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = cache_dir / CALCE_XLSX_CANDIDATE_CACHE_MANIFEST
    if manifest_path.exists():
        return

    manifest = {
        "cache_type": "calce_xlsx_candidate_cache",
        "cache_version": CALCE_XLSX_CANDIDATE_CACHE_VERSION,
        "storage": {
            "format": "parquet",
            "compression": CALCE_XLSX_CANDIDATE_CACHE_COMPRESSION,
        },
        "files": {
            "layout": "cache/<sha256(cache_key)>.parquet",
        },
        "notes": [
            "One parquet file per CALCE primary xlsx member.",
            "The cached payload stores extracted cycle candidates, not final SOH samples.",
        ],
    }
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _serialize_datetime(value: datetime | None) -> str | None:
    if value is None:
        return None
    return value.isoformat()


def _deserialize_datetime(value: object) -> datetime | None:
    if value is None:
        return None
    text = str(value).strip()
    if text == "":
        return None
    return datetime.fromisoformat(text)


def _serialize_quality_flags(flags: tuple[str, ...]) -> str:
    return json.dumps(list(flags), ensure_ascii=False)


def _deserialize_quality_flags(value: object) -> tuple[str, ...]:
    if value is None:
        return ()
    text = str(value).strip()
    if text == "":
        return ()
    parsed = json.loads(text)
    return tuple(str(item) for item in parsed)
