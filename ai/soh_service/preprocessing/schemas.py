from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Literal

DatasetId = Literal["nasa", "calce"]
SequencePhase = Literal["charge", "discharge", "impedance", "unknown"]

REQUIRED_SEQUENCE_FIELDS = ("time_s", "voltage_v", "current_a")
OPTIONAL_SEQUENCE_FIELDS = ("temperature_c",)


@dataclass(frozen=True)
class CanonicalCycleMetadata:
    dataset_id: DatasetId
    source_id: str
    cell_id: str
    cycle_index: int | None
    sequence_phase: SequencePhase
    label_source_phase: SequencePhase | None
    source_format: str | None
    start_time: datetime | None
    temperature_condition_c: float | None
    baseline_capacity_ah: float | None
    current_capacity_ah: float | None
    soh_ratio: float | None
    has_temperature: bool
    quality_flags: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class CanonicalSequencePoint:
    time_s: float
    voltage_v: float
    current_a: float
    temperature_c: float | None
    temperature_mask: int
    sample_index: int | None = None
    step_index: int | None = None
    raw_phase_hint: str | None = None


@dataclass(frozen=True)
class CanonicalSequenceSample:
    metadata: CanonicalCycleMetadata
    sequence: tuple[CanonicalSequencePoint, ...]
