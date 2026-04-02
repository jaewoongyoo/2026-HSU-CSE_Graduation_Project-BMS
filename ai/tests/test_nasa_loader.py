from __future__ import annotations

import csv
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from soh_service.datasets.nasa import (  # noqa: E402
    NasaCleanedDatasetLoader,
    NasaMissingDataFileError,
)


class NasaCleanedDatasetLoaderTest(unittest.TestCase):
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
                    "[2010. 7. 21. 17. 25. 40.671]",
                    "4",
                    "B0047",
                    "2",
                    "3",
                    "00003.csv",
                    "",
                    "",
                    "",
                ]
            )
            writer.writerow(
                [
                    "impedance",
                    "[2010. 7. 21. 16. 53. 45.968]",
                    "24",
                    "B0047",
                    "1",
                    "2",
                    "00002.csv",
                    "",
                    "(0.05605783343888099-0.001j)",
                    "0.20097016584458333",
                ]
            )
            writer.writerow(
                [
                    "discharge",
                    "[2010. 7. 22. 1. 40. 6.218]",
                    "4",
                    "B0047",
                    "6",
                    "7",
                    "00007.csv",
                    "1.5080762969973425",
                    "",
                    "",
                ]
            )

        with (self.dataset_root / "data" / "00003.csv").open(
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
            writer.writerow(["3.48", "0.00", "5.99", "-0.0006", "0.002", "0.0"])
            writer.writerow(["3.74", "1.48", "6.01", "1.4995", "4.618", "2.594"])

        with (self.dataset_root / "data" / "00002.csv").open(
            "w", newline="", encoding="utf-8"
        ) as handle:
            writer = csv.writer(handle)
            writer.writerow(
                [
                    "Sense_current",
                    "Battery_current",
                    "Current_ratio",
                    "Battery_impedance",
                    "Rectified_Impedance",
                ]
            )
            writer.writerow(
                [
                    "(928.3-48.4j)",
                    "(228.7-70.9j)",
                    "(3.76+0.95j)",
                    "(0.19+0.07j)",
                    "(0.17-0.02j)",
                ]
            )

        self.loader = NasaCleanedDatasetLoader(self.dataset_root)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_list_metadata_skips_missing_files_by_default(self) -> None:
        available = self.loader.list_metadata()
        self.assertEqual([item.filename for item in available], ["00003.csv", "00002.csv"])

        all_rows = self.loader.list_metadata(include_missing_files=True)
        self.assertEqual(len(all_rows), 3)
        self.assertFalse(all_rows[-1].file_exists)

    def test_load_charge_cycle_parses_start_time_and_values(self) -> None:
        cycle = self.loader.load_cycle("00003.csv")

        self.assertEqual(cycle.metadata.cycle_type, "charge")
        self.assertEqual(
            cycle.metadata.start_time,
            datetime(2010, 7, 21, 17, 25, 40, 671000),
        )
        self.assertEqual(len(cycle.samples), 2)
        self.assertEqual(
            set(cycle.samples[0].keys()),
            {
                "time_s",
                "voltage_measured_v",
                "current_measured_a",
                "temperature_measured_c",
                "current_charge_a",
                "voltage_charge_v",
            },
        )
        self.assertAlmostEqual(cycle.samples[1]["time_s"], 2.594)
        self.assertAlmostEqual(cycle.samples[1]["voltage_charge_v"], 4.618)

    def test_load_impedance_cycle_parses_complex_values(self) -> None:
        cycle = self.loader.load_cycle("00002.csv")
        sample = cycle.samples[0]

        self.assertEqual(
            cycle.metadata.re_ohm, complex("(0.05605783343888099-0.001j)")
        )
        self.assertEqual(sample["sense_current_complex"], complex("(928.3-48.4j)"))
        self.assertEqual(
            sample["rectified_impedance_complex"], complex("(0.17-0.02j)")
        )

    def test_iter_cycles_can_fail_on_missing_files(self) -> None:
        with self.assertRaises(NasaMissingDataFileError):
            list(self.loader.iter_cycles(strict_missing_files=True))

    def test_summary_reports_missing_counts(self) -> None:
        summary = self.loader.summarize()

        self.assertEqual(summary.metadata_row_count, 3)
        self.assertEqual(summary.available_cycle_count, 2)
        self.assertEqual(summary.missing_cycle_count, 1)
        self.assertEqual(summary.available_cycle_type_counts["charge"], 1)
        self.assertEqual(summary.available_cycle_type_counts["impedance"], 1)
        self.assertEqual(summary.missing_filenames_sample, ("00007.csv",))


if __name__ == "__main__":
    unittest.main()
