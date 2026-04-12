from __future__ import annotations

from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable, Literal

import numpy as np
import pyarrow.parquet as pq
import torch
from torch.utils.data import Dataset

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
from soh_service.preprocessing.windowing import (
    WindowPolicyConfig,
    WindowedSequenceBundle,
    build_windowed_sequence_bundle,
)

DatasetScope = Literal["nasa_only", "nasa_calce"]
SplitName = Literal["train", "val", "test"]

CANONICAL_ARTIFACT_DEFAULT_DIR = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "interim"
    / "canonical_nasa_calce_v1"
)
PASSTHROUGH_FEATURE_FIELDS = {"temperature_mask"}


@dataclass(frozen=True)
class FeatureNormalizationStats:
    feature_names: tuple[str, ...]
    mean_by_feature: dict[str, float]
    std_by_feature: dict[str, float]


@dataclass(frozen=True)
class BundleDatasetItem:
    source_id: str
    split: str | None
    target_soh_ratio: float
    inputs: torch.Tensor
    window_quality: torch.Tensor
    window_count: int


class WindowBundleDataset(Dataset[BundleDatasetItem]):
    def __init__(
        self,
        bundles: list[WindowedSequenceBundle],
        feature_names: tuple[str, ...],
        normalization_stats: FeatureNormalizationStats | None = None,
        split_name: str | None = None,
    ) -> None:
        self._bundles = bundles
        self._feature_names = feature_names
        self._normalization_stats = normalization_stats
        self._split_name = split_name

    def __len__(self) -> int:
        return len(self._bundles)

    def __getitem__(self, index: int) -> BundleDatasetItem:
        bundle = self._bundles[index]
        target_soh_ratio = bundle.parent_metadata.soh_ratio
        if target_soh_ratio is None:
            raise ValueError(f"Bundle {bundle.parent_metadata.source_id} does not have soh_ratio")

        window_tensors: list[np.ndarray] = []
        window_quality_scores: list[float] = []
        for window in bundle.windows:
            window_array = np.array(
                [
                    [_coerce_feature_value(row.get(field)) for field in self._feature_names]
                    for row in window.sequence_rows
                ],
                dtype=np.float32,
            )
            window_array = _normalize_window_array(
                window_array,
                feature_names=self._feature_names,
                normalization_stats=self._normalization_stats,
            )
            window_tensors.append(window_array)
            window_quality_scores.append(window.metadata.proxy_quality_score)

        inputs = torch.tensor(np.stack(window_tensors, axis=0), dtype=torch.float32)
        window_quality = torch.tensor(window_quality_scores, dtype=torch.float32)
        return BundleDatasetItem(
            source_id=bundle.parent_metadata.source_id,
            split=self._split_name,
            target_soh_ratio=float(target_soh_ratio),
            inputs=inputs,
            window_quality=window_quality,
            window_count=len(window_tensors),
        )


def collate_window_bundles(
    batch: list[BundleDatasetItem],
) -> dict[str, object]:
    if not batch:
        raise ValueError("batch must not be empty")

    batch_size = len(batch)
    max_window_count = max(item.window_count for item in batch)
    step_count = batch[0].inputs.shape[1]
    feature_count = batch[0].inputs.shape[2]

    padded_inputs = torch.zeros(
        (batch_size, max_window_count, step_count, feature_count),
        dtype=torch.float32,
    )
    window_mask = torch.zeros((batch_size, max_window_count), dtype=torch.bool)
    padded_window_quality = torch.zeros((batch_size, max_window_count), dtype=torch.float32)
    targets = torch.zeros(batch_size, dtype=torch.float32)
    window_counts = torch.zeros(batch_size, dtype=torch.long)
    source_ids: list[str] = []

    for batch_index, item in enumerate(batch):
        window_count = item.window_count
        padded_inputs[batch_index, :window_count] = item.inputs
        window_mask[batch_index, :window_count] = True
        padded_window_quality[batch_index, :window_count] = item.window_quality
        targets[batch_index] = item.target_soh_ratio
        window_counts[batch_index] = window_count
        source_ids.append(item.source_id)

    return {
        "inputs": padded_inputs,
        "window_mask": window_mask,
        "window_quality": padded_window_quality,
        "targets": targets,
        "window_counts": window_counts,
        "source_ids": source_ids,
    }


