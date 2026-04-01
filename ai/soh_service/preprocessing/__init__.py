"""NASA/CALCE canonical preprocessing package."""

from soh_service.preprocessing.adapters import (
    build_calce_xlsx_canonical_samples,
    build_nasa_canonical_samples,
)
from soh_service.preprocessing.labels import (
    choose_baseline_capacity,
    compute_capacity_soh,
)
from soh_service.preprocessing.pipeline import (
    build_canonical_training_samples,
    build_metadata_table,
    build_preprocessing_artifacts,
    build_sequence_rows,
    export_preprocessing_artifacts,
    write_preprocessing_artifacts,
)
from soh_service.preprocessing.schemas import (
    CanonicalCycleMetadata,
    CanonicalSequencePoint,
    CanonicalSequenceSample,
)
from soh_service.preprocessing.splits import (
    build_group_split_assignments,
    build_split_manifest,
)

__all__ = [
    "CanonicalCycleMetadata",
    "CanonicalSequencePoint",
    "CanonicalSequenceSample",
    "build_calce_xlsx_canonical_samples",
    "build_nasa_canonical_samples",
    "build_canonical_training_samples",
    "build_metadata_table",
    "build_preprocessing_artifacts",
    "build_sequence_rows",
    "export_preprocessing_artifacts",
    "build_group_split_assignments",
    "build_split_manifest",
    "choose_baseline_capacity",
    "compute_capacity_soh",
    "write_preprocessing_artifacts",
]
