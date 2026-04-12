from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from datetime import date, datetime
from pathlib import Path

import pyarrow.parquet as pq

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from soh_service.datasets.calce import (  # noqa: E402
    CalceArchiveMemberFingerprint,
    CalceFileMetadata,
    CalceRecord,
)
from soh_service.datasets.nasa import NasaCleanedDatasetLoader  # noqa: E402
from soh_service.preprocessing.adapters.calce import (  # noqa: E402
    _extract_calce_xlsx_candidates,
    build_calce_xlsx_canonical_samples,
)
from soh_service.preprocessing.adapters.nasa import (  # noqa: E402
    build_nasa_canonical_samples,
)
from soh_service.preprocessing.labels import (  # noqa: E402
    choose_baseline_capacity,
    compute_capacity_soh,
)
from soh_service.preprocessing.observation import (  # noqa: E402
    ObservationTransformConfig,
    build_lstm_variant_rows,
    build_observation_feature_rows,
    build_observation_transform_metadata,
    get_lstm_sequence_fields,
    get_observation_variant_spec,
)
from soh_service.preprocessing.pipeline import (  # noqa: E402
    build_metadata_table,
    build_canonical_training_samples,
    build_sequence_rows,
    export_preprocessing_artifacts,
    write_preprocessing_artifacts,
)
from soh_service.preprocessing.schemas import (  # noqa: E402
    CanonicalCycleMetadata,
    CanonicalSequencePoint,
    CanonicalSequenceSample,
)
from soh_service.preprocessing.splits import (  # noqa: E402
    build_group_split_assignments,
    build_split_manifest,
)
from soh_service.preprocessing.windowing import (  # noqa: E402
    WindowPolicyConfig,
    build_windowed_sequence_bundle,
    resample_canonical_sample,
)


def make_canonical_sample(
    source_id: str,
    cell_id: str,
    dataset_id: str = "nasa",
    cycle_index: int = 1,
    temperature_c: float | None = 25.0,
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
        current_capacity_ah=0.95,
        soh_ratio=0.95,
        has_temperature=temperature_c is not None,
        quality_flags=("ok",),
    )
    sequence = (
        CanonicalSequencePoint(
            time_s=0.0,
            voltage_v=3.7,
            current_a=1.0,
            temperature_c=temperature_c,
            temperature_mask=1 if temperature_c is not None else 0,
            sample_index=0,
            step_index=1,
            raw_phase_hint="charge",
        ),
        CanonicalSequencePoint(
            time_s=2.0,
            voltage_v=3.8,
            current_a=0.9,
            temperature_c=temperature_c,
            temperature_mask=1 if temperature_c is not None else 0,
            sample_index=1,
            step_index=1,
            raw_phase_hint="charge",
        ),
    )
    return CanonicalSequenceSample(metadata=metadata, sequence=sequence)


def make_long_canonical_sample(
    source_id: str,
    cell_id: str,
    duration_s: float = 1200.0,
    dataset_id: str = "nasa",
    cycle_index: int = 1,
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
        current_capacity_ah=0.9,
        soh_ratio=0.9,
        has_temperature=True,
        quality_flags=("ok",),
    )
    quarter = duration_s / 4.0
    sequence = (
        CanonicalSequencePoint(
            time_s=0.0,
            voltage_v=3.60,
            current_a=1.20,
            temperature_c=25.0,
            temperature_mask=1,
            sample_index=0,
            step_index=1,
            raw_phase_hint="charge",
        ),
        CanonicalSequencePoint(
            time_s=quarter,
            voltage_v=3.75,
            current_a=1.00,
            temperature_c=25.5,
            temperature_mask=1,
            sample_index=1,
            step_index=1,
            raw_phase_hint="charge",
        ),
        CanonicalSequencePoint(
            time_s=quarter * 2.0,
            voltage_v=3.90,
            current_a=0.80,
            temperature_c=26.0,
            temperature_mask=1,
            sample_index=2,
            step_index=1,
            raw_phase_hint="charge",
        ),
        CanonicalSequencePoint(
            time_s=quarter * 3.0,
            voltage_v=4.05,
            current_a=0.60,
            temperature_c=26.5,
            temperature_mask=1,
            sample_index=3,
            step_index=1,
            raw_phase_hint="charge",
        ),
        CanonicalSequencePoint(
            time_s=duration_s,
            voltage_v=4.20,
            current_a=0.40,
            temperature_c=27.0,
            temperature_mask=1,
            sample_index=4,
            step_index=1,
            raw_phase_hint="charge",
        ),
    )
    return CanonicalSequenceSample(metadata=metadata, sequence=sequence)


