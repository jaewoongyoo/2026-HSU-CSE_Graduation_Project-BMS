from __future__ import annotations

import argparse
import sys
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from soh_service.datasets.calce import CalceDatasetLoader
from soh_service.datasets.nasa import NasaCleanedDatasetLoader
from soh_service.preprocessing import export_preprocessing_artifacts


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export canonical NASA/CALCE preprocessing artifacts.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Output directory. Defaults to ai/data/interim/canonical_nasa_calce_v1 with parquet artifacts.",
    )
    parser.add_argument(
        "--warmup-cycle-count",
        type=int,
        default=5,
        help="Number of early labeled cycles used for baseline capacity estimation.",
    )
    parser.add_argument(
        "--skip-nasa",
        action="store_true",
        help="Skip NASA canonical export.",
    )
    parser.add_argument(
        "--skip-calce",
        action="store_true",
        help="Skip CALCE canonical export.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    nasa_loader = None if args.skip_nasa else NasaCleanedDatasetLoader()
    calce_loader = None if args.skip_calce else CalceDatasetLoader()
    artifact_paths = export_preprocessing_artifacts(
        output_dir=args.output_dir,
        nasa_loader=nasa_loader,
        calce_loader=calce_loader,
        warmup_cycle_count=args.warmup_cycle_count,
    )

    for name in ("metadata", "sequences", "splits", "manifest"):
        print(f"{name}: {artifact_paths[name]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
