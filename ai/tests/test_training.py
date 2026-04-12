from __future__ import annotations

import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as pq
import torch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from soh_service.preprocessing.pipeline import write_preprocessing_artifacts  # noqa: E402
from soh_service.preprocessing.schemas import (  # noqa: E402
    CanonicalCycleMetadata,
    CanonicalSequencePoint,
    CanonicalSequenceSample,
)
from soh_service.preprocessing.windowing import WindowPolicyConfig  # noqa: E402
from soh_service.training.data import (  # noqa: E402
    WindowBundleDataset,
    build_windowed_bundles_from_artifact,
    collate_window_bundles,
    compute_feature_normalization_stats,
    load_canonical_samples_from_artifact,
)
from soh_service.training.model import (  # noqa: E402
    HierarchicalLstmConfig,
    HierarchicalLstmRegressor,
)
from soh_service.training.runner import LstmTrainingConfig, run_lstm_training  # noqa: E402


def make_training_sample(
    *,
    source_id: str,
    cell_id: str,
    dataset_id: str,
    cycle_index: int,
    soh_ratio: float,
    duration_s: float = 1200.0,
) -> CanonicalSequenceSample:
    metadata = CanonicalCycleMetadata(
        dataset_id=dataset_id,  # type: ignore[arg-type]
        source_id=source_id,
        cell_id=cell_id,
        cycle_index=cycle_index,
        sequence_phase="charge",
        label_source_phase="discharge",
        source_format="csv",
        start_time=datetime(2024, 1, 1, 0, 0, 0),
        temperature_condition_c=25.0,
        baseline_capacity_ah=1.0,
        current_capacity_ah=soh_ratio,
        soh_ratio=soh_ratio,
        has_temperature=True,
        quality_flags=("ok",),
    )
    quarter = duration_s / 4.0
    sequence = (
        CanonicalSequencePoint(0.0, 3.60, 1.20, 25.0, 1, 0, 1, "charge"),
        CanonicalSequencePoint(quarter, 3.75, 1.00, 25.5, 1, 1, 1, "charge"),
        CanonicalSequencePoint(quarter * 2.0, 3.90, 0.80, 26.0, 1, 2, 1, "charge"),
        CanonicalSequencePoint(quarter * 3.0, 4.05, 0.60, 26.5, 1, 3, 1, "charge"),
        CanonicalSequencePoint(duration_s, 4.20, 0.40, 27.0, 1, 4, 1, "charge"),
    )
    return CanonicalSequenceSample(metadata=metadata, sequence=sequence)


class TrainingDataPathTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.artifact_dir = Path(self.temp_dir.name)

        samples = [
            make_training_sample(
                source_id="nasa:B1:001",
                cell_id="B1",
                dataset_id="nasa",
                cycle_index=1,
                soh_ratio=0.95,
            ),
            make_training_sample(
                source_id="nasa:B2:001",
                cell_id="B2",
                dataset_id="nasa",
                cycle_index=1,
                soh_ratio=0.90,
            ),
            make_training_sample(
                source_id="calce:C1:001",
                cell_id="C1",
                dataset_id="calce",
                cycle_index=1,
                soh_ratio=0.88,
            ),
        ]
        write_preprocessing_artifacts(samples, output_dir=self.artifact_dir)
        split_rows = [
            {
                "dataset_id": "nasa",
                "cell_id": "B1",
                "source_id": "nasa:B1:001",
                "split": "train",
            },
            {
                "dataset_id": "nasa",
                "cell_id": "B2",
                "source_id": "nasa:B2:001",
                "split": "val",
            },
            {
                "dataset_id": "calce",
                "cell_id": "C1",
                "source_id": "calce:C1:001",
                "split": "test",
            },
        ]
        pq.write_table(
            pa.Table.from_pylist(split_rows),
            self.artifact_dir / "splits.parquet",
        )

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_load_canonical_samples_filters_by_dataset_scope(self) -> None:
        nasa_only_samples = load_canonical_samples_from_artifact(
            artifact_dir=self.artifact_dir,
            split_name=None,
            dataset_scope="nasa_only",
        )
        nasa_calce_samples = load_canonical_samples_from_artifact(
            artifact_dir=self.artifact_dir,
            split_name=None,
            dataset_scope="nasa_calce",
        )

        self.assertEqual(len(nasa_only_samples), 2)
        self.assertEqual(len(nasa_calce_samples), 3)
        self.assertTrue(all(sample.metadata.dataset_id == "nasa" for sample in nasa_only_samples))

    def test_bundle_dataset_and_collate_follow_expected_shapes(self) -> None:
        bundles = build_windowed_bundles_from_artifact(
            artifact_dir=self.artifact_dir,
            split_name=None,
            dataset_scope="nasa_only",
            version_id="v1",
            window_policy=WindowPolicyConfig(
                window_duration_s=600.0,
                resample_interval_s=20.0,
            ),
        )

        feature_names = tuple(bundles[0].windows[0].sequence_rows[0].keys())
        normalization_stats = compute_feature_normalization_stats(bundles, feature_names)
        dataset = WindowBundleDataset(
            bundles,
            feature_names=feature_names,
            normalization_stats=normalization_stats,
        )
        batch = collate_window_bundles([dataset[0], dataset[1]])

        self.assertEqual(batch["inputs"].shape, (2, 2, 30, len(feature_names)))
        self.assertEqual(batch["window_mask"].shape, (2, 2))
        self.assertEqual(batch["targets"].shape, (2,))

    def test_hierarchical_lstm_forward_runs_on_bundle_batch(self) -> None:
        bundles = build_windowed_bundles_from_artifact(
            artifact_dir=self.artifact_dir,
            split_name=None,
            dataset_scope="nasa_only",
            version_id="v1",
            window_policy=WindowPolicyConfig(
                window_duration_s=600.0,
                resample_interval_s=20.0,
            ),
        )

        feature_names = tuple(bundles[0].windows[0].sequence_rows[0].keys())
        normalization_stats = compute_feature_normalization_stats(bundles, feature_names)
        dataset = WindowBundleDataset(
            bundles,
            feature_names=feature_names,
            normalization_stats=normalization_stats,
        )
        batch = collate_window_bundles([dataset[0], dataset[1]])

        model = HierarchicalLstmRegressor(
            HierarchicalLstmConfig(input_size=batch["inputs"].shape[-1])
        )
        predictions = model(batch["inputs"], batch["window_mask"])

        self.assertIsInstance(predictions, torch.Tensor)
        self.assertEqual(predictions.shape, (2,))

    def test_training_runner_writes_checkpoints_and_metrics(self) -> None:
        output_root = self.artifact_dir / "checkpoints"
        report_root = self.artifact_dir / "reports"

        result = run_lstm_training(
            LstmTrainingConfig(
                dataset_scope="nasa_only",
                observation_variant="v1",
                artifact_dir=str(self.artifact_dir),
                output_root=str(output_root),
                report_root=str(report_root),
                run_id="smoke",
                epochs=1,
                batch_size=1,
                learning_rate=1e-3,
            )
        )

        self.assertEqual(result["dataset_sizes"]["train"], 1)
        self.assertEqual(result["dataset_sizes"]["val"], 1)
        self.assertTrue((output_root / "nasa_only" / "v1" / "smoke" / "best.pt").exists())
        self.assertTrue((output_root / "nasa_only" / "v1" / "smoke" / "metrics.json").exists())
        self.assertTrue((report_root / "nasa_only" / "v1" / "smoke" / "summary.json").exists())


if __name__ == "__main__":
    unittest.main()
