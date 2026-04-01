from __future__ import annotations

import re
import zipfile
from collections import Counter
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from io import BytesIO, StringIO
from pathlib import Path
from typing import Callable, Iterator

import numpy as np
import pandas as pd

CALCE_DATASET_DIR = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "external"
    / "CALCE_Battery_Research_Data"
)

_SUPPORTED_EXTENSIONS = frozenset({".xlsx", ".txt", ".csv"})
_TEMPERATURE_PATTERN = re.compile(r"(\d+)degC?", re.IGNORECASE)


ScalarValue = str | int | float | bool | datetime | None


class CalceDatasetError(RuntimeError):
    """Raised when the CALCE dataset cannot be indexed or parsed."""


@dataclass(frozen=True)
class CalceFileMetadata:
    archive_name: str
    inner_path: str
    cell_id: str
    file_format: str
    file_kind: str
    inferred_test_date: date | None
    inferred_temperature_c: float | None
    note_flags: tuple[str, ...]


@dataclass(frozen=True)
class CalceRecord:
    metadata: CalceFileMetadata
    columns: tuple[str, ...]
    samples: list[dict[str, ScalarValue]]


@dataclass(frozen=True)
class CalceDatasetSummary:
    archive_count: int
    indexed_file_count: int
    format_counts: dict[str, int]
    file_kind_counts: dict[str, int]
    archive_file_counts: dict[str, int]


class CalceDatasetLoader:
    """Loader for the CALCE battery research archives stored as zip files."""

    def __init__(self, dataset_root: str | Path = CALCE_DATASET_DIR) -> None:
        self.dataset_root = Path(dataset_root)
        self._metadata_cache: tuple[CalceFileMetadata, ...] | None = None
        self._metadata_index: dict[tuple[str, str], CalceFileMetadata] | None = None

    def summarize(self) -> CalceDatasetSummary:
        metadata_rows = self._load_metadata_rows()
        return CalceDatasetSummary(
            archive_count=len({item.archive_name for item in metadata_rows}),
            indexed_file_count=len(metadata_rows),
            format_counts=dict(Counter(item.file_format for item in metadata_rows)),
            file_kind_counts=dict(Counter(item.file_kind for item in metadata_rows)),
            archive_file_counts=dict(Counter(item.archive_name for item in metadata_rows)),
        )

    def list_files(
        self,
        archive_names: set[str] | None = None,
        file_formats: set[str] | None = None,
        file_kinds: set[str] | None = None,
    ) -> list[CalceFileMetadata]:
        normalized_archives = set(archive_names) if archive_names else None
        normalized_formats = {value.lower() for value in file_formats} if file_formats else None
        normalized_kinds = {value.lower() for value in file_kinds} if file_kinds else None

        selected: list[CalceFileMetadata] = []
        for metadata in self._load_metadata_rows():
            if normalized_archives and metadata.archive_name not in normalized_archives:
                continue
            if normalized_formats and metadata.file_format.lower() not in normalized_formats:
                continue
            if normalized_kinds and metadata.file_kind.lower() not in normalized_kinds:
                continue
            selected.append(metadata)
        return selected

    def get_file_metadata(self, archive_name: str, inner_path: str) -> CalceFileMetadata:
        metadata = self._load_metadata_index().get((archive_name, inner_path))
        if metadata is None:
            raise CalceDatasetError(
                f"CALCE metadata does not contain file '{inner_path}' in archive '{archive_name}'"
            )
        return metadata

    def load_record(self, archive_name: str, inner_path: str) -> CalceRecord:
        metadata = self.get_file_metadata(archive_name, inner_path)
        archive_path = self.dataset_root / archive_name
        if not archive_path.exists():
            raise CalceDatasetError(f"CALCE archive not found: {archive_path}")

        with zipfile.ZipFile(archive_path) as archive:
            raw_bytes = archive.read(inner_path)

        if metadata.file_format == "txt":
            columns, samples = _parse_calce_txt(raw_bytes)
        elif metadata.file_format == "xlsx":
            columns, samples = _parse_calce_xlsx(raw_bytes)
        elif metadata.file_format == "csv":
            columns, samples = _parse_calce_temperature_csv(raw_bytes)
        else:
            raise CalceDatasetError(f"Unsupported CALCE file format: {metadata.file_format}")

        return CalceRecord(metadata=metadata, columns=columns, samples=samples)

    def iter_records(
        self,
        archive_names: set[str] | None = None,
        file_formats: set[str] | None = None,
        file_kinds: set[str] | None = None,
    ) -> Iterator[CalceRecord]:
        for metadata in self.list_files(
            archive_names=archive_names,
            file_formats=file_formats,
            file_kinds=file_kinds,
        ):
            yield self.load_record(metadata.archive_name, metadata.inner_path)

    def _load_metadata_rows(self) -> tuple[CalceFileMetadata, ...]:
        if self._metadata_cache is not None:
            return self._metadata_cache

        if not self.dataset_root.exists():
            raise CalceDatasetError(f"CALCE dataset root not found: {self.dataset_root}")

        rows: list[CalceFileMetadata] = []
        for archive_path in sorted(self.dataset_root.glob("*.zip")):
            with zipfile.ZipFile(archive_path) as archive:
                for inner_path in archive.namelist():
                    if inner_path.endswith("/"):
                        continue
                    extension = Path(inner_path).suffix.lower()
                    if extension not in _SUPPORTED_EXTENSIONS:
                        continue
                    rows.append(
                        CalceFileMetadata(
                            archive_name=archive_path.name,
                            inner_path=inner_path,
                            cell_id=archive_path.stem,
                            file_format=extension.lstrip("."),
                            file_kind=_infer_calce_file_kind(inner_path, extension),
                            inferred_test_date=_infer_test_date(inner_path),
                            inferred_temperature_c=_infer_temperature_c(inner_path),
                            note_flags=_infer_note_flags(inner_path),
                        )
                    )

        self._metadata_cache = tuple(rows)
        self._metadata_index = {
            (metadata.archive_name, metadata.inner_path): metadata
            for metadata in self._metadata_cache
        }
        return self._metadata_cache

    def _load_metadata_index(self) -> dict[tuple[str, str], CalceFileMetadata]:
        if self._metadata_index is None:
            self._load_metadata_rows()
        return self._metadata_index or {}