class PreprocessingHelpersTest(unittest.TestCase):
    def test_capacity_helpers_follow_expected_rules(self) -> None:
        baseline = choose_baseline_capacity([1.0, 0.95, None, 0.9], warmup_cycle_count=2)

        self.assertEqual(baseline, 0.975)
        self.assertEqual(compute_capacity_soh(0.9, 1.0), 0.9)
        self.assertIsNone(compute_capacity_soh(None, 1.0))

    def test_observation_transform_builds_expected_derived_series(self) -> None:
        sample = make_canonical_sample("obs:s1", "B1")

        rows = build_observation_feature_rows(
            sample,
            config=ObservationTransformConfig(
                efficiency_value=0.85,
                regulated_output_voltage_v=5.0,
            ),
        )

        self.assertEqual(len(rows), 2)
        self.assertAlmostEqual(rows[0]["power_w"], 3.7)
        self.assertAlmostEqual(rows[1]["power_w"], 3.42)
        self.assertAlmostEqual(rows[0]["cumulative_energy_wh"], 0.0)
        self.assertAlmostEqual(rows[1]["cumulative_energy_wh"], 0.001977777777777778)
        self.assertAlmostEqual(rows[0]["effective_output_power_w"], 3.145)
        self.assertAlmostEqual(rows[1]["effective_output_power_w"], 2.907)
        self.assertAlmostEqual(rows[1]["estimated_output_current_5v_a"], 0.5814)
    def test_observation_variant_registry_matches_three_step_ladder(self) -> None:
        self.assertEqual(
            get_lstm_sequence_fields("v1"),
            (
                "time_s",
                "voltage_v",
                "current_a",
                "temperature_c",
                "temperature_mask",
                "power_w",
            ),
        )
        self.assertIn("cumulative_energy_wh", get_lstm_sequence_fields("v2"))
        self.assertIn("effective_output_power_w", get_lstm_sequence_fields("v3"))
        self.assertIn("estimated_output_current_5v_a", get_lstm_sequence_fields("v3"))
        self.assertFalse(get_observation_variant_spec("v3").requires_full_sequence_context)

    def test_lstm_variant_rows_and_metadata_follow_selected_version(self) -> None:
        sample = make_canonical_sample("obs:s2", "B2")

        v2_rows = build_lstm_variant_rows(sample, "v2")
        v3_rows = build_lstm_variant_rows(sample, "v3")
        metadata = build_observation_transform_metadata(
            "v3",
            config=ObservationTransformConfig(
                efficiency_value=0.9,
                regulated_output_voltage_v=5.0,
            ),
        )

        self.assertEqual(
            set(v2_rows[0].keys()),
            set(get_lstm_sequence_fields("v2")),
        )
        self.assertNotIn("effective_output_power_w", v2_rows[0])
        self.assertIn("effective_output_power_w", v3_rows[0])
        self.assertEqual(metadata["observation_version"], "v3")
        self.assertFalse(metadata["requires_full_sequence_context"])
        self.assertEqual(metadata["efficiency_value"], 0.9)

    def test_resample_canonical_sample_builds_fixed_interval_grid(self) -> None:
        sample = make_long_canonical_sample("long:s1", "B-long", duration_s=1200.0)

        resampled = resample_canonical_sample(sample, interval_s=20.0)

        self.assertEqual(len(resampled.sequence), 60)
        self.assertEqual(resampled.sequence[0].time_s, 0.0)
        self.assertEqual(resampled.sequence[-1].time_s, 1180.0)
        self.assertAlmostEqual(resampled.sequence[15].time_s, 300.0)
        self.assertAlmostEqual(resampled.sequence[15].voltage_v, 3.75)

    def test_windowed_sequence_bundle_follows_v1_policy_shape(self) -> None:
        sample = make_long_canonical_sample("bundle:s1", "B-bundle", duration_s=1200.0)

        bundle = build_windowed_sequence_bundle(
            sample,
            version_id="v1",
            policy=WindowPolicyConfig(
                window_duration_s=600.0,
                resample_interval_s=20.0,
                late_phase_threshold=0.8,
                allow_partial_tail_window=False,
            ),
        )

        self.assertIsNotNone(bundle)
        assert bundle is not None
        self.assertEqual(bundle.observation_version, "v1")
        self.assertEqual(len(bundle.windows), 2)
        self.assertEqual(len(bundle.windows[0].sequence_rows), 30)
        self.assertEqual(len(bundle.windows[1].sequence_rows), 30)
        self.assertEqual(bundle.windows[0].sequence_rows[0]["time_s"], 0.0)
        self.assertEqual(bundle.windows[1].sequence_rows[0]["time_s"], 0.0)
        self.assertEqual(
            set(bundle.windows[0].sequence_rows[0].keys()),
            set(get_lstm_sequence_fields("v1")),
        )
        self.assertEqual(bundle.windows[0].metadata.window_start_s, 0.0)
        self.assertEqual(bundle.windows[0].metadata.window_end_s, 600.0)
        self.assertEqual(bundle.windows[0].metadata.late_charge_flag, 0)
        self.assertEqual(bundle.windows[0].metadata.proxy_quality_score, 1.0)
        self.assertEqual(bundle.windows[1].metadata.window_start_s, 600.0)
        self.assertEqual(bundle.windows[1].metadata.window_end_s, 1200.0)
        self.assertEqual(bundle.windows[1].metadata.late_charge_flag, 1)
        self.assertEqual(bundle.windows[1].metadata.crosses_late_phase, 1)
        self.assertEqual(bundle.windows[1].metadata.proxy_quality_score, 0.6)

    def test_windowed_sequence_bundle_drops_partial_tail_window_in_v1(self) -> None:
        sample = make_long_canonical_sample("bundle:s2", "B-tail", duration_s=1300.0)

        bundle = build_windowed_sequence_bundle(
            sample,
            version_id="v1",
            policy=WindowPolicyConfig(
                window_duration_s=600.0,
                resample_interval_s=20.0,
                allow_partial_tail_window=False,
            ),
        )

        self.assertIsNotNone(bundle)
        assert bundle is not None
        self.assertEqual(len(bundle.windows), 2)
        self.assertEqual(bundle.windows[-1].metadata.window_end_s, 1200.0)


class NasaPreprocessingAdapterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.dataset_root = Path(self.temp_dir.name)
        (self.dataset_root / "data").mkdir(parents=True, exist_ok=True)

        with (self.dataset_root / "metadata.csv").open(
            "w", newline="", encoding="utf-8"
        ) as handle:
            writer = csv.writer(handle)
            writer.writerow(
                [
                    "type",
                    "start_time",
                    "ambient_temperature",
                    "battery_id",
                    "test_id",
                    "uid",
                    "filename",
                    "Capacity",
                    "Re",
                    "Rct",
                ]
            )
            writer.writerow(
                [
                    "charge",
                    "[2010. 7. 21. 17. 0. 0.0]",
                    "25",
                    "B0001",
                    "1",
                    "1",
                    "charge_1.csv",
                    "",
                    "",
                    "",
                ]
            )
            writer.writerow(
                [
                    "discharge",
                    "[2010. 7. 21. 18. 0. 0.0]",
                    "25",
                    "B0001",
                    "2",
                    "2",
                    "discharge_1.csv",
                    "1.0",
                    "",
                    "",
                ]
            )
            writer.writerow(
                [
                    "charge",
                    "[2010. 7. 22. 17. 0. 0.0]",
                    "25",
                    "B0001",
                    "3",
                    "3",
                    "charge_2.csv",
                    "",
                    "",
                    "",
                ]
            )
            writer.writerow(
                [
                    "discharge",
                    "[2010. 7. 22. 18. 0. 0.0]",
                    "25",
                    "B0001",
                    "4",
                    "4",
                    "discharge_2.csv",
                    "0.8",
                    "",
                    "",
                ]
            )

        for filename in ("charge_1.csv", "charge_2.csv"):
            with (self.dataset_root / "data" / filename).open(
                "w", newline="", encoding="utf-8"
            ) as handle:
                writer = csv.writer(handle)
                writer.writerow(
                    [
                        "Voltage_measured",
                        "Current_measured",
                        "Temperature_measured",
                        "Current_charge",
                        "Voltage_charge",
                        "Time",
                    ]
                )
                writer.writerow(["3.50", "1.20", "24.0", "1.21", "4.10", "5.0"])
                writer.writerow(["3.70", "1.10", "24.5", "1.11", "4.18", "7.0"])

        for filename in ("discharge_1.csv", "discharge_2.csv"):
            with (self.dataset_root / "data" / filename).open(
                "w", newline="", encoding="utf-8"
            ) as handle:
                writer = csv.writer(handle)
                writer.writerow(
                    [
                        "Voltage_measured",
                        "Current_measured",
                        "Temperature_measured",
                        "Current_load",
                        "Voltage_load",
                        "Time",
                    ]
                )
                writer.writerow(["3.90", "-1.00", "25.0", "1.00", "3.90", "0.0"])

        self.loader = NasaCleanedDatasetLoader(self.dataset_root)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_build_nasa_canonical_samples_pairs_charge_with_discharge_labels(self) -> None:
        samples = build_nasa_canonical_samples(self.loader, warmup_cycle_count=1)

        self.assertEqual(len(samples), 2)
        self.assertEqual(samples[0].metadata.dataset_id, "nasa")
        self.assertEqual(samples[0].metadata.sequence_phase, "charge")
        self.assertEqual(samples[0].metadata.current_capacity_ah, 1.0)
        self.assertEqual(samples[0].metadata.soh_ratio, 1.0)
        self.assertEqual(samples[1].metadata.current_capacity_ah, 0.8)
        self.assertEqual(samples[1].metadata.soh_ratio, 0.8)
        self.assertEqual(samples[0].sequence[0].time_s, 0.0)
        self.assertEqual(samples[0].sequence[1].time_s, 2.0)
        self.assertEqual(samples[0].sequence[0].current_a, 1.2)


