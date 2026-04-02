from __future__ import annotations

import csv
import sys
import tempfile
import unittest
import zipfile
from datetime import date, datetime
from io import BytesIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from soh_service.datasets.calce import CalceDatasetLoader  # noqa: E402


class CalceDatasetLoaderTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.dataset_root = Path(self.temp_dir.name)
        self._write_txt_archive()
        self._write_xlsx_archive()
        self._write_metadata_edge_case_archive()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_txt_archive(self) -> None:
        archive_path = self.dataset_root / "CS2_8.zip"
        txt_body = (
            "Time\tStatus code\tPgm step\tmV\tmA\tTemperature\tCapacity\t\n"
            "0.000000\t8\t2\t4005\t552\t19\t0\t\n"
            "0.750233\t8\t2\t4030\t550\t20\t0\t\n"
        )
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr("CS2_8/CS2_8_1_19_10.txt", txt_body)

    def _write_xlsx_archive(self) -> None:
        archive_path = self.dataset_root / "CS2_35.zip"
        xlsx_bytes = _build_test_xlsx()
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr("CS2_35/CS2_35_10_15_10.xlsx", xlsx_bytes)

    def _write_metadata_edge_case_archive(self) -> None:
        archive_path = self.dataset_root / "CX2_4.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            archive.writestr("CX2_4/Temperature/25deg_10_5_12_1.csv", "Scan,Time,Alarm 101\n")
            archive.writestr("CX2_4/Temperature/45deg_10_1_12_pt2.csv", "Scan,Time,Alarm 101\n")
            archive.writestr("CX2_4/source data/45degC_10times_CX2_4_10_4_11 1 part.txt", "Time\tStatus code\n")

    def test_summary_counts_archives_and_formats(self) -> None:
        loader = CalceDatasetLoader(self.dataset_root)
        summary = loader.summarize()

        self.assertEqual(summary.archive_count, 3)
        self.assertEqual(summary.indexed_file_count, 5)
        self.assertEqual(summary.format_counts["txt"], 2)
        self.assertEqual(summary.format_counts["xlsx"], 1)
        self.assertEqual(summary.format_counts["csv"], 2)
        self.assertEqual(summary.file_kind_counts["cycling_log"], 3)
        self.assertEqual(summary.file_kind_counts["temperature_log"], 2)

    def test_txt_record_normalizes_core_fields(self) -> None:
        loader = CalceDatasetLoader(self.dataset_root)
        record = loader.load_record("CS2_8.zip", "CS2_8/CS2_8_1_19_10.txt")

        self.assertEqual(record.metadata.cell_id, "CS2_8")
        self.assertEqual(record.metadata.inferred_test_date, date(2010, 1, 19))
        self.assertEqual(record.columns[:6], ("time_s", "status_code", "pgm_step", "voltage_mv", "current_ma", "temperature_c"))
        self.assertEqual(record.samples[0]["time_s"], 0.0)
        self.assertEqual(record.samples[0]["voltage_mv"], 4005)
        self.assertEqual(record.samples[1]["temperature_c"], 20)

    def test_xlsx_record_normalizes_channel_sheet(self) -> None:
        loader = CalceDatasetLoader(self.dataset_root)
        record = loader.load_record("CS2_35.zip", "CS2_35/CS2_35_10_15_10.xlsx")

        self.assertEqual(record.metadata.cell_id, "CS2_35")
        self.assertEqual(record.metadata.inferred_test_date, date(2010, 10, 15))
        self.assertEqual(record.columns[:6], ("data_point", "test_time_s", "date_time", "step_time_s", "step_index", "cycle_index"))
        self.assertEqual(record.samples[0]["data_point"], 1)
        self.assertEqual(record.samples[0]["cycle_index"], 1)
        self.assertEqual(record.samples[0]["date_time"], datetime(2010, 10, 15, 12, 0, 0))
        self.assertTrue(record.samples[0]["is_fc_data"] is False)

    def test_metadata_inference_handles_split_suffixes_without_corrupting_dates(self) -> None:
        loader = CalceDatasetLoader(self.dataset_root)
        csv_metadata = loader.get_file_metadata(
            "CX2_4.zip",
            "CX2_4/Temperature/25deg_10_5_12_1.csv",
        )
        pt_metadata = loader.get_file_metadata(
            "CX2_4.zip",
            "CX2_4/Temperature/45deg_10_1_12_pt2.csv",
        )
        part_metadata = loader.get_file_metadata(
            "CX2_4.zip",
            "CX2_4/source data/45degC_10times_CX2_4_10_4_11 1 part.txt",
        )

        self.assertEqual(csv_metadata.inferred_test_date, date(2012, 10, 5))
        self.assertEqual(pt_metadata.inferred_test_date, date(2012, 10, 1))
        self.assertEqual(part_metadata.inferred_test_date, date(2011, 10, 4))
        self.assertIn("split_file", csv_metadata.note_flags)
        self.assertIn("split_file", pt_metadata.note_flags)
        self.assertIn("split_file", part_metadata.note_flags)