def _infer_calce_file_kind(inner_path: str, extension: str) -> str:
    lowered = inner_path.lower()
    if "/temperature/" in lowered or extension == ".csv":
        return "temperature_log"
    return "cycling_log"


def _infer_test_date(inner_path: str) -> date | None:
    stem = Path(inner_path).stem
    tokens = re.findall(r"\d+", stem)
    span = _infer_date_token_span(tokens)
    if span is None:
        return None
    return _build_date_from_tokens(tokens, span[0])


def _infer_temperature_c(inner_path: str) -> float | None:
    match = _TEMPERATURE_PATTERN.search(inner_path)
    if match is None:
        return None
    return float(match.group(1))


def _infer_note_flags(inner_path: str) -> tuple[str, ...]:
    lowered = inner_path.lower()
    flags: list[str] = []
    if _has_calce_split_suffix(lowered):
        flags.append("split_file")
    if "weird" in lowered:
        flags.append("operator_note")
    return tuple(flags)


def _has_calce_split_suffix(lowered_inner_path: str) -> bool:
    if re.search(r"(?i)(?:\bpart\b|\bpt\d+\b|[\s_-]+\d+\s*part\b)", lowered_inner_path):
        return True

    tokens = re.findall(r"\d+", Path(lowered_inner_path).stem)
    span = _infer_date_token_span(tokens)
    if span is None:
        return False
    suffix_tokens = tokens[span[1]:]
    return bool(suffix_tokens) and all(len(token) <= 2 for token in suffix_tokens)


def _infer_date_token_span(tokens: list[str]) -> tuple[int, int] | None:
    if len(tokens) < 3:
        return None
    for idx in range(len(tokens) - 3, -1, -1):
        year_token = tokens[idx + 2]
        if len(year_token) != 2:
            continue
        if _build_date_from_tokens(tokens, idx) is not None:
            return (idx, idx + 3)
    return None


def _build_date_from_tokens(tokens: list[str], start_index: int) -> date | None:
    month = int(tokens[start_index])
    day = int(tokens[start_index + 1])
    year = 2000 + int(tokens[start_index + 2])
    try:
        return date(year, month, day)
    except ValueError:
        return None


