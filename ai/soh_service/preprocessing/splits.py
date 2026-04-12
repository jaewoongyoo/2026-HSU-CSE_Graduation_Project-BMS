"""Train/validation/test split rules for canonical preprocessing outputs."""

from __future__ import annotations

from typing import Literal

from soh_service.preprocessing.schemas import CanonicalSequenceSample

SplitName = Literal["train", "val", "test"]


def build_group_split_assignments(
    samples: list[CanonicalSequenceSample],
    train_ratio: float = 0.7,
    val_ratio: float = 0.15,
) -> dict[str, SplitName]:
    """Assign deterministic splits grouped by physical cell/battery id.

    When multiple datasets are present the split ratios are applied
    independently within each dataset so that every dataset contributes
    to every split.  This avoids the alphabetical-sort bias that would
    otherwise place all cells from one dataset into train and all cells
    from another into val/test.
    """

    if not samples:
        return {}
    if train_ratio <= 0 or val_ratio < 0 or train_ratio + val_ratio >= 1.0:
        raise ValueError("Split ratios must satisfy 0 < train and train + val < 1")

    # Collect unique (dataset_id, cell_id) keys per dataset.
    groups_by_dataset: dict[str, list[tuple[str, str]]] = {}
    for sample in samples:
        did = sample.metadata.dataset_id
        key = (did, sample.metadata.cell_id)
        if did not in groups_by_dataset:
            groups_by_dataset[did] = []
        if key not in groups_by_dataset[did]:
            groups_by_dataset[did].append(key)

    assignments_by_group: dict[tuple[str, str], SplitName] = {}
    for dataset_id in sorted(groups_by_dataset):
        group_keys = sorted(set(groups_by_dataset[dataset_id]))
        group_count = len(group_keys)
        for index, group_key in enumerate(group_keys):
            fraction = index / group_count
            if fraction < train_ratio:
                split: SplitName = "train"
            elif fraction < train_ratio + val_ratio:
                split = "val"
            else:
                split = "test"
            assignments_by_group[group_key] = split

    return {
        sample.metadata.source_id: assignments_by_group[
            (sample.metadata.dataset_id, sample.metadata.cell_id)
        ]
        for sample in samples
    }


def build_split_manifest(
    samples: list[CanonicalSequenceSample],
    train_ratio: float = 0.7,
    val_ratio: float = 0.15,
) -> list[dict[str, str]]:
    """Return a manifest-style split table for canonical samples."""

    assignments = build_group_split_assignments(
        samples,
        train_ratio=train_ratio,
        val_ratio=val_ratio,
    )
    manifest: list[dict[str, str]] = []
    for sample in sorted(samples, key=lambda item: item.metadata.source_id):
        manifest.append(
            {
                "dataset_id": sample.metadata.dataset_id,
                "cell_id": sample.metadata.cell_id,
                "source_id": sample.metadata.source_id,
                "split": assignments[sample.metadata.source_id],
            }
        )
    return manifest