def _build_test_xlsx() -> bytes:
    workbook_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Info" sheetId="1" r:id="rId1"/>
    <sheet name="Channel_1-008" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>
"""
    workbook_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
</Relationships>
"""
    shared_strings = [
        "TEST REPORT",
        "Data_Point",
        "Test_Time(s)",
        "Date_Time",
        "Step_Time(s)",
        "Step_Index",
        "Cycle_Index",
        "Current(A)",
        "Voltage(V)",
        "Charge_Capacity(Ah)",
        "Discharge_Capacity(Ah)",
        "Charge_Energy(Wh)",
        "Discharge_Energy(Wh)",
        "dV/dt(V/s)",
        "Internal_Resistance(Ohm)",
        "Is_FC_Data",
        "AC_Impedance(Ohm)",
        "ACI_Phase_Angle(Deg)",
    ]
    shared_strings_xml = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
        '<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="{0}" uniqueCount="{0}">'.format(len(shared_strings)),
    ]
    for value in shared_strings:
        shared_strings_xml.append(f"<si><t>{value}</t></si>")
    shared_strings_xml.append("</sst>")

    sheet1_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1"><c r="A1" t="s"><v>0</v></c></row>
  </sheetData>
</worksheet>
"""
    sheet2_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1">
      <c r="A1" t="s"><v>1</v></c>
      <c r="B1" t="s"><v>2</v></c>
      <c r="C1" t="s"><v>3</v></c>
      <c r="D1" t="s"><v>4</v></c>
      <c r="E1" t="s"><v>5</v></c>
      <c r="F1" t="s"><v>6</v></c>
      <c r="G1" t="s"><v>7</v></c>
      <c r="H1" t="s"><v>8</v></c>
      <c r="I1" t="s"><v>9</v></c>
      <c r="J1" t="s"><v>10</v></c>
      <c r="K1" t="s"><v>11</v></c>
      <c r="L1" t="s"><v>12</v></c>
      <c r="M1" t="s"><v>13</v></c>
      <c r="N1" t="s"><v>14</v></c>
      <c r="O1" t="s"><v>15</v></c>
      <c r="P1" t="s"><v>16</v></c>
      <c r="Q1" t="s"><v>17</v></c>
    </row>
    <row r="2">
      <c r="A2"><v>1</v></c>
      <c r="B2"><v>30.0005</v></c>
      <c r="C2"><v>40466.5</v></c>
      <c r="D2"><v>30.0005</v></c>
      <c r="E2"><v>1</v></c>
      <c r="F2"><v>1</v></c>
      <c r="G2"><v>0.55</v></c>
      <c r="H2"><v>3.56</v></c>
      <c r="I2"><v>0.0045</v></c>
      <c r="J2"><v>0</v></c>
      <c r="K2"><v>0.016</v></c>
      <c r="L2"><v>0</v></c>
      <c r="M2"><v>0.0013</v></c>
      <c r="N2"><v>0</v></c>
      <c r="O2"><v>0</v></c>
      <c r="P2"><v>0</v></c>
      <c r="Q2"><v>0</v></c>
    </row>
  </sheetData>
</worksheet>
"""

    content_types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>
"""
    root_rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
"""

    buffer = BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", root_rels)
        archive.writestr("xl/workbook.xml", workbook_xml)
        archive.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        archive.writestr("xl/sharedStrings.xml", "\n".join(shared_strings_xml))
        archive.writestr("xl/worksheets/sheet1.xml", sheet1_xml)
        archive.writestr("xl/worksheets/sheet2.xml", sheet2_xml)
    return buffer.getvalue()


if __name__ == "__main__":
    unittest.main()
