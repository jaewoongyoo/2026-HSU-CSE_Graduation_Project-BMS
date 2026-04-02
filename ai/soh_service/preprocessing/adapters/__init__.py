"""Dataset-specific projection adapters for canonical preprocessing."""

from soh_service.preprocessing.adapters.calce import (
    CALCE_AUXILIARY_FORMATS,
    CALCE_PRIMARY_FORMATS,
    build_calce_xlsx_canonical_samples,
)
from soh_service.preprocessing.adapters.nasa import (
    NASA_AUXILIARY_PHASES,
    NASA_INPUT_PHASE,
    NASA_LABEL_PHASE,
    build_nasa_canonical_samples,
)

__all__ = [
    "CALCE_AUXILIARY_FORMATS",
    "CALCE_PRIMARY_FORMATS",
    "NASA_AUXILIARY_PHASES",
    "NASA_INPUT_PHASE",
    "NASA_LABEL_PHASE",
    "build_calce_xlsx_canonical_samples",
    "build_nasa_canonical_samples",
]