class CalcePreprocessingAdapterTest(unittest.TestCase):
    def _build_record(self) -> CalceRecord:
        metadata = CalceFileMetadata(
            archive_name="CS2_3.zip",
            inner_path="CS2_3/CS2_3_10_04_12.xlsx",
            cell_id="CS2_3",
            file_format="xlsx",
            file_kind="cycling_log",
            inferred_test_date=date(2012, 10, 4),
            inferred_temperature_c=None,
            note_flags=(),
        )
        samples = [
            {
                "data_point": 1,
                "test_time_s": 10.0,
                "date_time": datetime(2012, 10, 4, 12, 0, 0),
                "step_time_s": 10.0,
                "step_index": 1,
                "cycle_index": 1,
                "current_a": 0.8,
                "voltage_v": 3.8,
                "charge_capacity_ah": 0.02,
                "discharge_capacity_ah": 0.0,
            },
            {
                "data_point": 2,
                "test_time_s": 12.0,
                "date_time": datetime(2012, 10, 4, 12, 0, 2),
                "step_time_s": 12.0,
                "step_index": 1,
                "cycle_index": 1,
                "current_a": 0.7,
                "voltage_v": 3.9,
                "charge_capacity_ah": 0.03,
                "discharge_capacity_ah": 1.1,
            },
            {
                "data_point": 3,
                "test_time_s": 20.0,
                "date_time": datetime(2012, 10, 5, 12, 0, 0),
                "step_time_s": 20.0,
                "step_index": 2,
                "cycle_index": 2,
                "current_a": 0.75,
                "voltage_v": 3.85,
                "charge_capacity_ah": 0.02,
                "discharge_capacity_ah": 0.0,
            },
            {
                "data_point": 4,
                "test_time_s": 23.0,
                "date_time": datetime(2012, 10, 5, 12, 0, 3),
                "step_time_s": 23.0,
                "step_index": 2,
                "cycle_index": 2,
                "current_a": 0.72,
                "voltage_v": 3.95,
                "charge_capacity_ah": 0.03,
                "discharge_capacity_ah": 0.9,
            },
        ]
        return CalceRecord(
            metadata=metadata,
            columns=tuple(samples[0].keys()),
            samples=samples,
        )

    def test_extract_calce_candidates_builds_charge_sequences(self) -> None:
        candidates = _extract_calce_xlsx_candidates(self._build_record())

        self.assertEqual(len(candidates), 2)
        self.assertEqual(candidates[0].cycle_index, 1)
        self.assertEqual(candidates[0].time_values_s, (0.0, 2.0))
        self.assertEqual(candidates[0].step_indices, (1, 1))
        self.assertEqual(candidates[0].current_capacity_ah, 1.1)
        self.assertIn("missing_temperature", candidates[0].quality_flags)

    def test_extract_calce_candidates_excludes_discharge_rows(self) -> None:
        """Discharge rows (negative current) must not appear in charge sequences.

        Arbin Charge_Capacity(Ah) is cumulative from test start so it is
        always > 0 for cycles after the first, making a capacity-based mask
        include discharge rows.  Only instantaneous current > 0 is reliable.
        """
        from datetime import date
        from soh_service.datasets.calce import CalceFileMetadata, CalceRecord

        metadata = CalceFileMetadata(
            archive_name="CS2_3.zip",
            inner_path="CS2_3/CS2_3_discharge_test.xlsx",
            cell_id="CS2_3",
            file_format="xlsx",
            file_kind="cycling_log",
            inferred_test_date=date(2012, 10, 5),
            inferred_temperature_c=None,
            note_flags=(),
        )
        # Cycle 2 with cumulative charge_capacity_ah > 0 on all rows,
        # including the discharge row (negative current).
        samples = [
            {
                "test_time_s": 100.0,
                "step_index": 3,
                "cycle_index": 2,
                "current_a": 0.80,   # charge
                "voltage_v": 3.80,
                "charge_capacity_ah": 2.10,  # cumulative > 0 even on discharge rows
                "discharge_capacity_ah": 0.00,
            },
            {
                "test_time_s": 200.0,
                "step_index": 3,
                "cycle_index": 2,
                "current_a": 0.60,   # charge (CV taper)
                "voltage_v": 3.95,
                "charge_capacity_ah": 2.20,
                "discharge_capacity_ah": 0.00,
            },
            {
                "test_time_s": 300.0,
                "step_index": 4,
                "cycle_index": 2,
                "current_a": -1.00,  # discharge — must be excluded
                "voltage_v": 3.70,
                "charge_capacity_ah": 2.20,  # still > 0 (cumulative, unchanged)
                "discharge_capacity_ah": 1.05,
            },
        ]
        record = CalceRecord(
            metadata=metadata,
            columns=tuple(samples[0].keys()),
            samples=samples,
        )

        candidates = _extract_calce_xlsx_candidates(record)

        self.assertEqual(len(candidates), 1)
        self.assertEqual(candidates[0].cycle_index, 2)
        # Only the two charge rows must be in the sequence.
        self.assertEqual(len(candidates[0].current_values_a), 2)
        self.assertTrue(all(c > 0 for c in candidates[0].current_values_a),
                        "discharge row with negative current leaked into charge sequence")

    def test_build_calce_xlsx_canonical_samples_uses_baseline_labeling(self) -> None:
        class StubLoader:
            def __init__(self, record: CalceRecord) -> None:
                self.record = record

            def list_files(
                self,
                archive_names: set[str] | None = None,
                file_formats: set[str] | None = None,
                file_kinds: set[str] | None = None,
            ) -> list[CalceFileMetadata]:
                return [self.record.metadata]

            def load_record(self, archive_name: str, inner_path: str) -> CalceRecord:
                return self.record

        samples = build_calce_xlsx_canonical_samples(
            StubLoader(self._build_record()),  # type: ignore[arg-type]
            warmup_cycle_count=1,
        )

        self.assertEqual(len(samples), 2)
        self.assertEqual(samples[0].metadata.dataset_id, "calce")
        self.assertEqual(samples[0].metadata.soh_ratio, 1.0)
        self.assertAlmostEqual(samples[1].metadata.soh_ratio or 0.0, 0.8181818181)
        self.assertEqual(samples[0].sequence[0].temperature_mask, 0)
        self.assertEqual(samples[0].sequence[0].step_index, 1)

    def test_build_calce_xlsx_canonical_samples_reuses_parquet_cache(self) -> None:
        class CachedStubLoader:
            def __init__(
                self,
                record: CalceRecord,
                fingerprint: CalceArchiveMemberFingerprint,
            ) -> None:
                self.record = record
                self.fingerprint = fingerprint
                self.load_record_calls = 0

            def list_files(
                self,
                archive_names: set[str] | None = None,
                file_formats: set[str] | None = None,
                file_kinds: set[str] | None = None,
            ) -> list[CalceFileMetadata]:
                return [self.record.metadata]

            def load_record(self, archive_name: str, inner_path: str) -> CalceRecord:
                self.load_record_calls += 1
                return self.record

            def get_file_fingerprint(
                self,
                archive_name: str,
                inner_path: str,
            ) -> CalceArchiveMemberFingerprint:
                return self.fingerprint

        fingerprint = CalceArchiveMemberFingerprint(
            crc=101,
            file_size=202,
            compress_size=303,
        )
        loader = CachedStubLoader(self._build_record(), fingerprint)

        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)
            first_samples = build_calce_xlsx_canonical_samples(
                loader,  # type: ignore[arg-type]
                warmup_cycle_count=1,
                cache_dir=cache_dir,
            )
            second_samples = build_calce_xlsx_canonical_samples(
                loader,  # type: ignore[arg-type]
                warmup_cycle_count=1,
                cache_dir=cache_dir,
            )

            self.assertEqual(loader.load_record_calls, 1)
            self.assertEqual(first_samples, second_samples)
            self.assertTrue((cache_dir / "manifest.json").exists())
            self.assertEqual(len(list((cache_dir / "cache").glob("*.parquet"))), 1)

    def test_build_calce_xlsx_canonical_samples_invalidates_cache_on_fingerprint_change(
        self,
    ) -> None:
        class CachedStubLoader:
            def __init__(
                self,
                record: CalceRecord,
                fingerprint: CalceArchiveMemberFingerprint,
            ) -> None:
                self.record = record
                self.fingerprint = fingerprint
                self.load_record_calls = 0

            def list_files(
                self,
                archive_names: set[str] | None = None,
                file_formats: set[str] | None = None,
                file_kinds: set[str] | None = None,
            ) -> list[CalceFileMetadata]:
                return [self.record.metadata]

            def load_record(self, archive_name: str, inner_path: str) -> CalceRecord:
                self.load_record_calls += 1
                return self.record

            def get_file_fingerprint(
                self,
                archive_name: str,
                inner_path: str,
            ) -> CalceArchiveMemberFingerprint:
                return self.fingerprint

        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)
            first_loader = CachedStubLoader(
                self._build_record(),
                CalceArchiveMemberFingerprint(crc=1, file_size=2, compress_size=3),
            )
            second_loader = CachedStubLoader(
                self._build_record(),
                CalceArchiveMemberFingerprint(crc=4, file_size=5, compress_size=6),
            )

            build_calce_xlsx_canonical_samples(
                first_loader,  # type: ignore[arg-type]
                warmup_cycle_count=1,
                cache_dir=cache_dir,
            )
            build_calce_xlsx_canonical_samples(
                second_loader,  # type: ignore[arg-type]
                warmup_cycle_count=1,
                cache_dir=cache_dir,
            )

            self.assertEqual(first_loader.load_record_calls, 1)
            self.assertEqual(second_loader.load_record_calls, 1)
            self.assertEqual(len(list((cache_dir / "cache").glob("*.parquet"))), 2)


