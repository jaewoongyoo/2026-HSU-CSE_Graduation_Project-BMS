"""NASA adapter policy and projection helpers for canonical preprocessing."""

from __future__ import annotations

from collections import defaultdict
from datetime import datetime

from soh_service.datasets.nasa import (
    NasaCleanedDatasetLoader,
    NasaCycleMetadata,
    NasaCycleRecord,
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

NASA_INPUT_PHASE = "charge"
NASA_LABEL_PHASE = "discharge"
NASA_AUXILIARY_PHASES = ("impedance",)


def build_nasa_canonical_samples(
    loader: NasaCleanedDatasetLoader,
    battery_ids: set[str] | None = None,
    warmup_cycle_count: int = 5,
) -> list[CanonicalSequenceSample]:
    """Project NASA charge cycles into canonical LSTM samples."""

    metadata_rows = loader.list_metadata(
        battery_ids=battery_ids,
        include_missing_files=False,
    )
    charges_by_battery: dict[str, list[NasaCycleMetadata]] = defaultdict(list)
    discharges_by_battery: dict[str, list[NasaCycleMetadata]] = defaultdict(list)
    for metadata in metadata_rows:
        if metadata.cycle_type == NASA_INPUT_PHASE:
            charges_by_battery[metadata.battery_id].append(metadata)
        elif metadata.cycle_type == NASA_LABEL_PHASE and _is_valid_capacity(
            metadata.capacity_ah
        ):
            discharges_by_battery[metadata.battery_id].append(metadata)

    samples: list[CanonicalSequenceSample] = []
    for battery_id in sorted(charges_by_battery):
        charge_rows = sorted(charges_by_battery[battery_id], key=_sort_nasa_metadata)
        discharge_rows = sorted(
            discharges_by_battery.get(battery_id, []),
            key=_sort_nasa_metadata,
        )
        paired_rows = _pair_charge_and_discharge(charge_rows, discharge_rows)
        labeled_pairs = [
            (charge_meta, discharge_meta)
            for charge_meta, discharge_meta in paired_rows
            if discharge_meta is not None and _is_valid_capacity(discharge_meta.capacity_ah)
        ]
        if not labeled_pairs:
            continue

        baseline_capacity_ah = choose_baseline_capacity(
            [discharge_meta.capacity_ah for _, discharge_meta in labeled_pairs],
            warmup_cycle_count=warmup_cycle_count,
        )
        for charge_meta, discharge_meta in labeled_pairs:
            charge_record = loader.load_cycle(charge_meta.filename)
            samples.append(
                _project_nasa_charge_record(
                    charge_record=charge_record,
                    discharge_metadata=discharge_meta,
                    baseline_capacity_ah=baseline_capacity_ah,
                )
            )

    return sorted(samples, key=_sort_canonical_sample)


def _pair_charge_and_discharge(
    charge_rows: list[NasaCycleMetadata],
    discharge_rows: list[NasaCycleMetadata],
) -> list[tuple[NasaCycleMetadata, NasaCycleMetadata | None]]:
    pairs: list[tuple[NasaCycleMetadata, NasaCycleMetadata | None]] = []
    discharge_index = 0
    for charge_meta in charge_rows:
        while discharge_index < len(discharge_rows) and _sort_nasa_metadata(
            discharge_rows[discharge_index]
        ) < _sort_nasa_metadata(charge_meta):
            discharge_index += 1

        matched_discharge = (
            discharge_rows[discharge_index]
            if discharge_index < len(discharge_rows)
            else None
        )
        if matched_discharge is not None:
            discharge_index += 1
        pairs.append((charge_meta, matched_discharge))
    return pairs


def _project_nasa_charge_record(
    charge_record: NasaCycleRecord,
    discharge_metadata: NasaCycleMetadata,
    baseline_capacity_ah: float | None,
) -> CanonicalSequenceSample:
    current_capacity_ah = discharge_metadata.capacity_ah
    soh_ratio = compute_capacity_soh(current_capacity_ah, baseline_capacity_ah)

    time_values = [float(sample["time_s"]) for sample in charge_record.samples]
    voltage_values = [
        float(sample["voltage_measured_v"]) for sample in charge_record.samples
    ]
    current_values = [
        float(sample["current_measured_a"]) for sample in charge_record.samples
    ]
    temperature_values = [
        _coerce_optional_float(sample.get("temperature_measured_c"))
        for sample in charge_record.samples
    ]
    relative_time_s = to_relative_time_axis(time_values)
    temperature_mask = build_observed_mask(temperature_values)

    quality_flags: list[str] = []
    if baseline_capacity_ah is None:
        quality_flags.append("missing_baseline_capacity")

    sequence = tuple(
        CanonicalSequencePoint(
            time_s=relative_time_s[idx],
            voltage_v=voltage_values[idx],
            current_a=current_values[idx],
            temperature_c=temperature_values[idx],
            temperature_mask=temperature_mask[idx],
            sample_index=idx,
            raw_phase_hint=NASA_INPUT_PHASE,
        )
        for idx in range(len(relative_time_s))
    )
    metadata = CanonicalCycleMetadata(
        dataset_id="nasa",
        source_id=f"nasa:{charge_record.metadata.filename}",
        cell_id=charge_record.metadata.battery_id,
        cycle_index=charge_record.metadata.test_id or charge_record.metadata.uid,
        sequence_phase=NASA_INPUT_PHASE,
        label_source_phase=NASA_LABEL_PHASE,
        source_format="csv",
        start_time=charge_record.metadata.start_time,
        temperature_condition_c=charge_record.metadata.ambient_temperature_c,
        baseline_capacity_ah=baseline_capacity_ah,
        current_capacity_ah=current_capacity_ah,
        soh_ratio=soh_ratio,
        has_temperature=any(mask == 1 for mask in temperature_mask),
        quality_flags=tuple(quality_flags),
    )
    return CanonicalSequenceSample(metadata=metadata, sequence=sequence)


def _sort_nasa_metadata(metadata: NasaCycleMetadata) -> tuple[datetime, int, int, str]:
    return (
        metadata.start_time or datetime.min,
        metadata.test_id if metadata.test_id is not None else -1,
        metadata.uid if metadata.uid is not None else -1,
        metadata.filename,
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


def _coerce_optional_float(value: object) -> float | None:
    if value is None:
        return None
    return float(value)
