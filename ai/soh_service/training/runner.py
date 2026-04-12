from __future__ import annotations

import json
import math
import random
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader

from soh_service.preprocessing.observation import (
    ObservationTransformConfig,
    ObservationVariantId,
    build_observation_transform_metadata,
)
from soh_service.preprocessing.windowing import WindowPolicyConfig
from soh_service.training.data import (
    CANONICAL_ARTIFACT_DEFAULT_DIR,
    DatasetScope,
    build_window_bundle_datasets,
    collate_window_bundles,
    serialize_feature_normalization_stats,
    serialize_window_policy_config,
)
from soh_service.training.model import HierarchicalLstmConfig, HierarchicalLstmRegressor

DEFAULT_CHECKPOINT_ROOT = (
    Path(__file__).resolve().parents[2] / "artifacts" / "checkpoints" / "lstm"
)
DEFAULT_REPORT_ROOT = Path(__file__).resolve().parents[2] / "artifacts" / "reports" / "lstm"


@dataclass(frozen=True)
class LstmTrainingConfig:
    dataset_scope: DatasetScope = "nasa_only"
    observation_variant: ObservationVariantId = "v1"
    artifact_dir: str | None = None
    output_root: str | None = None
    report_root: str | None = None
    run_id: str | None = None
    batch_size: int = 8
    epochs: int = 10
    learning_rate: float = 1e-3
    weight_decay: float = 0.0
    step_hidden_size: int = 64
    bundle_hidden_size: int = 64
    dropout: float = 0.1
    random_seed: int = 42
    num_workers: int = 0
    device: str = "cpu"
    use_quality_weighting: bool = False


@dataclass(frozen=True)
class TrainingRunArtifacts:
    checkpoint_dir: Path
    report_dir: Path
    best_checkpoint_path: Path
    last_checkpoint_path: Path
    config_path: Path
    metrics_path: Path
    transform_metadata_path: Path
    normalization_stats_path: Path
    summary_report_path: Path


