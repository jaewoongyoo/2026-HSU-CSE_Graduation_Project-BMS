from __future__ import annotations

from collections.abc import Sequence


def to_relative_time_axis(time_values_s: Sequence[float]) -> list[float]:
    """Shift a sequence so that the first timestamp becomes zero."""

    if not time_values_s:
        return []

    base = float(time_values_s[0])
    return [float(value) - base for value in time_values_s]


def build_observed_mask(values: Sequence[float | None]) -> list[int]:
    """Return 1 for observed values and 0 for missing values."""

    return [0 if value is None else 1 for value in values]
