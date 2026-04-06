from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from soh_service.preprocessing.observation import (
    ObservationTransformConfig,
    ObservationVariantId,
    build_observation_feature_rows,
    get_lstm_sequence_fields,
)
from soh_service.preprocessing.schemas import (
    CanonicalCycleMetadata,
    CanonicalSequencePoint,
    CanonicalSequenceSample,
)


@dataclass(frozen=True)
class WindowPolicyConfig:
    window_duration_s: float = 600.0
    resample_interval_s: float = 20.0
    late_phase_threshold: float = 0.8
    allow_partial_tail_window: bool = False

    def __post_init__(self) -> None:
        if self.window_duration_s <= 0.0:
            raise ValueError("window_duration_s must be positive")
        if self.resample_interval_s <= 0.0:
            raise ValueError("resample_interval_s must be positive")
        steps = self.window_duration_s / self.resample_interval_s
        if not math.isclose(steps, round(steps), rel_tol=0.0, abs_tol=1e-9):
            raise ValueError(
                "window_duration_s must be divisible by resample_interval_s for fixed windows"
            )
        if not 0.0 < self.late_phase_threshold <= 1.0:
            raise ValueError("late_phase_threshold must satisfy 0 < threshold <= 1")

    @property
    def steps_per_window(self) -> int:
        return int(round(self.window_duration_s / self.resample_interval_s))


@dataclass(frozen=True)
class WindowMetadata:
    parent_sequence_id: str
    dataset_id: str
    cell_id: str
    cycle_index: int | None
    observation_version: str
    window_index: int
    window_start_s: float
    window_end_s: float
    window_duration_s: float
    resample_interval_s: float
    num_steps_after_resample: int
    parent_duration_s: float
    window_start_progress_ratio: float
    window_mid_progress_ratio: float
    window_end_progress_ratio: float
    late_charge_flag: int
    crosses_late_phase: int
    valid_step_ratio: float
    interpolated_step_ratio: float
    mean_abs_current_a: float
    mean_power_w: float
    proxy_quality_score: float


@dataclass(frozen=True)
class WindowedSequenceSample:
    metadata: WindowMetadata
    sequence_rows: tuple[dict[str, object], ...]
    target_soh_ratio: float | None


@dataclass(frozen=True)
class WindowedSequenceBundle:
    parent_metadata: CanonicalCycleMetadata
    observation_version: str
    windows: tuple[WindowedSequenceSample, ...]


def resample_canonical_sample(
    sample: CanonicalSequenceSample,
    interval_s: float,
) -> CanonicalSequenceSample:
    if interval_s <= 0.0:
        raise ValueError("interval_s must be positive")
    if not sample.sequence:
        return sample

    original_times_s = np.array([point.time_s for point in sample.sequence], dtype=float)
    if np.any(np.diff(original_times_s) < 0.0):
        raise ValueError("sample.sequence time_s must be non-decreasing")

    resampled_times_s = np.array(
        _build_resample_grid(
            total_duration_s=float(original_times_s[-1]),
            interval_s=interval_s,
        ),
        dtype=float,
    )

    voltage_values_v = np.array([point.voltage_v for point in sample.sequence], dtype=float)
    current_values_a = np.array([point.current_a for point in sample.sequence], dtype=float)

    resampled_voltage_v = np.interp(resampled_times_s, original_times_s, voltage_values_v)
    resampled_current_a = np.interp(resampled_times_s, original_times_s, current_values_a)

    temperature_values_c, temperature_mask = _resample_temperature_series(
        sample,
        original_times_s,
        resampled_times_s,
    )

    raw_phase_hint = sample.sequence[0].raw_phase_hint
    resampled_sequence = tuple(
        CanonicalSequencePoint(
            time_s=float(time_s),
            voltage_v=float(voltage_v),
            current_a=float(current_a),
            temperature_c=temperature_c,
            temperature_mask=temperature_mask_value,
            sample_index=index,
            step_index=None,
            raw_phase_hint=raw_phase_hint,
        )
        for index, (
            time_s,
            voltage_v,
            current_a,
            temperature_c,
            temperature_mask_value,
        ) in enumerate(
            zip(
                resampled_times_s,
                resampled_voltage_v,
                resampled_current_a,
                temperature_values_c,
                temperature_mask,
            )
        )
    )
    return CanonicalSequenceSample(metadata=sample.metadata, sequence=resampled_sequence)


