from __future__ import annotations

from collections.abc import Sequence

import numpy as np


def choose_baseline_capacity(
    capacities_ah: Sequence[float | None],
    warmup_cycle_count: int = 5,
) -> float | None:
    """Return the median of the first valid capacities for baseline SOH."""

    valid = [
        float(value)
        for value in capacities_ah
        if value is not None and float(value) > 0.0
    ]
    if not valid:
        return None

    window = valid[: max(1, warmup_cycle_count)]
    return float(np.median(window))


def compute_capacity_soh(
    current_capacity_ah: float | None,
    baseline_capacity_ah: float | None,
) -> float | None:
    """Compute capacity-based SOH when both values are available."""

    if current_capacity_ah is None or baseline_capacity_ah is None:
        return None
    if baseline_capacity_ah <= 0:
        return None
    return float(current_capacity_ah / baseline_capacity_ah)