def _decode_text_bytes(raw_bytes: bytes) -> str:
    if raw_bytes.startswith(b"\xff\xfe") or raw_bytes.startswith(b"\xfe\xff"):
        return raw_bytes.decode("utf-16")
    if b"\x00" in raw_bytes[:200]:
        return raw_bytes.decode("utf-16-le")

    for encoding in ("utf-8-sig", "utf-8", "cp1252"):
        try:
            return raw_bytes.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw_bytes.decode("utf-8", errors="replace")


def _parse_calce_txt(raw_bytes: bytes) -> tuple[tuple[str, ...], list[dict[str, ScalarValue]]]:
    text = _decode_text_bytes(raw_bytes)
    frame = pd.read_csv(StringIO(text), sep="\t")
    prepared = _prepare_frame(frame)
    if len(prepared.columns) == 0:
        raise CalceDatasetError("Empty CALCE txt file")
    return _frame_to_records(prepared, _normalize_txt_header, _parse_scalar)


def _parse_calce_temperature_csv(
    raw_bytes: bytes,
) -> tuple[tuple[str, ...], list[dict[str, ScalarValue]]]:
    text = _decode_text_bytes(raw_bytes)
    lines = text.splitlines()
    header_index = next(
        (idx for idx, line in enumerate(lines) if line.startswith("Scan,")),
        None,
    )
    if header_index is None:
        raise CalceDatasetError("CALCE temperature csv header not found")

    frame = pd.read_csv(StringIO(text), skiprows=header_index)
    prepared = _prepare_frame(frame)
    if len(prepared.columns) == 0:
        raise CalceDatasetError("CALCE temperature csv header not found")
    return _frame_to_records(
        prepared,
        _normalize_temperature_csv_header,
        _parse_temperature_csv_value,
    )


def _parse_calce_xlsx(raw_bytes: bytes) -> tuple[tuple[str, ...], list[dict[str, ScalarValue]]]:
    try:
        with pd.ExcelFile(BytesIO(raw_bytes), engine="openpyxl") as workbook:
            if not workbook.sheet_names:
                raise CalceDatasetError("CALCE xlsx workbook does not contain sheets")

            selected_sheet = next(
                (name for name in workbook.sheet_names if name.lower() != "info"),
                workbook.sheet_names[0],
            )
            frame = pd.read_excel(workbook, sheet_name=selected_sheet, dtype=object)
    except ImportError as exc:
        raise CalceDatasetError(
            "CALCE xlsx parsing requires openpyxl to be installed in the runtime environment."
        ) from exc
    except ValueError as exc:
        raise CalceDatasetError(f"CALCE xlsx workbook could not be parsed: {exc}") from exc

    prepared = _prepare_frame(frame)
    if len(prepared.columns) == 0:
        raise CalceDatasetError("CALCE xlsx worksheet does not contain data rows")
    return _frame_to_records(prepared, _normalize_xlsx_header, _parse_xlsx_value)


def _prepare_frame(frame: pd.DataFrame) -> pd.DataFrame:
    prepared = frame.dropna(how="all").dropna(axis="columns", how="all")
    keep_columns = [
        column for column in prepared.columns if not _is_unnamed_column(column)
    ]
    if keep_columns:
        prepared = prepared.loc[:, keep_columns]
    return prepared.reset_index(drop=True)


def _frame_to_records(
    frame: pd.DataFrame,
    header_normalizer: Callable[[str], str],
    value_parser: Callable[[str, object], ScalarValue] | Callable[[object], ScalarValue],
) -> tuple[tuple[str, ...], list[dict[str, ScalarValue]]]:
    headers = [str(column).strip() for column in frame.columns]
    normalized_headers = tuple(header_normalizer(header) for header in headers)
    samples: list[dict[str, ScalarValue]] = []
    for row in frame.itertuples(index=False, name=None):
        sample: dict[str, ScalarValue] = {}
        for idx, raw_value in enumerate(row):
            parser = value_parser
            if parser in (_parse_temperature_csv_value, _parse_xlsx_value):
                parsed = parser(headers[idx], raw_value)  # type: ignore[misc]
            else:
                parsed = parser(raw_value)  # type: ignore[misc]
            sample[normalized_headers[idx]] = parsed
        samples.append(sample)
    return normalized_headers, samples


def _is_unnamed_column(value: object) -> bool:
    if value is None or pd.isna(value):
        return True
    normalized = str(value).strip()
    return normalized == "" or normalized.lower().startswith("unnamed:")