def build_windowed_sequence_bundle(
    sample: CanonicalSequenceSample,
    version_id: ObservationVariantId = "v1",
    policy: WindowPolicyConfig | None = None,
    transform_config: ObservationTransformConfig | None = None,
) -> WindowedSequenceBundle | None:
    policy = policy or WindowPolicyConfig()
    transform_config = transform_config or ObservationTransformConfig()

    if not sample.sequence:
        return None

    parent_duration_s = float(sample.sequence[-1].time_s)
    if parent_duration_s < policy.window_duration_s:
        return None

    resampled_sample = resample_canonical_sample(
        sample,
        interval_s=policy.resample_interval_s,
    )
    observation_rows = build_observation_feature_rows(
        resampled_sample,
        config=transform_config,
    )
    if not observation_rows:
        return None

    selected_fields = get_lstm_sequence_fields(version_id)
    steps_per_window = policy.steps_per_window

    total_steps = len(observation_rows)
    if policy.allow_partial_tail_window:
        total_windows = math.ceil(total_steps / steps_per_window)
    else:
        total_windows = total_steps // steps_per_window
    if total_windows <= 0:
        return None

    original_time_keys = {round(point.time_s, 6) for point in sample.sequence}
    windows: list[WindowedSequenceSample] = []

    for window_index in range(total_windows):
        start_index = window_index * steps_per_window
        end_index = min(start_index + steps_per_window, total_steps)
        full_rows = observation_rows[start_index:end_index]
        if len(full_rows) < steps_per_window and not policy.allow_partial_tail_window:
            break
        if not full_rows:
            continue

        selected_rows = tuple(
            {field: row[field] for field in selected_fields}
            for row in full_rows
        )

        window_start_s = float(full_rows[0]["time_s"])
        nominal_window_end_s = window_start_s + policy.window_duration_s
        effective_window_end_s = min(nominal_window_end_s, parent_duration_s)
        progress_start = _safe_progress_ratio(window_start_s, parent_duration_s)
        progress_mid = _safe_progress_ratio(
            window_start_s + (effective_window_end_s - window_start_s) / 2.0,
            parent_duration_s,
        )
        progress_end = _safe_progress_ratio(effective_window_end_s, parent_duration_s)

        exact_match_count = sum(
            1
            for row in full_rows
            if round(float(row["time_s"]), 6) in original_time_keys
        )
        valid_step_ratio = len(full_rows) / steps_per_window
        interpolated_step_ratio = 1.0 - (exact_match_count / len(full_rows))
        mean_abs_current_a = float(
            np.mean([abs(float(row["current_a"])) for row in full_rows])
        )
        mean_power_w = float(np.mean([float(row["power_w"]) for row in full_rows]))

        late_charge_flag = int(progress_end >= policy.late_phase_threshold)
        crosses_late_phase = int(
            progress_start < policy.late_phase_threshold <= progress_end
        )
        phase_score = 1.0 if late_charge_flag == 0 else 0.6
        signal_score = 0.0 if mean_abs_current_a <= 1e-9 else 1.0
        proxy_quality_score = phase_score * valid_step_ratio * signal_score

        metadata = WindowMetadata(
            parent_sequence_id=sample.metadata.source_id,
            dataset_id=sample.metadata.dataset_id,
            cell_id=sample.metadata.cell_id,
            cycle_index=sample.metadata.cycle_index,
            observation_version=version_id,
            window_index=window_index,
            window_start_s=window_start_s,
            window_end_s=effective_window_end_s,
            window_duration_s=effective_window_end_s - window_start_s,
            resample_interval_s=policy.resample_interval_s,
            num_steps_after_resample=len(full_rows),
            parent_duration_s=parent_duration_s,
            window_start_progress_ratio=progress_start,
            window_mid_progress_ratio=progress_mid,
            window_end_progress_ratio=progress_end,
            late_charge_flag=late_charge_flag,
            crosses_late_phase=crosses_late_phase,
            valid_step_ratio=valid_step_ratio,
            interpolated_step_ratio=interpolated_step_ratio,
            mean_abs_current_a=mean_abs_current_a,
            mean_power_w=mean_power_w,
            proxy_quality_score=proxy_quality_score,
        )
        windows.append(
            WindowedSequenceSample(
                metadata=metadata,
                sequence_rows=selected_rows,
                target_soh_ratio=sample.metadata.soh_ratio,
            )
        )

    if not windows:
        return None

    return WindowedSequenceBundle(
        parent_metadata=sample.metadata,
        observation_version=version_id,
        windows=tuple(windows),
    )


def _build_resample_grid(total_duration_s: float, interval_s: float) -> list[float]:
    if total_duration_s < 0.0:
        raise ValueError("total_duration_s must be non-negative")
    if total_duration_s == 0.0:
        return [0.0]
    step_count = max(1, int(math.ceil(total_duration_s / interval_s)))
    return [index * interval_s for index in range(step_count)]


def _resample_temperature_series(
    sample: CanonicalSequenceSample,
    original_times_s: np.ndarray,
    resampled_times_s: np.ndarray,
) -> tuple[list[float | None], list[int]]:
    valid_temperature_pairs = [
        (point.time_s, point.temperature_c)
        for point in sample.sequence
        if point.temperature_mask == 1 and point.temperature_c is not None
    ]
    if not valid_temperature_pairs:
        return [None for _ in resampled_times_s], [0 for _ in resampled_times_s]

    valid_times_s = np.array([time_s for time_s, _ in valid_temperature_pairs], dtype=float)
    valid_temperature_values_c = np.array(
        [float(temperature_c) for _, temperature_c in valid_temperature_pairs],
        dtype=float,
    )
    if len(valid_times_s) == 1:
        filled_value = float(valid_temperature_values_c[0])
        return [filled_value for _ in resampled_times_s], [1 for _ in resampled_times_s]

    resampled_temperature_c = np.interp(
        resampled_times_s,
        valid_times_s,
        valid_temperature_values_c,
    )
    return [float(value) for value in resampled_temperature_c], [1 for _ in resampled_times_s]


def _safe_progress_ratio(position_s: float, total_duration_s: float) -> float:
    if total_duration_s <= 0.0:
        return 0.0
    return min(max(position_s / total_duration_s, 0.0), 1.0)
