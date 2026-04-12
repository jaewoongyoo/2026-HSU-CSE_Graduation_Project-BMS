from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))

from soh_service.preprocessing import ObservationTransformConfig, WindowPolicyConfig
from soh_service.training.runner import LstmTrainingConfig, run_lstm_training


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train the first hierarchical LSTM SOH baseline from canonical artifacts.",
    )
    parser.add_argument(
        "--artifact-dir",
        type=str,
        default=None,
        help="Canonical artifact directory. Defaults to ai/data/interim/canonical_nasa_calce_v1.",
    )
    parser.add_argument(
        "--dataset-scope",
        choices=("nasa_only", "nasa_calce"),
        default="nasa_only",
        help="Dataset scope to train on.",
    )
    parser.add_argument(
        "--variant",
        choices=("v1", "v2", "v3"),
        default="v1",
        help="Observation transform variant to train.",
    )
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--step-hidden-size", type=int, default=64)
    parser.add_argument("--bundle-hidden-size", type=int, default=64)
    parser.add_argument("--dropout", type=float, default=0.1)
    parser.add_argument("--run-id", type=str, default=None)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--device", type=str, default="cpu")
    parser.add_argument("--window-duration-s", type=float, default=600.0)
    parser.add_argument("--resample-interval-s", type=float, default=20.0)
    parser.add_argument("--late-phase-threshold", type=float, default=0.8)
    parser.add_argument("--efficiency-value", type=float, default=0.85)
    parser.add_argument("--regulated-output-voltage-v", type=float, default=5.0)
    parser.add_argument(
        "--quality-weighting",
        action="store_true",
        default=False,
        help="Weight training loss by bundle-level proxy_quality_score.",
    )
    parser.add_argument(
        "--resume-from",
        type=str,
        default=None,
        help="Checkpoint directory to resume from (must contain last.pt and metrics.json).",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    result = run_lstm_training(
        resume_from=args.resume_from,
        config=LstmTrainingConfig(
            dataset_scope=args.dataset_scope,
            observation_variant=args.variant,
            artifact_dir=args.artifact_dir,
            run_id=args.run_id,
            epochs=args.epochs,
            batch_size=args.batch_size,
            learning_rate=args.learning_rate,
            step_hidden_size=args.step_hidden_size,
            bundle_hidden_size=args.bundle_hidden_size,
            dropout=args.dropout,
            random_seed=args.seed,
            device=args.device,
            use_quality_weighting=args.quality_weighting,
        ),
        window_policy=WindowPolicyConfig(
            window_duration_s=args.window_duration_s,
            resample_interval_s=args.resample_interval_s,
            late_phase_threshold=args.late_phase_threshold,
            allow_partial_tail_window=False,
        ),
        transform_config=ObservationTransformConfig(
            efficiency_value=args.efficiency_value,
            regulated_output_voltage_v=args.regulated_output_voltage_v,
        ),
    )
    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))


if __name__ == "__main__":
    main()