def build_window_bundle_datasets(
    artifact_dir: str | Path | None = None,
    dataset_scope: DatasetScope = "nasa_only",
    version_id: ObservationVariantId = "v1",
    window_policy: WindowPolicyConfig | None = None,
    transform_config: ObservationTransformConfig | None = None,
) -> dict[SplitName, WindowBundleDataset]:
    feature_names = get_lstm_sequence_fields(version_id)
    train_bundles = build_windowed_bundles_from_artifact(
        artifact_dir=artifact_dir,
        split_name="train",
        dataset_scope=dataset_scope,
        version_id=version_id,
        window_policy=window_policy,
        transform_config=transform_config,
    )
    normalization_stats = compute_feature_normalization_stats(
        train_bundles,
        feature_names=feature_names,
    )

    datasets: dict[SplitName, WindowBundleDataset] = {
        "train": WindowBundleDataset(
            train_bundles,
            feature_names=feature_names,
            normalization_stats=normalization_stats,
            split_name="train",
        )
    }
    for split_name in ("val", "test"):
        bundles = build_windowed_bundles_from_artifact(
            artifact_dir=artifact_dir,
            split_name=split_name,
            dataset_scope=dataset_scope,
            version_id=version_id,
            window_policy=window_policy,
            transform_config=transform_config,
        )
        datasets[split_name] = WindowBundleDataset(
            bundles,
            feature_names=feature_names,
            normalization_stats=normalization_stats,
            split_name=split_name,
        )
    return datasets


def build_windowed_bundles_from_artifact(
    artifact_dir: str | Path | None = None,
    split_name: SplitName | None = None,
    dataset_scope: DatasetScope = "nasa_only",
    version_id: ObservationVariantId = "v1",
    window_policy: WindowPolicyConfig | None = None,
    transform_config: ObservationTransformConfig | None = None,
) -> list[WindowedSequenceBundle]:
    canonical_samples = load_canonical_samples_from_artifact(
        artifact_dir=artifact_dir,
        split_name=split_name,
        dataset_scope=dataset_scope,
    )
    bundles: list[WindowedSequenceBundle] = []
    for sample in canonical_samples:
        bundle = build_windowed_sequence_bundle(
            sample,
            version_id=version_id,
            policy=window_policy,
            transform_config=transform_config,
        )
        if bundle is None:
            continue
        bundles.append(bundle)
    return bundles


def load_canonical_samples_from_artifact(
    artifact_dir: str | Path | None = None,
    split_name: SplitName | None = None,
    dataset_scope: DatasetScope = "nasa_only",
) -> list[CanonicalSequenceSample]:
    resolved_artifact_dir = Path(artifact_dir) if artifact_dir is not None else CANONICAL_ARTIFACT_DEFAULT_DIR
    metadata_rows = pq.read_table(resolved_artifact_dir / "metadata.parquet").to_pylist()
    sequence_rows = pq.read_table(resolved_artifact_dir / "sequences.parquet").to_pylist()
    split_rows = pq.read_table(resolved_artifact_dir / "splits.parquet").to_pylist()

    allowed_dataset_ids = _resolve_dataset_scope_ids(dataset_scope)
    split_by_source_id = {row["source_id"]: row["split"] for row in split_rows}

    selected_metadata_rows = [
        row
        for row in metadata_rows
        if row["dataset_id"] in allowed_dataset_ids
        and (split_name is None or split_by_source_id.get(row["source_id"]) == split_name)
    ]
    selected_source_ids = {row["source_id"] for row in selected_metadata_rows}
    sequence_rows_by_source_id: dict[str, list[dict[str, object]]] = defaultdict(list)
    for row in sequence_rows:
        source_id = row["source_id"]
        if source_id in selected_source_ids:
            sequence_rows_by_source_id[source_id].append(row)

    samples: list[CanonicalSequenceSample] = []
    for row in selected_metadata_rows:
        source_id = row["source_id"]
        rows_for_source = sorted(
            sequence_rows_by_source_id[source_id],
            key=lambda item: (
                float(item["time_s"]),
                -1 if item["sample_index"] is None else int(item["sample_index"]),
            ),
        )
        if not rows_for_source:
            continue
        metadata = CanonicalCycleMetadata(
            dataset_id=row["dataset_id"],
            source_id=source_id,
            cell_id=row["cell_id"],
            cycle_index=row["cycle_index"],
            sequence_phase=row["sequence_phase"],
            label_source_phase=row["label_source_phase"],
            source_format=row["source_format"],
            start_time=_parse_datetime(row["start_time"]),
            temperature_condition_c=row["temperature_condition_c"],
            baseline_capacity_ah=row["baseline_capacity_ah"],
            current_capacity_ah=row["current_capacity_ah"],
            soh_ratio=row["soh_ratio"],
            has_temperature=bool(row["has_temperature"]),
            quality_flags=_parse_quality_flags(row["quality_flags"]),
        )
        sequence = tuple(
            CanonicalSequencePoint(
                time_s=float(item["time_s"]),
                voltage_v=float(item["voltage_v"]),
                current_a=float(item["current_a"]),
                temperature_c=None if item["temperature_c"] is None else float(item["temperature_c"]),
                temperature_mask=int(item["temperature_mask"]),
                sample_index=None if item["sample_index"] is None else int(item["sample_index"]),
                step_index=None if item["step_index"] is None else int(item["step_index"]),
                raw_phase_hint=item["raw_phase_hint"],
            )
            for item in rows_for_source
        )
        samples.append(CanonicalSequenceSample(metadata=metadata, sequence=sequence))

    return sorted(
        samples,
        key=lambda sample: (
            sample.metadata.dataset_id,
            sample.metadata.cell_id,
            -1 if sample.metadata.cycle_index is None else sample.metadata.cycle_index,
            sample.metadata.source_id,
        ),
    )