def run_lstm_training(
    config: LstmTrainingConfig,
    window_policy: WindowPolicyConfig | None = None,
    transform_config: ObservationTransformConfig | None = None,
    resume_from: str | Path | None = None,
) -> dict[str, object]:
    window_policy = window_policy or WindowPolicyConfig()
    transform_config = transform_config or ObservationTransformConfig()
    _set_random_seed(config.random_seed)

    datasets = build_window_bundle_datasets(
        artifact_dir=config.artifact_dir or CANONICAL_ARTIFACT_DEFAULT_DIR,
        dataset_scope=config.dataset_scope,
        version_id=config.observation_variant,
        window_policy=window_policy,
        transform_config=transform_config,
    )
    if len(datasets["train"]) == 0:
        raise ValueError("Train dataset is empty")
    if len(datasets["val"]) == 0:
        raise ValueError("Validation dataset is empty")

    train_loader = DataLoader(
        datasets["train"],
        batch_size=config.batch_size,
        shuffle=True,
        num_workers=config.num_workers,
        collate_fn=collate_window_bundles,
    )
    val_loader = DataLoader(
        datasets["val"],
        batch_size=config.batch_size,
        shuffle=False,
        num_workers=config.num_workers,
        collate_fn=collate_window_bundles,
    )
    test_loader = DataLoader(
        datasets["test"],
        batch_size=config.batch_size,
        shuffle=False,
        num_workers=config.num_workers,
        collate_fn=collate_window_bundles,
    )

    input_size = len(datasets["train"][0].inputs.shape) and datasets["train"][0].inputs.shape[-1]
    model = HierarchicalLstmRegressor(
        HierarchicalLstmConfig(
            input_size=input_size,
            step_hidden_size=config.step_hidden_size,
            bundle_hidden_size=config.bundle_hidden_size,
            dropout=config.dropout,
        )
    )
    device = torch.device(config.device)
    model.to(device)

    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=config.learning_rate,
        weight_decay=config.weight_decay,
    )
    loss_fn = nn.MSELoss()
    use_quality_weighting = config.use_quality_weighting

    artifacts = _prepare_output_paths(config)
    normalization_stats = datasets["train"]._normalization_stats
    if normalization_stats is None:
        raise ValueError("Normalization stats must exist for training")

    history: list[dict[str, float | int]] = []
    best_val_mae = math.inf
    best_epoch = 0
    start_epoch = 1

    resume_best_checkpoint: Path | None = None
    if resume_from is not None:
        resume_dir = Path(resume_from)
        last_ckpt = resume_dir / "last.pt"
        prev_metrics = resume_dir / "metrics.json"
        if not last_ckpt.exists():
            raise FileNotFoundError(f"Resume checkpoint not found: {last_ckpt}")
        ckpt = torch.load(last_ckpt, map_location=device)
        model.load_state_dict(ckpt["model_state_dict"])
        optimizer.load_state_dict(ckpt["optimizer_state_dict"])
        start_epoch = ckpt["epoch"] + 1
        if prev_metrics.exists():
            prev = json.loads(prev_metrics.read_text(encoding="utf-8"))
            best_val_mae = prev.get("best_val_mae", math.inf)
            best_epoch = prev.get("best_epoch", 0)
            history = prev.get("history", [])
        # 이전 best.pt를 fallback으로 보존 (새 run에서 개선 없을 경우 사용)
        prev_best = resume_dir / "best.pt"
        if prev_best.exists():
            resume_best_checkpoint = prev_best

    for epoch_index in range(start_epoch, start_epoch + config.epochs):
        train_metrics = _run_epoch(
            model=model,
            loader=train_loader,
            device=device,
            optimizer=optimizer,
            loss_fn=loss_fn,
            training=True,
            use_quality_weighting=use_quality_weighting,
        )
        val_metrics = _run_epoch(
            model=model,
            loader=val_loader,
            device=device,
            optimizer=None,
            loss_fn=loss_fn,
            training=False,
            use_quality_weighting=False,
        )
        history.append(
            {
                "epoch": epoch_index,
                "train_loss": train_metrics["loss"],
                "train_mae": train_metrics["mae"],
                "train_rmse": train_metrics["rmse"],
                "val_loss": val_metrics["loss"],
                "val_mae": val_metrics["mae"],
                "val_rmse": val_metrics["rmse"],
            }
        )

        if val_metrics["mae"] < best_val_mae:
            best_val_mae = val_metrics["mae"]
            best_epoch = epoch_index
            _save_checkpoint(
                artifacts.best_checkpoint_path,
                model=model,
                optimizer=optimizer,
                epoch=epoch_index,
                metrics=val_metrics,
            )

    _save_checkpoint(
        artifacts.last_checkpoint_path,
        model=model,
        optimizer=optimizer,
        epoch=config.epochs,
        metrics=history[-1] if history else {},
    )

    # 새 run에서 best.pt가 갱신되지 않은 경우 이전 run의 best.pt를 복사
    if not artifacts.best_checkpoint_path.exists() and resume_best_checkpoint is not None:
        import shutil
        shutil.copy2(resume_best_checkpoint, artifacts.best_checkpoint_path)

    best_checkpoint = torch.load(artifacts.best_checkpoint_path, map_location=device)
    model.load_state_dict(best_checkpoint["model_state_dict"])
    test_metrics = (
        _run_epoch(
            model=model,
            loader=test_loader,
            device=device,
            optimizer=None,
            loss_fn=loss_fn,
            training=False,
            use_quality_weighting=False,
        )
        if len(datasets["test"]) > 0
        else {"loss": float("nan"), "mae": float("nan"), "rmse": float("nan")}
    )

    transform_metadata = build_observation_transform_metadata(
        config.observation_variant,
        config=transform_config,
    )
    transform_metadata["window_policy"] = serialize_window_policy_config(window_policy)
    metrics_payload = {
        "dataset_scope": config.dataset_scope,
        "observation_variant": config.observation_variant,
        "best_epoch": best_epoch,
        "best_val_mae": best_val_mae,
        "history": history,
        "test_metrics": test_metrics,
    }

    artifacts.config_path.write_text(
        json.dumps(asdict(config), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    artifacts.transform_metadata_path.write_text(
        json.dumps(transform_metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    artifacts.normalization_stats_path.write_text(
        json.dumps(
            serialize_feature_normalization_stats(normalization_stats),
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    artifacts.metrics_path.write_text(
        json.dumps(metrics_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    artifacts.summary_report_path.write_text(
        json.dumps(
            {
                "config": asdict(config),
                "best_epoch": best_epoch,
                "best_val_mae": best_val_mae,
                "test_metrics": test_metrics,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    return {
        "artifacts": {
            "checkpoint_dir": str(artifacts.checkpoint_dir),
            "report_dir": str(artifacts.report_dir),
            "best_checkpoint": str(artifacts.best_checkpoint_path),
            "last_checkpoint": str(artifacts.last_checkpoint_path),
            "config": str(artifacts.config_path),
            "metrics": str(artifacts.metrics_path),
            "transform_metadata": str(artifacts.transform_metadata_path),
            "normalization_stats": str(artifacts.normalization_stats_path),
            "summary_report": str(artifacts.summary_report_path),
        },
        "best_epoch": best_epoch,
        "best_val_mae": best_val_mae,
        "test_metrics": test_metrics,
        "dataset_sizes": {
            "train": len(datasets["train"]),
            "val": len(datasets["val"]),
            "test": len(datasets["test"]),
        },
    }


def _prepare_output_paths(config: LstmTrainingConfig) -> TrainingRunArtifacts:
    run_id = config.run_id or datetime.now().strftime("%Y%m%d_%H%M%S")
    checkpoint_root = Path(config.output_root) if config.output_root is not None else DEFAULT_CHECKPOINT_ROOT
    report_root = Path(config.report_root) if config.report_root is not None else DEFAULT_REPORT_ROOT

    checkpoint_dir = checkpoint_root / config.dataset_scope / config.observation_variant / run_id
    report_dir = report_root / config.dataset_scope / config.observation_variant / run_id
    checkpoint_dir.mkdir(parents=True, exist_ok=True)
    report_dir.mkdir(parents=True, exist_ok=True)

    return TrainingRunArtifacts(
        checkpoint_dir=checkpoint_dir,
        report_dir=report_dir,
        best_checkpoint_path=checkpoint_dir / "best.pt",
        last_checkpoint_path=checkpoint_dir / "last.pt",
        config_path=checkpoint_dir / "config.json",
        metrics_path=checkpoint_dir / "metrics.json",
        transform_metadata_path=checkpoint_dir / "transform_metadata.json",
        normalization_stats_path=checkpoint_dir / "normalization_stats.json",
        summary_report_path=report_dir / "summary.json",
    )


def _run_epoch(
    model: HierarchicalLstmRegressor,
    loader: DataLoader,
    device: torch.device,
    optimizer: torch.optim.Optimizer | None,
    loss_fn: nn.Module,
    training: bool,
    use_quality_weighting: bool = False,
) -> dict[str, float]:
    if training:
        model.train()
    else:
        model.eval()

    total_loss = 0.0
    total_absolute_error = 0.0
    total_squared_error = 0.0
    total_count = 0

    for batch in loader:
        inputs = batch["inputs"].to(device)
        window_mask = batch["window_mask"].to(device)
        targets = batch["targets"].to(device)

        if training:
            assert optimizer is not None
            optimizer.zero_grad()

        with torch.set_grad_enabled(training):
            predictions = model(inputs, window_mask)
            if use_quality_weighting:
                window_quality = batch["window_quality"].to(device)
                # aggregate window-level scores to bundle-level by masked mean
                quality_sum = (window_quality * window_mask.float()).sum(dim=1)
                actual_window_counts = window_mask.float().sum(dim=1).clamp(min=1.0)
                bundle_quality = quality_sum / actual_window_counts  # [B]
                # normalize within batch so mean weight = 1 (preserves loss scale)
                weights = bundle_quality / bundle_quality.mean().clamp(min=1e-6)
                loss = (weights * (predictions - targets) ** 2).mean()
            else:
                loss = loss_fn(predictions, targets)
            if training:
                loss.backward()
                optimizer.step()

        batch_size = targets.shape[0]
        errors = predictions.detach() - targets.detach()
        total_loss += loss.detach().item() * batch_size
        total_absolute_error += torch.abs(errors).sum().item()
        total_squared_error += torch.square(errors).sum().item()
        total_count += batch_size

    if total_count == 0:
        return {"loss": float("nan"), "mae": float("nan"), "rmse": float("nan")}

    mean_loss = total_loss / total_count
    mean_mae = total_absolute_error / total_count
    mean_rmse = math.sqrt(total_squared_error / total_count)
    return {"loss": mean_loss, "mae": mean_mae, "rmse": mean_rmse}


def _save_checkpoint(
    output_path: Path,
    model: HierarchicalLstmRegressor,
    optimizer: torch.optim.Optimizer,
    epoch: int,
    metrics: dict[str, object],
) -> None:
    torch.save(
        {
            "epoch": epoch,
            "model_state_dict": model.state_dict(),
            "optimizer_state_dict": optimizer.state_dict(),
            "metrics": metrics,
        },
        output_path,
    )


def _set_random_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
