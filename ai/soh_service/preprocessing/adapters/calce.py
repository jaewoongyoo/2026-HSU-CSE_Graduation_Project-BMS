"""CALCE adapter policy and projection helpers for canonical preprocessing."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime

import pandas as pd

from soh_service.datasets.calce import (
    CalceDatasetLoader,
    CalceFileMetadata,
    CalceRecord,
)
from soh_service.preprocessing.labels import (
    choose_baseline_capacity,
    compute_capacity_soh,
)
from soh_service.preprocessing.schemas import (
    CanonicalCycleMetadata,
    CanonicalSequencePoint,
    CanonicalSequenceSample,
)
from soh_service.preprocessing.sequences import (
    build_observed_mask,
    to_relative_time_axis,
)

CALCE_PRIMARY_FORMATS = ("xlsx",)
CALCE_AUXILIARY_FORMATS = ("txt", "csv")


@dataclass(frozen=True)
class _CalceCycleCandidate:
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


def build_calce_xlsx_canonical_samples(
    loader: CalceDatasetLoader,
    archive_names: set[str] | None = None,
    warmup_cycle_count: int = 5,
) -> list[CanonicalSequenceSample]:
    """Project CALCE xlsx cycling logs into canonical LSTM samples."""

    rows_by_cell: dict[str, list[_CalceCycleCandidate]] = defaultdict(list)
    for metadata in loader.list_files(
        archive_names=archive_names,
        file_formats=set(CALCE_PRIMARY_FORMATS),
    ):
        record = loader.load_record(metadata.archive_name, metadata.inner_path)
        for candidate in _extract_calce_xlsx_candidates(record):
            rows_by_cell[candidate.record_metadata.cell_id].append(candidate)

    samples: list[CanonicalSequenceSample] = []
    for cell_id in sorted(rows_by_cell):
        candidates = sorted(rows_by_cell[cell_id], key=_sort_calce_candidate)
        labeled_capacities = [
            candidate.current_capacity_ah
            for candidate in candidates
            if _is_valid_capacity(candidate.current_capacity_ah)
        ]
        if not labeled_capacities:
            continue

        baseline_capacity_ah = choose_baseline_capacity(
            labeled_capacities,
            warmup_cycle_count=warmup_cycle_count,
        )
        for candidate in candidates:
            if not _is_valid_capacity(candidate.current_capacity_ah):
                continue
            samples.append(
                _project_calce_candidate(
                    candidate,
                    baseline_capacity_ah=baseline_capacity_ah,
                )
            )

    return sorted(samples, key=_sort_canonical_sample)


def _extract_calce_xlsx_candidates(record: CalceRecord) -> list[_CalceCycleCandidate]:
    frame = pd.DataFrame.from_records(record.samples)
    if frame.empty:
        return []

    if "cycle_index" not in frame.columns:
        return []

    frame = frame.copy()
    frame["cycle_index"] = pd.to_numeric(frame["cycle_index"], errors="coerce")
    frame = frame.dropna(subset=["cycle_index"])
    if frame.empty:
        return []

    candidates: list[_CalceCycleCandidate] = []
    for cycle_value in sorted(frame["cycle_index"].unique()):
        cycle_rows = frame[frame["cycle_index"] == cycle_value].copy()
        charge_rows = _select_charge_rows(cycle_rows)
        if charge_rows.empty:
            continue

        time_column = "test_time_s" if "test_time_s" in charge_rows.columns else "step_time_s"
        time_values = pd.to_numeric(charge_rows[time_column], errors="coerce")
        voltage_values = pd.to_numeric(charge_rows.get("voltage_v"), errors="coerce")
        current_values = pd.to_numeric(charge_rows.get("current_a"), errors="coerce")
        valid_mask = (
            time_values.notna() & voltage_values.notna() & current_values.notna()
        )
        if not valid_mask.any():
            continue

        charge_rows = charge_rows.loc[valid_mask].reset_index(drop=True)
        time_values = time_values.loc[valid_mask].astype(float).tolist()
        voltage_values = voltage_values.loc[valid_mask].astype(float).tolist()
        current_values = current_values.loc[valid_mask].astype(float).tolist()
        temperature_values = _extract_temperature_values(charge_rows)
        step_indices = _extract_step_indices(charge_rows)
        current_capacity_ah = _extract_discharge_capacity_ah(cycle_rows)
        quality_flags: list[str] = []
        if not any(value is not None for value in temperature_values):
            quality_flags.append("missing_temperature")
        if not any(value is not None for value in step_indices):
            quality_flags.append("missing_step_index")
        if current_capacity_ah is None:
            quality_flags.append("missing_label")

        candidates.append(
            _CalceCycleCandidate(
                record_metadata=record.metadata,
                cycle_index=int(cycle_value),
                start_time=_extract_start_time(charge_rows),
                time_values_s=tuple(to_relative_time_axis(time_values)),
                voltage_values_v=tuple(voltage_values),
                current_values_a=tuple(current_values),
                temperature_values_c=tuple(temperature_values),
                step_indices=tuple(step_indices),
                current_capacity_ah=current_capacity_ah,
                quality_flags=tuple(quality_flags),
            )
        )
    return candidates


def _project_calce_candidate(
    candidate: _CalceCycleCandidate,
    baseline_capacity_ah: float | None,
) -> CanonicalSequenceSample:
    temperature_mask = build_observed_mask(candidate.temperature_values_c)
    soh_ratio = compute_capacity_soh(
        candidate.current_capacity_ah,
        baseline_capacity_ah,
    )
    quality_flags = list(candidate.quality_flags)
    if baseline_capacity_ah is None:
        quality_flags.append("missing_baseline_capacity")

    sequence = tuple(
        CanonicalSequencePoint(
            time_s=candidate.time_values_s[idx],
            voltage_v=candidate.voltage_values_v[idx],
            current_a=candidate.current_values_a[idx],
            temperature_c=candidate.temperature_values_c[idx],
            temperature_mask=temperature_mask[idx],
            sample_index=idx,
            step_index=candidate.step_indices[idx],
            raw_phase_hint="charge",
        )
        for idx in range(len(candidate.time_values_s))
    )
    metadata = CanonicalCycleMetadata(
        dataset_id="calce",
        source_id=(
            f"calce:{candidate.record_metadata.archive_name}:"
            f"{candidate.record_metadata.inner_path}#cycle={candidate.cycle_index}"
        ),
        cell_id=candidate.record_metadata.cell_id,
        cycle_index=candidate.cycle_index,
        sequence_phase="charge",
        label_source_phase="discharge",
        source_format=candidate.record_metadata.file_format,
        start_time=candidate.start_time,
        temperature_condition_c=candidate.record_metadata.inferred_temperature_c,
        baseline_capacity_ah=baseline_capacity_ah,
        current_capacity_ah=candidate.current_capacity_ah,
        soh_ratio=soh_ratio,
        has_temperature=any(mask == 1 for mask in temperature_mask),
        quality_flags=tuple(quality_flags),
    )
    return CanonicalSequenceSample(metadata=metadata, sequence=sequence)


def _select_charge_rows(frame: pd.DataFrame) -> pd.DataFrame:
    current_values = pd.to_numeric(frame.get("current_a"), errors="coerce").fillna(0.0)
    charge_capacity = pd.to_numeric(
        frame.get("charge_capacity_ah"),
        errors="coerce",
    ).fillna(0.0)
    charge_mask = (current_values > 0.0) | (charge_capacity > 0.0)
    return frame.loc[charge_mask].copy()


def _extract_discharge_capacity_ah(frame: pd.DataFrame) -> float | None:
    if "discharge_capacity_ah" not in frame.columns:
        return None
    values = pd.to_numeric(frame["discharge_capacity_ah"], errors="coerce").dropna()
    positive_values = values[values > 0.0]
    if positive_values.empty:
        return None
    return float(positive_values.max())


def _extract_temperature_values(frame: pd.DataFrame) -> list[float | None]:
    if "temperature_c" not in frame.columns:
        return [None] * len(frame)
    values = pd.to_numeric(frame["temperature_c"], errors="coerce")
    return [
        None if pd.isna(value) else float(value)
        for value in values
    ]


def _extract_step_indices(frame: pd.DataFrame) -> list[int | None]:
    if "step_index" not in frame.columns:
        return [None] * len(frame)
    values = pd.to_numeric(frame["step_index"], errors="coerce")
    return [
        None if pd.isna(value) else int(value)
        for value in values
    ]


def _extract_start_time(frame: pd.DataFrame) -> datetime | None:
    if "date_time" not in frame.columns:
        return None
    for value in frame["date_time"]:
        if isinstance(value, datetime):
            return value
    return None


def _sort_calce_candidate(
    candidate: _CalceCycleCandidate,
) -> tuple[str, datetime, int, str, str]:
    return (
        candidate.record_metadata.cell_id,
        candidate.start_time or datetime.min,
        candidate.cycle_index,
        candidate.record_metadata.archive_name,
        candidate.record_metadata.inner_path,
    )


def _sort_canonical_sample(
    sample: CanonicalSequenceSample,
) -> tuple[str, datetime, int, str]:
    return (
        sample.metadata.cell_id,
        sample.metadata.start_time or datetime.min,
        sample.metadata.cycle_index if sample.metadata.cycle_index is not None else -1,
        sample.metadata.source_id,
    )


def _is_valid_capacity(value: float | None) -> bool:
    return value is not None and float(value) > 0.0
