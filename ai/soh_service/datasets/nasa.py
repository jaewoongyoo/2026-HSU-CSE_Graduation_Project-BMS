from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterator

import numpy as np
import pandas as pd

NASA_CLEANED_DATASET_DIR = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "external"
    / "Li-ion_Battery_Aging_Datasets"
    / "cleaned_dataset"
)

_START_TIME_PATTERN = re.compile(r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?")
_SUPPORTED_CYCLE_TYPES = frozenset({"charge", "discharge", "impedance"})
_EMPTY_TOKENS = frozenset({"", "[]"})
_CYCLE_COLUMN_MAPS: dict[str, dict[str, str]] = {
    "charge": {
        "Time": "time_s",
        "Voltage_measured": "voltage_measured_v",
        "Current_measured": "current_measured_a",
        "Temperature_measured": "temperature_measured_c",
        "Current_charge": "current_charge_a",
        "Voltage_charge": "voltage_charge_v",
    },
    "discharge": {
        "Time": "time_s",
        "Voltage_measured": "voltage_measured_v",
        "Current_measured": "current_measured_a",
        "Temperature_measured": "temperature_measured_c",
        "Current_load": "current_load_a",
        "Voltage_load": "voltage_load_v",
    },
    "impedance": {
        "Sense_current": "sense_current_complex",
        "Battery_current": "battery_current_complex",
        "Current_ratio": "current_ratio_complex",
        "Battery_impedance": "battery_impedance_complex",
        "Rectified_Impedance": "rectified_impedance_complex",
    },
}


class NasaDatasetError(RuntimeError):
    """Raised when the NASA cleaned dataset cannot be parsed as expected."""


class NasaMissingDataFileError(NasaDatasetError):
    """Raised when metadata points to a data file that is not present on disk."""


NumericValue = float | complex | None
SampleValue = float | complex | None


@dataclass(frozen=True)
class NasaCycleMetadata:
    cycle_type: str
    start_time: datetime | None
    ambient_temperature_c: float | None
    battery_id: str
    test_id: int | None
    uid: int | None
    filename: str
    capacity_ah: float | None
    re_ohm: NumericValue
    rct_ohm: NumericValue
    file_exists: bool


@dataclass(frozen=True)
class NasaCycleRecord:
    metadata: NasaCycleMetadata
    samples: list[dict[str, SampleValue]]


@dataclass(frozen=True)
class NasaDatasetSummary:
    metadata_row_count: int
    available_cycle_count: int
    missing_cycle_count: int
    metadata_cycle_type_counts: dict[str, int]
    available_cycle_type_counts: dict[str, int]
    missing_filenames_sample: tuple[str, ...]


class NasaCleanedDatasetLoader:
    """Loader for the cleaned NASA battery aging CSV dataset."""

    def __init__(self, dataset_root: str | Path = NASA_CLEANED_DATASET_DIR) -> None:
        self.dataset_root = Path(dataset_root)
        self.metadata_path = self.dataset_root / "metadata.csv"
        self.data_dir = self.dataset_root / "data"
        self._metadata_cache: tuple[NasaCycleMetadata, ...] | None = None
        self._metadata_by_filename: dict[str, NasaCycleMetadata] | None = None

    def summarize(self) -> NasaDatasetSummary:
        metadata_rows = self._load_metadata_rows()
        missing_filenames = tuple(
            metadata.filename for metadata in metadata_rows if not metadata.file_exists
        )
        available_rows = [metadata for metadata in metadata_rows if metadata.file_exists]
        return NasaDatasetSummary(
            metadata_row_count=len(metadata_rows),
            available_cycle_count=len(available_rows),
            missing_cycle_count=len(missing_filenames),
            metadata_cycle_type_counts=dict(
                Counter(metadata.cycle_type for metadata in metadata_rows)
            ),
            available_cycle_type_counts=dict(
                Counter(metadata.cycle_type for metadata in available_rows)
            ),
            missing_filenames_sample=missing_filenames[:20],
        )

    def list_metadata(
        self,
        cycle_types: set[str] | None = None,
        battery_ids: set[str] | None = None,
        include_missing_files: bool = False,
    ) -> list[NasaCycleMetadata]:
        normalized_cycle_types = _normalize_cycle_types(cycle_types)
        normalized_battery_ids = set(battery_ids) if battery_ids else None

        selected: list[NasaCycleMetadata] = []
        for metadata in self._load_metadata_rows():
            if normalized_cycle_types and metadata.cycle_type not in normalized_cycle_types:
                continue
            if normalized_battery_ids and metadata.battery_id not in normalized_battery_ids:
                continue
            if not include_missing_files and not metadata.file_exists:
                continue
            selected.append(metadata)
        return selected

    def get_cycle_metadata(self, filename: str) -> NasaCycleMetadata:
        metadata = self._load_metadata_index().get(filename)
        if metadata is None:
            raise NasaDatasetError(f"NASA metadata does not contain filename: {filename}")
        return metadata

    def load_cycle(self, filename: str) -> NasaCycleRecord:
        metadata = self.get_cycle_metadata(filename)
        if not metadata.file_exists:
            raise NasaMissingDataFileError(
                f"NASA metadata references a missing data file: {filename}"
            )

        data_path = self.data_dir / filename
        frame = pd.read_csv(data_path)
        samples = _normalize_sample_frame(metadata.cycle_type, frame)
        return NasaCycleRecord(metadata=metadata, samples=samples)

    def iter_cycles(
        self,
        cycle_types: set[str] | None = None,
        battery_ids: set[str] | None = None,
        strict_missing_files: bool = False,
    ) -> Iterator[NasaCycleRecord]:
        for metadata in self.list_metadata(
            cycle_types=cycle_types,
            battery_ids=battery_ids,
            include_missing_files=True,
        ):
            if not metadata.file_exists:
                if strict_missing_files:
                    raise NasaMissingDataFileError(
                        f"NASA metadata references a missing data file: {metadata.filename}"
                    )
                continue
            yield self.load_cycle(metadata.filename)

    def _load_metadata_rows(self) -> tuple[NasaCycleMetadata, ...]:
        if self._metadata_cache is not None:
            return self._metadata_cache

        if not self.metadata_path.exists():
            raise NasaDatasetError(f"NASA metadata file not found: {self.metadata_path}")
        if not self.data_dir.exists():
            raise NasaDatasetError(f"NASA data directory not found: {self.data_dir}")

        frame = pd.read_csv(self.metadata_path, dtype=str, keep_default_na=False)
        if "type" not in frame.columns or "filename" not in frame.columns:
            raise NasaDatasetError(
                f"NASA metadata is missing required columns in {self.metadata_path}"
            )

        frame["type"] = frame["type"].str.strip()
        frame["filename"] = frame["filename"].str.strip()
        frame["battery_id"] = frame.get("battery_id", "").astype(str).str.strip()
        unsupported = sorted(set(frame["type"]).difference(_SUPPORTED_CYCLE_TYPES))
        if unsupported:
            raise NasaDatasetError(
                f"Unsupported NASA cycle type(s) in {self.metadata_path}: {unsupported}"
            )

        file_exists = frame["filename"].map(lambda value: (self.data_dir / value).exists())
        metadata_rows: list[NasaCycleMetadata] = []
        for row_index, row in frame.iterrows():
            metadata_rows.append(
                NasaCycleMetadata(
                    cycle_type=str(row["type"]),
                    start_time=_parse_start_time(row.get("start_time", "")),
                    ambient_temperature_c=_parse_optional_float(
                        row.get("ambient_temperature", "")
                    ),
                    battery_id=str(row.get("battery_id", "")),
                    test_id=_parse_optional_int(row.get("test_id", "")),
                    uid=_parse_optional_int(row.get("uid", "")),
                    filename=str(row["filename"]),
                    capacity_ah=_parse_optional_float(row.get("Capacity", "")),
                    re_ohm=_parse_optional_number(row.get("Re", "")),
                    rct_ohm=_parse_optional_number(row.get("Rct", "")),
                    file_exists=bool(file_exists.iloc[row_index]),
                )
            )

        self._metadata_cache = tuple(metadata_rows)
        self._metadata_by_filename = {
            metadata.filename: metadata for metadata in self._metadata_cache
        }
        return self._metadata_cache

    def _load_metadata_index(self) -> dict[str, NasaCycleMetadata]:
        if self._metadata_by_filename is None:
            self._load_metadata_rows()
        return self._metadata_by_filename or {}


def _normalize_cycle_types(cycle_types: set[str] | None) -> set[str] | None:
    if cycle_types is None:
        return None

    normalized = {cycle_type.strip() for cycle_type in cycle_types}
    unsupported = normalized.difference(_SUPPORTED_CYCLE_TYPES)
    if unsupported:
        raise NasaDatasetError(
            f"Unsupported NASA cycle types requested: {sorted(unsupported)}"
        )
    return normalized


def _parse_optional_float(value: object) -> float | None:
    normalized = _normalize_scalar_input(value)
    if normalized in _EMPTY_TOKENS:
        return None
    return float(normalized)


def _parse_optional_int(value: object) -> int | None:
    parsed = _parse_optional_float(value)
    if parsed is None:
        return None
    return int(parsed)


def _parse_optional_number(value: object) -> NumericValue:
    normalized = _normalize_scalar_input(value)
    if normalized in _EMPTY_TOKENS:
        return None
    if "j" in normalized.lower():
        return complex(normalized)
    return float(normalized)


def _parse_start_time(value: object) -> datetime | None:
    matches = _START_TIME_PATTERN.findall(_normalize_scalar_input(value))
    if len(matches) != 6:
        return None

    year, month, day, hour, minute = (int(float(item)) for item in matches[:5])
    second_float = float(matches[5])
    second = int(second_float)
    microsecond = int(round((second_float - second) * 1_000_000))
    if microsecond == 1_000_000:
        second += 1
        microsecond = 0
    return datetime(year, month, day, hour, minute, second, microsecond)


def _parse_required_float(value: object, field_name: str) -> float:
    parsed = _parse_optional_float(value)
    if parsed is None:
        raise NasaDatasetError(f"Missing required numeric field: {field_name}")
    return parsed


def _parse_optional_complex(value: object) -> complex | None:
    normalized = _normalize_scalar_input(value)
    if normalized in _EMPTY_TOKENS:
        return None
    return complex(normalized)


def _normalize_sample_frame(
    cycle_type: str, frame: pd.DataFrame
) -> list[dict[str, SampleValue]]:
    column_map = _CYCLE_COLUMN_MAPS.get(cycle_type)
    if column_map is None:
        raise NasaDatasetError(f"Unsupported NASA cycle type: {cycle_type}")

    missing_columns = [column for column in column_map if column not in frame.columns]
    if missing_columns:
        raise NasaDatasetError(
            f"NASA {cycle_type} cycle is missing required columns: {missing_columns}"
        )

    selected = frame.loc[:, list(column_map)].copy()
    selected.columns = [column_map[column] for column in column_map]

    if cycle_type == "impedance":
        for column in selected.columns:
            selected[column] = selected[column].map(_parse_optional_complex)
    else:
        for source_column, target_column in column_map.items():
            numeric_series = pd.to_numeric(frame[source_column], errors="coerce")
            if numeric_series.isna().any():
                raise NasaDatasetError(
                    f"Missing required numeric field: {source_column}"
                )
            selected[target_column] = numeric_series.astype(float)

    return _dataframe_to_records(selected)


def _dataframe_to_records(frame: pd.DataFrame) -> list[dict[str, SampleValue]]:
    columns = tuple(str(column) for column in frame.columns)
    records: list[dict[str, SampleValue]] = []
    for row in frame.itertuples(index=False, name=None):
        record = {
            columns[idx]: _to_python_scalar(value)
            for idx, value in enumerate(row)
        }
        records.append(record)
    return records


def _normalize_scalar_input(value: object) -> str:
    python_value = _to_python_scalar(value)
    if python_value is None:
        return ""
    if isinstance(python_value, str):
        return python_value.strip()
    return str(python_value).strip()


def _to_python_scalar(value: object) -> SampleValue:
    if value is None:
        return None
    if isinstance(value, pd.Timestamp):
        return value.to_pydatetime()
    if isinstance(value, np.generic):
        value = value.item()
    if isinstance(value, float) and np.isnan(value):
        return None
    return value  # type: ignore[return-value]
