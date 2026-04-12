from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from soh_service.preprocessing.schemas import CanonicalSequenceSample

ObservationVariantId = Literal["v1", "v2", "v3"]

CORE_SEQUENCE_INPUT_FIELDS = (
    "time_s",
    "voltage_v",
    "current_a",
    "temperature_c",
    "temperature_mask",
)
OBSERVATION_DERIVED_FIELDS = (
    "power_w",
    "cumulative_energy_wh",
    "effective_output_power_w",
    "estimated_output_current_5v_a",
)


@dataclass(frozen=True)
class ObservationTransformConfig:
    efficiency_value: float = 0.85
    regulated_output_voltage_v: float = 5.0

    def __post_init__(self) -> None:
        if not 0.0 < self.efficiency_value <= 1.0:
            raise ValueError("efficiency_value must satisfy 0 < efficiency <= 1")
        if self.regulated_output_voltage_v <= 0.0:
            raise ValueError("regulated_output_voltage_v must be positive")


@dataclass(frozen=True)
class ObservationVariantSpec:
    version_id: ObservationVariantId
    derived_sequence_fields: tuple[str, ...]
    requires_full_sequence_context: bool = False
    notes: tuple[str, ...] = ()


OBSERVATION_VARIANTS: dict[ObservationVariantId, ObservationVariantSpec] = {
    "v1": ObservationVariantSpec(
        version_id="v1",
        derived_sequence_fields=("power_w",),
        notes=("Lowest-assumption observation-aware baseline.",),
    ),
    "v2": ObservationVariantSpec(
        version_id="v2",
        derived_sequence_fields=("power_w", "cumulative_energy_wh"),
        notes=("Adds causal cumulative energy progression.",),
    ),
    "v3": ObservationVariantSpec(
        version_id="v3",
        derived_sequence_fields=(
            "power_w",
            "cumulative_energy_wh",
            "effective_output_power_w",
            "estimated_output_current_5v_a",
        ),
        notes=(
            "Adds regulated-output proxy channels based on explicit efficiency and voltage assumptions.",
        ),
    ),
}


def get_observation_variant_spec(version_id: ObservationVariantId) -> ObservationVariantSpec:
    return OBSERVATION_VARIANTS[version_id]


def get_lstm_sequence_fields(version_id: ObservationVariantId) -> tuple[str, ...]:
    spec = get_observation_variant_spec(version_id)
    return CORE_SEQUENCE_INPUT_FIELDS + spec.derived_sequence_fields


def build_observation_transform_metadata(
    version_id: ObservationVariantId,
    config: ObservationTransformConfig | None = None,
) -> dict[str, object]:
    config = config or ObservationTransformConfig()
    spec = get_observation_variant_spec(version_id)
    return {
        "observation_version": spec.version_id,
        "derived_sequence_fields": list(spec.derived_sequence_fields),
        "requires_full_sequence_context": spec.requires_full_sequence_context,
        "efficiency_value": config.efficiency_value,
        "regulated_output_voltage_v": config.regulated_output_voltage_v,
        "notes": list(spec.notes),
    }


def compute_power_series(
    voltage_values_v: list[float],
    current_values_a: list[float],
) -> list[float]:
    _ensure_same_length(voltage_values_v, current_values_a, "voltage/current")
    return [voltage_v * current_a for voltage_v, current_a in zip(voltage_values_v, current_values_a)]


def compute_cumulative_energy_wh(
    time_values_s: list[float],
    power_values_w: list[float],
) -> list[float]:
    _ensure_same_length(time_values_s, power_values_w, "time/power")
    if not time_values_s:
        return []

    cumulative_values_wh = [0.0]
    for index in range(1, len(time_values_s)):
        delta_time_s = time_values_s[index] - time_values_s[index - 1]
        if delta_time_s < 0.0:
            raise ValueError("time_values_s must be non-decreasing")
        incremental_wh = (
            (power_values_w[index - 1] + power_values_w[index]) * 0.5 * delta_time_s
        ) / 3600.0
        cumulative_values_wh.append(cumulative_values_wh[-1] + incremental_wh)
    return cumulative_values_wh


def compute_effective_output_power_series(
    power_values_w: list[float],
    efficiency_value: float = 0.85,
) -> list[float]:
    return [power_w * efficiency_value for power_w in power_values_w]


def compute_estimated_output_current_series(
    effective_output_power_values_w: list[float],
    regulated_output_voltage_v: float = 5.0,
) -> list[float]:
    if regulated_output_voltage_v <= 0.0:
        raise ValueError("regulated_output_voltage_v must be positive")
    return [
        power_w / regulated_output_voltage_v for power_w in effective_output_power_values_w
    ]



def build_observation_feature_rows(
    sample: CanonicalSequenceSample,
    config: ObservationTransformConfig | None = None,
) -> list[dict[str, object]]:
    config = config or ObservationTransformConfig()

    time_values_s = [point.time_s for point in sample.sequence]
    voltage_values_v = [point.voltage_v for point in sample.sequence]
    current_values_a = [point.current_a for point in sample.sequence]

    power_values_w = compute_power_series(voltage_values_v, current_values_a)
    cumulative_energy_values_wh = compute_cumulative_energy_wh(
        time_values_s,
        power_values_w,
    )
    effective_output_power_values_w = compute_effective_output_power_series(
        power_values_w,
        efficiency_value=config.efficiency_value,
    )
    estimated_output_current_values_a = compute_estimated_output_current_series(
        effective_output_power_values_w,
        regulated_output_voltage_v=config.regulated_output_voltage_v,
    )
    rows: list[dict[str, object]] = []
    for index, point in enumerate(sample.sequence):
        rows.append(
            {
                "time_s": point.time_s,
                "voltage_v": point.voltage_v,
                "current_a": point.current_a,
                "temperature_c": point.temperature_c,
                "temperature_mask": point.temperature_mask,
                "sample_index": point.sample_index,
                "step_index": point.step_index,
                "raw_phase_hint": point.raw_phase_hint,
                "power_w": power_values_w[index],
                "cumulative_energy_wh": cumulative_energy_values_wh[index],
                "effective_output_power_w": effective_output_power_values_w[index],
                "estimated_output_current_5v_a": estimated_output_current_values_a[index],
            }
        )
    return rows


def build_lstm_variant_rows(
    sample: CanonicalSequenceSample,
    version_id: ObservationVariantId,
    config: ObservationTransformConfig | None = None,
) -> list[dict[str, object]]:
    selected_fields = get_lstm_sequence_fields(version_id)
    rows = build_observation_feature_rows(sample, config=config)
    return [
        {field: row[field] for field in selected_fields}
        for row in rows
    ]


def _ensure_same_length(
    left_values: list[float],
    right_values: list[float],
    label: str,
) -> None:
    if len(left_values) != len(right_values):
        raise ValueError(f"{label} series must have the same length")
