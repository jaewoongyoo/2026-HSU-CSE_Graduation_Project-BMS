"""Dataset-level preprocessing orchestration for canonical NASA/CALCE artifacts."""

from __future__ import annotations

import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

import pyarrow as pa
import pyarrow.parquet as pq

from soh_service.datasets.calce import CalceDatasetLoader
from soh_service.datasets.nasa import NasaCleanedDatasetLoader
from soh_service.preprocessing.adapters.calce import (
    build_calce_xlsx_canonical_samples,
)
from soh_service.preprocessing.adapters.nasa import build_nasa_canonical_samples
from soh_service.preprocessing.schemas import (
    OPTIONAL_SEQUENCE_FIELDS,
    REQUIRED_SEQUENCE_FIELDS,
    CanonicalSequenceSample,
)
from soh_service.preprocessing.splits import build_split_manifest

DEFAULT_EXPORT_DIRNAME = "canonical_nasa_calce_v1"
PARQUET_COMPRESSION = "snappy"
SEQUENCE_EXPORT_BATCH_SIZE = 50_000
METADATA_EXPORT_COLUMNS = (
    "dataset_id",
    "source_id",
    "cell_id",
    "cycle_index",
    "sequence_phase",
    "label_source_phase",
    "source_format",
    "start_time",
    "temperature_condition_c",
    "baseline_capacity_ah",
    "current_capacity_ah",
    "soh_ratio",
    "has_temperature",
    "quality_flags",
    "sequence_length",
)
SEQUENCE_EXPORT_COLUMNS = (
    "source_id",
    "time_s",
    "voltage_v",
    "current_a",
    "temperature_c",
    "temperature_mask",
    "sample_index",
    "step_index",
    "raw_phase_hint",
)
SPLIT_EXPORT_COLUMNS = ("dataset_id", "cell_id", "source_id", "split")
METADATA_ARROW_SCHEMA = pa.schema(
    [
        pa.field("dataset_id", pa.string()),
        pa.field("source_id", pa.string()),
        pa.field("cell_id", pa.string()),
        pa.field("cycle_index", pa.int64()),
        pa.field("sequence_phase", pa.string()),
        pa.field("label_source_phase", pa.string()),
        pa.field("source_format", pa.string()),
        pa.field("start_time", pa.string()),
        pa.field("temperature_condition_c", pa.float64()),
        pa.field("baseline_capacity_ah", pa.float64()),
        pa.field("current_capacity_ah", pa.float64()),
        pa.field("soh_ratio", pa.float64()),
        pa.field("has_temperature", pa.bool_()),
        pa.field("quality_flags", pa.string()),
        pa.field("sequence_length", pa.int64()),
    ]
)
SEQUENCE_ARROW_SCHEMA = pa.schema(
    [
        pa.field("source_id", pa.string()),
        pa.field("time_s", pa.float64()),
        pa.field("voltage_v", pa.float64()),
        pa.field("current_a", pa.float64()),
        pa.field("temperature_c", pa.float64()),
        pa.field("temperature_mask", pa.int8()),
        pa.field("sample_index", pa.int64()),
        pa.field("step_index", pa.int64()),
        pa.field("raw_phase_hint", pa.string()),
    ]
)
SPLIT_ARROW_SCHEMA = pa.schema(
    [
        pa.field("dataset_id", pa.string()),
        pa.field("cell_id", pa.string()),
        pa.field("source_id", pa.string()),
        pa.field("split", pa.string()),
    ]
)


def build_canonical_training_samples(
    nasa_loader: NasaCleanedDatasetLoader | None = None,
    calce_loader: CalceDatasetLoader | None = None,
    warmup_cycle_count: int = 5,
) -> list[CanonicalSequenceSample]:
    """Build canonical NASA/CALCE training samples using the fixed v1 rules."""

    samples: list[CanonicalSequenceSample] = []
    if nasa_loader is not None:
        samples.extend(
            build_nasa_canonical_samples(
                nasa_loader,
                warmup_cycle_count=warmup_cycle_count,
            )
        )
    if calce_loader is not None:
        samples.extend(
            build_calce_xlsx_canonical_samples(
                calce_loader,
                warmup_cycle_count=warmup_cycle_count,
            )
        )
    return sorted(
        samples,
        key=lambda sample: (
            sample.metadata.dataset_id,
            sample.metadata.cell_id,
            sample.metadata.cycle_index if sample.metadata.cycle_index is not None else -1,
            sample.metadata.source_id,
        ),
    )