def compute_feature_normalization_stats(
    bundles: Iterable[WindowedSequenceBundle],
    feature_names: tuple[str, ...],
) -> FeatureNormalizationStats:
    sum_by_feature = {feature_name: 0.0 for feature_name in feature_names}
    squared_sum_by_feature = {feature_name: 0.0 for feature_name in feature_names}
    count_by_feature = {feature_name: 0 for feature_name in feature_names}

    for bundle in bundles:
        for window in bundle.windows:
            for row in window.sequence_rows:
                for feature_name in feature_names:
                    if feature_name in PASSTHROUGH_FEATURE_FIELDS:
                        continue
                    value = _coerce_feature_value(row.get(feature_name))
                    sum_by_feature[feature_name] += value
                    squared_sum_by_feature[feature_name] += value * value
                    count_by_feature[feature_name] += 1

    mean_by_feature: dict[str, float] = {}
    std_by_feature: dict[str, float] = {}
    for feature_name in feature_names:
        if feature_name in PASSTHROUGH_FEATURE_FIELDS:
            mean_by_feature[feature_name] = 0.0
            std_by_feature[feature_name] = 1.0
            continue

        count = count_by_feature[feature_name]
        if count == 0:
            mean_by_feature[feature_name] = 0.0
            std_by_feature[feature_name] = 1.0
            continue

        mean = sum_by_feature[feature_name] / count
        variance = max((squared_sum_by_feature[feature_name] / count) - (mean * mean), 0.0)
        std = max(float(np.sqrt(variance)), 1e-6)
        mean_by_feature[feature_name] = mean
        std_by_feature[feature_name] = std

    return FeatureNormalizationStats(
        feature_names=feature_names,
        mean_by_feature=mean_by_feature,
        std_by_feature=std_by_feature,
    )


def serialize_feature_normalization_stats(
    stats: FeatureNormalizationStats,
) -> dict[str, object]:
    return {
        "feature_names": list(stats.feature_names),
        "mean_by_feature": dict(stats.mean_by_feature),
        "std_by_feature": dict(stats.std_by_feature),
    }


def serialize_window_policy_config(config: WindowPolicyConfig) -> dict[str, object]:
    return asdict(config)


def _resolve_dataset_scope_ids(dataset_scope: DatasetScope) -> set[str]:
    if dataset_scope == "nasa_only":
        return {"nasa"}
    if dataset_scope == "nasa_calce":
        return {"nasa", "calce"}
    raise ValueError(f"Unsupported dataset_scope: {dataset_scope}")


def _parse_datetime(value: object) -> datetime | None:
    if value in (None, ""):
        return None
    return datetime.fromisoformat(str(value))


def _parse_quality_flags(value: object) -> tuple[str, ...]:
    if value in (None, ""):
        return ()
    return tuple(flag for flag in str(value).split("|") if flag)


def _coerce_feature_value(value: object) -> float:
    if value is None:
        return 0.0
    return float(value)


def _normalize_window_array(
    window_array: np.ndarray,
    feature_names: tuple[str, ...],
    normalization_stats: FeatureNormalizationStats | None,
) -> np.ndarray:
    if normalization_stats is None:
        return window_array

    normalized = window_array.copy()
    for feature_index, feature_name in enumerate(feature_names):
        if feature_name in PASSTHROUGH_FEATURE_FIELDS:
            continue
        mean = normalization_stats.mean_by_feature.get(feature_name, 0.0)
        std = normalization_stats.std_by_feature.get(feature_name, 1.0)
        normalized[:, feature_index] = (normalized[:, feature_index] - mean) / std
    return normalized
