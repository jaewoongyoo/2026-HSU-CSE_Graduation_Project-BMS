"""NASA/CALCE canonical artifact를 읽어 표준 SOH 열화 곡선을 피팅해 JSON으로 저장한다.

실행:
    python scripts/fit_standard_curve.py \
        --dataset-scope nasa_calce \
        --output artifacts/curve_fit/standard_curve.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from soh_service.curve_fit.fitter import fit_standard_curve_from_artifact

DEFAULT_ARTIFACT_DIR = AI_ROOT / "data" / "interim" / "canonical_nasa_calce_v1"
DEFAULT_OUTPUT_PATH = AI_ROOT / "artifacts" / "curve_fit" / "standard_curve.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="NASA/CALCE canonical artifact → 표준 SOH 열화 곡선 피팅."
    )
    parser.add_argument(
        "--artifact-dir",
        type=str,
        default=str(DEFAULT_ARTIFACT_DIR),
        help="Canonical artifact 디렉토리 경로.",
    )
    parser.add_argument(
        "--dataset-scope",
        choices=("nasa_only", "calce_only", "nasa_calce"),
        default="nasa_calce",
        help="피팅에 사용할 데이터셋 범위.",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=str(DEFAULT_OUTPUT_PATH),
        help="표준 곡선 JSON 저장 경로.",
    )
    parser.add_argument(
        "--use-all-splits",
        action="store_true",
        default=False,
        help="지정 시 train/val/test 모두 사용. 기본은 train split만 사용.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    artifact = fit_standard_curve_from_artifact(
        artifact_dir=args.artifact_dir,
        dataset_scope=args.dataset_scope,
        only_train_split=not args.use_all_splits,
    )

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(artifact.to_dict(), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"표준 곡선 저장 완료: {output_path}")
    print(json.dumps(artifact.to_dict(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