def build_metadata_table(
    samples: list[CanonicalSequenceSample],
) -> list[dict[str, object]]:
    """Return metadata rows suitable for manifest/export steps."""

    return list(_iter_metadata_rows(samples))


def build_sequence_rows(
    samples: list[CanonicalSequenceSample],
) -> list[dict[str, object]]:
    """Flatten canonical sequences into row-wise export records."""

    return list(_iter_sequence_rows(samples))


def build_preprocessing_artifacts(
    nasa_loader: NasaCleanedDatasetLoader | None = None,
    calce_loader: CalceDatasetLoader | None = None,
    warmup_cycle_count: int = 5,
) -> dict[str, list[dict[str, object]]]:
    """Build metadata, sequence, and split artifacts from canonical samples."""

    samples = build_canonical_training_samples(
        nasa_loader=nasa_loader,
        calce_loader=calce_loader,
        warmup_cycle_count=warmup_cycle_count,
    )
    return {
        "metadata": build_metadata_table(samples),
        "sequences": build_sequence_rows(samples),
        "splits": build_split_manifest(samples),
    }


def write_preprocessing_artifacts(
    samples: list[CanonicalSequenceSample],
    output_dir: str | Path | None = None,
    warmup_cycle_count: int = 5,
) -> dict[str, Path]:
    """Write canonical preprocessing artifacts to disk for reuse."""

    artifact_dir = _resolve_output_dir(output_dir)
    artifact_dir.mkdir(parents=True, exist_ok=True)

    metadata_path = artifact_dir / "metadata.parquet"
    sequences_path = artifact_dir / "sequences.parquet"
    splits_path = artifact_dir / "splits.parquet"
    manifest_path = artifact_dir / "manifest.json"

    metadata_rows = build_metadata_table(samples)
    split_rows = build_split_manifest(samples)

    _write_small_table_parquet(
        metadata_rows,
        columns=METADATA_EXPORT_COLUMNS,
        schema=METADATA_ARROW_SCHEMA,
        output_path=metadata_path,
    )
    _write_small_table_parquet(
        split_rows,
        columns=SPLIT_EXPORT_COLUMNS,
        schema=SPLIT_ARROW_SCHEMA,
        output_path=splits_path,
    )
    _write_sequence_rows_parquet(samples, sequences_path)

    artifact_paths = {
        "output_dir": artifact_dir,
        "metadata": metadata_path,
        "sequences": sequences_path,
        "splits": splits_path,
        "manifest": manifest_path,
    }
    manifest = _build_export_manifest(
        samples=samples,
        artifact_paths=artifact_paths,
        warmup_cycle_count=warmup_cycle_count,
        split_rows=split_rows,
    )
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return artifact_paths


def export_preprocessing_artifacts(
    output_dir: str | Path | None = None,
    nasa_loader: NasaCleanedDatasetLoader | None = None,
    calce_loader: CalceDatasetLoader | None = None,
    warmup_cycle_count: int = 5,
) -> dict[str, Path]:
    """Build canonical samples and persist export artifacts to disk."""

    samples = build_canonical_training_samples(
        nasa_loader=nasa_loader,
        calce_loader=calce_loader,
        warmup_cycle_count=warmup_cycle_count,
    )
    return write_preprocessing_artifacts(
        samples=samples,
        output_dir=output_dir,
        warmup_cycle_count=warmup_cycle_count,
    )


def _iter_metadata_rows(
    samples: list[CanonicalSequenceSample],
) -> Iterator[dict[str, object]]:
    for sample in samples:
        metadata = sample.metadata
        yield {
            "dataset_id": metadata.dataset_id,
            "source_id": metadata.source_id,
            "cell_id": metadata.cell_id,
            "cycle_index": metadata.cycle_index,
            "sequence_phase": metadata.sequence_phase,
            "label_source_phase": metadata.label_source_phase,
            "source_format": metadata.source_format,
            "start_time": _serialize_datetime(metadata.start_time),
            "temperature_condition_c": metadata.temperature_condition_c,
            "baseline_capacity_ah": metadata.baseline_capacity_ah,
            "current_capacity_ah": metadata.current_capacity_ah,
            "soh_ratio": metadata.soh_ratio,
            "has_temperature": metadata.has_temperature,
            "quality_flags": _serialize_quality_flags(metadata.quality_flags),
            "sequence_length": len(sample.sequence),
        }