def _parse_scalar(value: object) -> ScalarValue:
    python_value = _to_python_scalar(value)
    if python_value is None:
        return None
    if isinstance(python_value, str):
        stripped = python_value.strip()
        if stripped == "":
            return None
        lowered = stripped.lower()
        if lowered == "true":
            return True
        if lowered == "false":
            return False
        try:
            if any(character in stripped for character in (".", "e", "E")):
                return float(stripped)
            return int(stripped)
        except ValueError:
            return stripped
    return python_value


def _normalize_txt_header(header: str) -> str:
    mapping = {
        "Time": "time_s",
        "Status code": "status_code",
        "Status category": "status_category",
        "Status color": "status_color",
        "Pgm code": "pgm_code",
        "Pgm step": "pgm_step",
        "Pgm para": "pgm_para",
        "Pgm cycle": "pgm_cycle",
        "mV": "voltage_mv",
        "mA": "current_ma",
        "Temperature": "temperature_c",
        "Duration": "duration_s",
        "Charge count": "charge_count",
        "Discharge count": "discharge_count",
        "Capacity": "capacity",
    }
    return mapping.get(header.strip(), _sanitize_name(header))


def _normalize_temperature_csv_header(header: str) -> str:
    mapping = {
        "Scan": "scan_index",
        "Time": "date_time_text",
        "Alarm 101": "alarm_101",
    }
    return mapping.get(header.strip(), _sanitize_name(header))


def _parse_temperature_csv_value(header: str, value: object) -> ScalarValue:
    normalized = _normalize_temperature_csv_header(header)
    parsed = _parse_scalar(value)
    if normalized == "date_time_text" and isinstance(parsed, str):
        for fmt in ("%m/%d/%Y %H:%M:%S:%f", "%m/%d/%Y %I:%M:%S %p"):
            try:
                return datetime.strptime(parsed, fmt)
            except ValueError:
                continue
    return parsed


def _normalize_xlsx_header(header: str) -> str:
    mapping = {
        "Data_Point": "data_point",
        "Test_Time(s)": "test_time_s",
        "Date_Time": "date_time",
        "Step_Time(s)": "step_time_s",
        "Step_Index": "step_index",
        "Cycle_Index": "cycle_index",
        "Current(A)": "current_a",
        "Voltage(V)": "voltage_v",
        "Charge_Capacity(Ah)": "charge_capacity_ah",
        "Discharge_Capacity(Ah)": "discharge_capacity_ah",
        "Charge_Energy(Wh)": "charge_energy_wh",
        "Discharge_Energy(Wh)": "discharge_energy_wh",
        "dV/dt(V/s)": "dv_dt_v_per_s",
        "Internal_Resistance(Ohm)": "internal_resistance_ohm",
        "Is_FC_Data": "is_fc_data",
        "AC_Impedance(Ohm)": "ac_impedance_ohm",
        "ACI_Phase_Angle(Deg)": "aci_phase_angle_deg",
    }
    return mapping.get(header.strip(), _sanitize_name(header))


def _parse_xlsx_value(header: str, value: object) -> ScalarValue:
    normalized = _normalize_xlsx_header(header)
    parsed = _parse_scalar(value)
    if normalized == "date_time":
        if isinstance(parsed, datetime):
            return parsed
        if isinstance(parsed, (int, float)):
            total_microseconds = round(float(parsed) * 24 * 60 * 60 * 1_000_000)
            return datetime(1899, 12, 30) + timedelta(microseconds=total_microseconds)
    if normalized == "is_fc_data":
        if isinstance(parsed, bool):
            return parsed
        if isinstance(parsed, (int, float)):
            return bool(int(parsed))
    return parsed


def _sanitize_name(value: str) -> str:
    normalized = re.sub(r"[^0-9a-zA-Z]+", "_", value.strip())
    normalized = normalized.strip("_").lower()
    return normalized or "field"


def _to_python_scalar(value: object) -> ScalarValue:
    if value is None:
        return None
    if isinstance(value, pd.Timestamp):
        return value.to_pydatetime()
    if isinstance(value, np.generic):
        value = value.item()
    if isinstance(value, float) and np.isnan(value):
        return None
    return value  # type: ignore[return-value]