class PreprocessingPipelineTest(unittest.TestCase):
    def test_split_assignments_keep_same_cell_together(self) -> None:
        samples = [
            make_canonical_sample("s1", "B1"),
            make_canonical_sample("s2", "B1"),
            make_canonical_sample("s3", "B2"),
            make_canonical_sample("s4", "B3"),
        ]
        assignments = build_group_split_assignments(samples, train_ratio=0.5, val_ratio=0.25)
        manifest = build_split_manifest(samples, train_ratio=0.5, val_ratio=0.25)

        self.assertEqual(assignments["s1"], assignments["s2"])
        self.assertEqual(len(manifest), 4)
        self.assertEqual(manifest[0]["source_id"], "s1")

    def test_split_assignments_stratify_across_multiple_datasets(self) -> None:
        """Each dataset must contribute cells to every split.

        Without stratification the alphabetical sort of (dataset_id, cell_id)
        puts all CALCE cells into train and all NASA cells into val/test, which
        creates a cross-domain split rather than a within-dataset split.
        """
        nasa_samples = [
            make_canonical_sample(f"nasa:s{i}", f"N{i}", dataset_id="nasa")
            for i in range(1, 5)   # 4 NASA cells
        ]
        calce_samples = [
            make_canonical_sample(f"calce:s{i}", f"C{i}", dataset_id="calce")
            for i in range(1, 5)   # 4 CALCE cells
        ]
        assignments = build_group_split_assignments(
            nasa_samples + calce_samples, train_ratio=0.5, val_ratio=0.25
        )

        nasa_splits = {assignments[s.metadata.source_id] for s in nasa_samples}
        calce_splits = {assignments[s.metadata.source_id] for s in calce_samples}

        # Both datasets must have at least one cell in train.
        self.assertIn("train", nasa_splits, "NASA has no train cells")
        self.assertIn("train", calce_splits, "CALCE has no train cells")
        # Neither dataset should be confined to a single split.
        self.assertGreater(len(nasa_splits), 1, "all NASA cells collapsed into one split")
        self.assertGreater(len(calce_splits), 1, "all CALCE cells collapsed into one split")

    def test_metadata_and_sequence_tables_flatten_samples(self) -> None:
        class StubNasaLoader:
            pass

        metadata_rows = build_metadata_table([])
        sequence_rows = build_sequence_rows([])

        self.assertEqual(metadata_rows, [])
        self.assertEqual(sequence_rows, [])

    def test_write_preprocessing_artifacts_creates_expected_files(self) -> None:
        samples = [
            make_canonical_sample("nasa:s1", "B1", dataset_id="nasa", cycle_index=1),
            make_canonical_sample("calce:s2", "C1", dataset_id="calce", cycle_index=2),
        ]

        with tempfile.TemporaryDirectory() as temp_dir:
            artifact_paths = write_preprocessing_artifacts(
                samples,
                output_dir=Path(temp_dir) / "exports",
                warmup_cycle_count=3,
            )

            self.assertTrue(artifact_paths["metadata"].exists())
            self.assertTrue(artifact_paths["sequences"].exists())
            self.assertTrue(artifact_paths["splits"].exists())
            self.assertTrue(artifact_paths["manifest"].exists())

            manifest = json.loads(artifact_paths["manifest"].read_text(encoding="utf-8"))
            self.assertEqual(manifest["sample_count"], 2)
            self.assertEqual(manifest["sequence_row_count"], 4)
            self.assertEqual(manifest["warmup_cycle_count"], 3)
            self.assertEqual(manifest["files"]["metadata"], "metadata.parquet")
            self.assertEqual(manifest["storage"]["format"], "parquet")

            rows = pq.read_table(artifact_paths["sequences"]).to_pylist()
            self.assertEqual(len(rows), 4)
            self.assertEqual(rows[0]["source_id"], "nasa:s1")

    def test_export_preprocessing_artifacts_uses_loader_built_samples(self) -> None:
        class StubNasaLoader:
            pass

        class StubCalceLoader:
            pass

        original_builder = build_canonical_training_samples

        try:
            def fake_builder(
                nasa_loader=None,
                calce_loader=None,
                warmup_cycle_count: int = 5,
            ):
                self.assertIsInstance(nasa_loader, StubNasaLoader)
                self.assertIsInstance(calce_loader, StubCalceLoader)
                self.assertEqual(warmup_cycle_count, 2)
                return [
                    make_canonical_sample(
                        "nasa:s1",
                        "B1",
                        dataset_id="nasa",
                        cycle_index=1,
                    )
                ]

            import soh_service.preprocessing.pipeline as pipeline_module

            pipeline_module.build_canonical_training_samples = fake_builder
            with tempfile.TemporaryDirectory() as temp_dir:
                artifact_paths = export_preprocessing_artifacts(
                    output_dir=Path(temp_dir),
                    nasa_loader=StubNasaLoader(),  # type: ignore[arg-type]
                    calce_loader=StubCalceLoader(),  # type: ignore[arg-type]
                    warmup_cycle_count=2,
                )

                self.assertTrue(artifact_paths["manifest"].exists())
        finally:
            import soh_service.preprocessing.pipeline as pipeline_module

            pipeline_module.build_canonical_training_samples = original_builder


if __name__ == "__main__":
    unittest.main()
