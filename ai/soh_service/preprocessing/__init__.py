"""NASA/CALCE canonical preprocessing package."""

from soh_service.preprocessing.adapters import (
    build_calce_xlsx_canonical_samples,
    build_nasa_canonical_samples,
)
from soh_service.preprocessing.labels import (
    choose_baseline_capacity,
    compute_capacity_soh,
)
from soh_service.preprocessing.observation import (
    CORE_SEQUENCE_INPUT_FIELDS,
    OBSERVATION_DERIVED_FIELDS,
    OBSERVATION_VARIANTS,
    ObservationTransformConfig,
    ObservationVariantSpec,
    build_lstm_variant_rows,
    build_observation_feature_rows,
    build_observation_transform_metadata,
    compute_cumulative_energy_wh,
    compute_effective_output_power_series,
    compute_estimated_output_current_series,
    compute_power_series,
    get_lstm_sequence_fields,
    get_observation_variant_spec,
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
from soh_service.preprocessing.windowing import (
    WindowMetadata,
    WindowPolicyConfig,
    WindowedSequenceBundle,
    WindowedSequenceSample,
    build_windowed_sequence_bundle,
    resample_canonical_sample,
)

__all__ = [
    "CanonicalCycleMetadata",
    "CanonicalSequencePoint",
    "CanonicalSequenceSample",
    "CORE_SEQUENCE_INPUT_FIELDS",
    "OBSERVATION_DERIVED_FIELDS",
    "OBSERVATION_VARIANTS",
    "ObservationTransformConfig",
    "ObservationVariantSpec",
    "build_calce_xlsx_canonical_samples",
    "build_lstm_variant_rows",
    "build_nasa_canonical_samples",
    "build_observation_feature_rows",
    "build_observation_transform_metadata",
    "build_canonical_training_samples",
    "build_metadata_table",
    "build_preprocessing_artifacts",
    "build_sequence_rows",
    "compute_cumulative_energy_wh",
    "compute_effective_output_power_series",
    "compute_estimated_output_current_series",
    "compute_power_series",
    "export_preprocessing_artifacts",
    "get_lstm_sequence_fields",
    "get_observation_variant_spec",
    "build_group_split_assignments",
    "build_split_manifest",
    "choose_baseline_capacity",
    "compute_capacity_soh",
    "WindowMetadata",
    "WindowPolicyConfig",
    "WindowedSequenceBundle",
    "WindowedSequenceSample",
    "build_windowed_sequence_bundle",
    "resample_canonical_sample",
    "write_preprocessing_artifacts",
]