def _iter_sequence_rows(
    samples: list[CanonicalSequenceSample],
) -> Iterator[dict[str, object]]:
    for sample in samples:
        for point in sample.sequence:
            yield {
                "source_id": sample.metadata.source_id,
                "time_s": point.time_s,
                "voltage_v": point.voltage_v,
                "current_a": point.current_a,
                "temperature_c": point.temperature_c,
                "temperature_mask": point.temperature_mask,
                "sample_index": point.sample_index,
                "step_index": point.step_index,
                "raw_phase_hint": point.raw_phase_hint,
            }


def _write_small_table_parquet(
    rows: list[dict[str, object]],
    columns: tuple[str, ...],
    schema: pa.Schema,
    output_path: Path,
) -> None:
    normalized_rows = [
        {column: row.get(column) for column in columns}
        for row in rows
    ]
    table = pa.Table.from_pylist(normalized_rows, schema=schema)
    pq.write_table(
        table,
        output_path,
        compression=PARQUET_COMPRESSION,
    )


def _write_sequence_rows_parquet(
    samples: list[CanonicalSequenceSample],
    output_path: Path,
) -> None:
    writer: pq.ParquetWriter | None = None
    batch_rows: list[dict[str, object]] = []

    try:
        for row in _iter_sequence_rows(samples):
            batch_rows.append(row)
            if len(batch_rows) >= SEQUENCE_EXPORT_BATCH_SIZE:
                writer = _flush_sequence_batch(
                    writer=writer,
                    rows=batch_rows,
                    output_path=output_path,
                )
                batch_rows = []

        if batch_rows:
            writer = _flush_sequence_batch(
                writer=writer,
                rows=batch_rows,
                output_path=output_path,
            )
        elif writer is None:
            pq.write_table(
                pa.Table.from_pylist([], schema=SEQUENCE_ARROW_SCHEMA),
                output_path,
                compression=PARQUET_COMPRESSION,
            )
    finally:
        if writer is not None:
            writer.close()


def _flush_sequence_batch(
    writer: pq.ParquetWriter | None,
    rows: list[dict[str, object]],
    output_path: Path,
) -> pq.ParquetWriter:
    table = pa.Table.from_pylist(
        [
            {column: row.get(column) for column in SEQUENCE_EXPORT_COLUMNS}
            for row in rows
        ],
        schema=SEQUENCE_ARROW_SCHEMA,
    )
    if writer is None:
        writer = pq.ParquetWriter(
            output_path,
            schema=SEQUENCE_ARROW_SCHEMA,
            compression=PARQUET_COMPRESSION,
        )
    writer.write_table(table)
    return writer


def _build_export_manifest(
    samples: list[CanonicalSequenceSample],
    artifact_paths: dict[str, Path],
    warmup_cycle_count: int,
    split_rows: list[dict[str, str]],
) -> dict[str, object]:
    dataset_sample_counts = Counter(sample.metadata.dataset_id for sample in samples)
    dataset_cell_counts = Counter(
        (sample.metadata.dataset_id, sample.metadata.cell_id) for sample in samples
    )
    split_sample_counts = Counter(row["split"] for row in split_rows)

    unique_cells_by_dataset: dict[str, int] = {}
    for dataset_id, _cell_id in dataset_cell_counts:
        unique_cells_by_dataset[dataset_id] = (
            unique_cells_by_dataset.get(dataset_id, 0) + 1
        )

    return {
        "artifact_version": DEFAULT_EXPORT_DIRNAME,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "warmup_cycle_count": warmup_cycle_count,
        "sample_count": len(samples),
        "sequence_row_count": sum(len(sample.sequence) for sample in samples),
        "dataset_sample_counts": dict(sorted(dataset_sample_counts.items())),
        "dataset_cell_counts": dict(sorted(unique_cells_by_dataset.items())),
        "split_sample_counts": dict(sorted(split_sample_counts.items())),
        "sequence_schema": {
            "required": list(REQUIRED_SEQUENCE_FIELDS),
            "optional": list(OPTIONAL_SEQUENCE_FIELDS),
            "mask_fields": ["temperature_mask"],
        },
        "storage": {
            "format": "parquet",
            "compression": PARQUET_COMPRESSION,
        },
        "files": {
            name: path.name
            for name, path in artifact_paths.items()
            if name != "output_dir"
        },
    }


def _resolve_output_dir(output_dir: str | Path | None) -> Path:
    if output_dir is not None:
        return Path(output_dir)
    return (
        Path(__file__).resolve().parents[2]
        / "data"
        / "interim"
        / DEFAULT_EXPORT_DIRNAME
    )


def _serialize_datetime(value: datetime | None) -> str | None:
    if value is None:
        return None
    return value.isoformat()


def _serialize_quality_flags(flags: tuple[str, ...]) -> str:
    return "|".join(flags)
