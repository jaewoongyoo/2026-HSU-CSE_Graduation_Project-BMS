"""
LSTM 기반 SOH 추론 모듈

체크포인트 디렉토리를 받아 모델과 전처리 파라미터를 로드하고,
실시간 텔레메트리 시퀀스에서 SOH를 추정한다.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch

from soh_service.preprocessing.observation import (
    ObservationTransformConfig,
    ObservationVariantId,
    get_lstm_sequence_fields,
)
from soh_service.preprocessing.schemas import (
    CanonicalCycleMetadata,
    CanonicalSequencePoint,
    CanonicalSequenceSample,
)
from soh_service.preprocessing.windowing import WindowPolicyConfig, build_windowed_sequence_bundle
from soh_service.training.data import FeatureNormalizationStats, PASSTHROUGH_FEATURE_FIELDS
from soh_service.training.model import HierarchicalLstmConfig, HierarchicalLstmRegressor


@dataclass(frozen=True)
class TelemetryPoint:
    """단일 시점 충전 텔레메트리 (Android BatteryManager 수집 기준)"""
    time_s: float
    voltage_v: float
    current_a: float
    temperature_c: float | None = None


def load_lstm_predictor(checkpoint_dir: str | Path) -> "LstmSOHPredictor":
    """
    체크포인트 디렉토리에서 LstmSOHPredictor를 로드한다.

    필요 파일: config.json, normalization_stats.json, transform_metadata.json, best.pt
    """
    checkpoint_dir = Path(checkpoint_dir)
    if not checkpoint_dir.is_dir():
        raise FileNotFoundError(f"체크포인트 디렉토리를 찾을 수 없습니다: {checkpoint_dir}")

    config_data = json.loads((checkpoint_dir / "config.json").read_text(encoding="utf-8"))
    norm_data = json.loads(
        (checkpoint_dir / "normalization_stats.json").read_text(encoding="utf-8")
    )
    transform_data = json.loads(
        (checkpoint_dir / "transform_metadata.json").read_text(encoding="utf-8")
    )

    version_id: ObservationVariantId = config_data["observation_variant"]
    feature_names = get_lstm_sequence_fields(version_id)

    normalization_stats = FeatureNormalizationStats(
        feature_names=tuple(norm_data["feature_names"]),
        mean_by_feature=norm_data["mean_by_feature"],
        std_by_feature=norm_data["std_by_feature"],
    )

    window_policy_data = transform_data.get("window_policy", {})
    window_policy = WindowPolicyConfig(
        window_duration_s=window_policy_data.get("window_duration_s", 600.0),
        resample_interval_s=window_policy_data.get("resample_interval_s", 20.0),
        late_phase_threshold=window_policy_data.get("late_phase_threshold", 0.8),
        allow_partial_tail_window=window_policy_data.get("allow_partial_tail_window", False),
    )

    transform_config = ObservationTransformConfig(
        efficiency_value=transform_data.get("efficiency_value", 0.85),
        regulated_output_voltage_v=transform_data.get("regulated_output_voltage_v", 5.0),
    )

    input_size = len(feature_names)
    lstm_config = HierarchicalLstmConfig(
        input_size=input_size,
        step_hidden_size=config_data.get("step_hidden_size", 64),
        bundle_hidden_size=config_data.get("bundle_hidden_size", 64),
        dropout=config_data.get("dropout", 0.1),
    )
    model = HierarchicalLstmRegressor(lstm_config)

    checkpoint = torch.load(checkpoint_dir / "best.pt", map_location="cpu")
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    return LstmSOHPredictor(
        model=model,
        feature_names=feature_names,
        normalization_stats=normalization_stats,
        window_policy=window_policy,
        transform_config=transform_config,
        version_id=version_id,
    )


class LstmSOHPredictor:
    """학습된 LSTM 모델로 SOH를 추론한다."""

    def __init__(
        self,
        model: HierarchicalLstmRegressor,
        feature_names: tuple[str, ...],
        normalization_stats: FeatureNormalizationStats,
        window_policy: WindowPolicyConfig,
        transform_config: ObservationTransformConfig,
        version_id: ObservationVariantId,
    ) -> None:
        self._model = model
        self._feature_names = feature_names
        self._normalization_stats = normalization_stats
        self._window_policy = window_policy
        self._transform_config = transform_config
        self._version_id = version_id

    def predict(self, telemetry: list[TelemetryPoint]) -> float:
        """
        충전 텔레메트리 시퀀스 → SOH 추정값

        Args:
            telemetry: 시간 오름차순으로 정렬된 TelemetryPoint 리스트

        Returns:
            SOH 추정값 (0.0 ~ 1.0)

        Raises:
            ValueError: 윈도우 생성에 필요한 최소 길이 미달
        """
        sample = _build_canonical_sample(telemetry)
        bundle = build_windowed_sequence_bundle(
            sample,
            version_id=self._version_id,
            policy=self._window_policy,
            transform_config=self._transform_config,
        )
        if bundle is None:
            raise ValueError(
                f"텔레메트리 시퀀스가 너무 짧습니다. "
                f"최소 {self._window_policy.window_duration_s:.0f}초 이상의 데이터가 필요합니다."
            )

        window_arrays: list[np.ndarray] = []
        for window in bundle.windows:
            raw = np.array(
                [
                    [_coerce(row.get(field)) for field in self._feature_names]
                    for row in window.sequence_rows
                ],
                dtype=np.float32,
            )
            window_arrays.append(_normalize(raw, self._feature_names, self._normalization_stats))

        # shape: [1, n_windows, steps, features]
        inputs = torch.tensor(
            np.stack(window_arrays, axis=0), dtype=torch.float32
        ).unsqueeze(0)
        window_mask = torch.ones(1, len(window_arrays), dtype=torch.bool)

        with torch.no_grad():
            prediction = self._model(inputs, window_mask)

        return float(torch.clamp(prediction.squeeze(), 0.0, 1.0).item())


# ── 내부 헬퍼 ─────────────────────────────────────────────────────────────────

def _build_canonical_sample(telemetry: list[TelemetryPoint]) -> CanonicalSequenceSample:
    """TelemetryPoint 리스트 → CanonicalSequenceSample (inference용 dummy metadata)"""
    has_temperature = any(p.temperature_c is not None for p in telemetry)
    sequence = tuple(
        CanonicalSequencePoint(
            time_s=point.time_s,
            voltage_v=point.voltage_v,
            current_a=point.current_a,
            temperature_c=point.temperature_c,
            temperature_mask=1 if point.temperature_c is not None else 0,
            sample_index=idx,
        )
        for idx, point in enumerate(telemetry)
    )
    metadata = CanonicalCycleMetadata(
        dataset_id="nasa",  # dummy: inference path에서 사용되지 않음
        source_id="inference:live",
        cell_id="unknown",
        cycle_index=None,
        sequence_phase="charge",
        label_source_phase=None,
        source_format=None,
        start_time=None,
        temperature_condition_c=None,
        baseline_capacity_ah=None,
        current_capacity_ah=None,
        soh_ratio=None,
        has_temperature=has_temperature,
    )
    return CanonicalSequenceSample(metadata=metadata, sequence=sequence)


def _coerce(value: object) -> float:
    return 0.0 if value is None else float(value)


def _normalize(
    array: np.ndarray,
    feature_names: tuple[str, ...],
    stats: FeatureNormalizationStats,
) -> np.ndarray:
    normalized = array.copy()
    for idx, name in enumerate(feature_names):
        if name in PASSTHROUGH_FEATURE_FIELDS:
            continue
        mean = stats.mean_by_feature.get(name, 0.0)
        std = stats.std_by_feature.get(name, 1.0)
        normalized[:, idx] = (normalized[:, idx] - mean) / std
    return normalized
