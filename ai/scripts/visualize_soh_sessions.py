"""
SOH 구간별 충전 세션 시각화 스크립트.

동일 셀의 충전 세션을 SOH 구간별로 overlay해서
SOH 하락 시 voltage/current 패턴 변화가 실제로 존재하는지 확인한다.

사용법:
    python scripts/visualize_soh_sessions.py
    python scripts/visualize_soh_sessions.py --dataset calce --cell CS2_36
    python scripts/visualize_soh_sessions.py --dataset nasa --cell B0045
    python scripts/visualize_soh_sessions.py --output-dir artifacts/reports/soh_vis
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.cm as cm
import numpy as np
import pandas as pd

AI_ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = AI_ROOT / "data/interim/canonical_nasa_calce_v1_mixed_smoke"
DEFAULT_OUTPUT = AI_ROOT / "artifacts/reports/soh_vis"

SOH_TARGETS = [1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3]
SOH_TOLERANCE = 0.04
WINDOW_SECONDS = 600.0  # 10분


def load_data(artifact_dir: Path) -> tuple[pd.DataFrame, pd.DataFrame]:
    meta = pd.read_parquet(artifact_dir / "metadata.parquet")
    seq = pd.read_parquet(artifact_dir / "sequences.parquet")
    return meta, seq


def _calce_sort_key(source_id: str) -> tuple:
    """CALCE source_id에서 (year, month, day, cycle) 순서키 추출."""
    m = re.search(r"_(\d+)_(\d+)_(\d+)\.xlsx#cycle=(\d+)", source_id)
    if m:
        month, day, year, cycle = int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4))
        return (year, month, day, cycle)
    return (0, 0, 0, 0)


def assign_global_cycle_order(cell_meta: pd.DataFrame, dataset_id: str) -> pd.DataFrame:
    """각 데이터셋 특성에 맞게 시계열 순서를 보장하는 global_order 컬럼 추가."""
    cell_meta = cell_meta.copy()
    if dataset_id == "calce":
        cell_meta["_sort_key"] = cell_meta["source_id"].apply(_calce_sort_key)
        cell_meta = cell_meta.sort_values("_sort_key").drop(columns="_sort_key")
    else:
        # NASA: start_time 기준 정렬
        cell_meta = cell_meta.sort_values("start_time")
    cell_meta["global_order"] = range(len(cell_meta))
    return cell_meta


def pick_representative_cycles(
    cell_meta: pd.DataFrame,
    targets: list[float],
    tolerance: float,
) -> pd.DataFrame:
    """각 SOH 타겟에 가장 가까운 사이클 1개씩 선택. global_order 기준 중간값 선호."""
    picked = []
    for target in targets:
        candidates = cell_meta[
            (cell_meta["soh_ratio"] >= target - tolerance)
            & (cell_meta["soh_ratio"] <= target + tolerance)
        ]
        if candidates.empty:
            continue
        best = candidates.iloc[(candidates["soh_ratio"] - target).abs().argsort()[:1]]
        picked.append(best)
    if not picked:
        return pd.DataFrame()
    return pd.concat(picked).drop_duplicates(subset="source_id")


def plot_soh_trajectory(
    cell_meta: pd.DataFrame,
    dataset_id: str,
    cell_id: str,
    output_dir: Path,
) -> None:
    """시계열 순서 기준 SOH 추이 그래프."""
    ordered = assign_global_cycle_order(cell_meta, dataset_id)
    fig, ax = plt.subplots(figsize=(10, 4))
    ax.plot(ordered["global_order"], ordered["soh_ratio"], "o-", markersize=2, linewidth=1)
    ax.set_xlabel("Cycle (chronological order)")
    ax.set_ylabel("SOH")
    ax.set_title(f"{dataset_id} / {cell_id}  —  SOH trajectory (chronological)")
    ax.grid(True, alpha=0.3)
    ax.axhline(0.8, color="red", linestyle="--", alpha=0.5, label="SOH=0.8")
    ax.legend()
    plt.tight_layout()
    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / f"{dataset_id}_{cell_id}_soh_trajectory.png"
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  [{dataset_id}/{cell_id}] SOH trajectory: {out_path}")


def plot_full_sessions(
    cell_meta: pd.DataFrame,
    seq: pd.DataFrame,
    dataset_id: str,
    cell_id: str,
    output_dir: Path,
) -> None:
    """SOH 구간별 전체 충전 세션 overlay."""
    ordered = assign_global_cycle_order(cell_meta, dataset_id)
    picked = pick_representative_cycles(ordered, SOH_TARGETS, SOH_TOLERANCE)
    if picked.empty:
        print(f"  [{dataset_id}/{cell_id}] 대표 사이클 없음, 스킵")
        return

    picked = picked.sort_values("soh_ratio", ascending=False)
    n = len(picked)
    colors = cm.RdYlGn(np.linspace(0.15, 0.85, n))

    fig, axes = plt.subplots(2, 1, figsize=(12, 8), sharex=False)
    fig.suptitle(f"{dataset_id} / {cell_id}  —  Full session by SOH level", fontsize=13)

    for ax, ch, ylabel in zip(axes, ["voltage_v", "current_a"], ["Voltage (V)", "Current (A)"]):
        for i, (_, row) in enumerate(picked.iterrows()):
            s = seq[seq["source_id"] == row["source_id"]].copy()
            if s.empty:
                continue
            t = (s["time_s"].values - s["time_s"].values[0]) / 60.0
            ax.plot(t, s[ch].values, color=colors[i], linewidth=1.2, alpha=0.85,
                    label=f"SOH={row['soh_ratio']:.3f}")
        ax.set_ylabel(ylabel)
        ax.set_xlabel("Time (min)")
        ax.legend(fontsize=8, ncol=2)
        ax.grid(True, alpha=0.3)

    plt.tight_layout()
    out_path = output_dir / f"{dataset_id}_{cell_id}_soh_sessions.png"
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  [{dataset_id}/{cell_id}] Full sessions: {out_path}  ({n} cycles)")


def plot_window_comparison(
    cell_meta: pd.DataFrame,
    seq: pd.DataFrame,
    dataset_id: str,
    cell_id: str,
    output_dir: Path,
) -> None:
    """10분 window 수준 패턴 비교.

    각 SOH 구간의 대표 세션에서 window를 잘라서
    첫 번째 window, 중간 window를 SOH별로 overlay.
    → 개별 window 내에서 SOH별 패턴 차이가 존재하는지 확인.
    """
    ordered = assign_global_cycle_order(cell_meta, dataset_id)
    # SOH 범위를 3구간으로 단순화 (high / mid / low)
    soh_bands = [
        ("high", 0.92, 1.05),
        ("mid",  0.72, 0.88),
        ("low",  0.40, 0.65),
    ]
    band_colors = {"high": "green", "mid": "orange", "low": "red"}

    # 각 구간에서 세션 길이가 충분한 것들 수집 (window 2개 이상 = 20분 이상)
    band_sessions: dict[str, list[pd.DataFrame]] = {b: [] for b, _, _ in soh_bands}
    for _, row in ordered.iterrows():
        s = seq[seq["source_id"] == row["source_id"]].copy()
        if s.empty:
            continue
        duration = s["time_s"].max() - s["time_s"].min()
        if duration < WINDOW_SECONDS * 2:
            continue
        soh = row["soh_ratio"]
        for band, lo, hi in soh_bands:
            if lo <= soh <= hi:
                band_sessions[band].append(s)
                break

    # 각 구간에서 최대 3개 세션만 사용
    for band in band_sessions:
        band_sessions[band] = band_sessions[band][:3]

    has_data = any(len(v) > 0 for v in band_sessions.values())
    if not has_data:
        print(f"  [{dataset_id}/{cell_id}] window 비교용 세션 부족, 스킵")
        return

    window_positions = ["first", "second"]  # 첫 번째 / 두 번째 10분 window
    fig, axes = plt.subplots(
        len(window_positions), 2, figsize=(14, 4 * len(window_positions))
    )
    fig.suptitle(
        f"{dataset_id} / {cell_id}  —  10-min window pattern by SOH\n"
        f"(green=high SOH, orange=mid, red=low)",
        fontsize=12,
    )

    for row_idx, win_pos in enumerate(window_positions):
        for col_idx, (ch, ylabel) in enumerate([("voltage_v", "Voltage (V)"), ("current_a", "Current (A)")]):
            ax = axes[row_idx, col_idx]
            ax.set_title(f"Window: {win_pos}  |  {ylabel}")
            plotted = False
            for band, _, _ in soh_bands:
                color = band_colors[band]
                for s_idx, s in enumerate(band_sessions[band]):
                    t0 = s["time_s"].values[0]
                    win_start = t0 + (0 if win_pos == "first" else WINDOW_SECONDS)
                    win_end = win_start + WINDOW_SECONDS
                    w = s[(s["time_s"] >= win_start) & (s["time_s"] < win_end)]
                    if len(w) < 3:
                        continue
                    t_norm = (w["time_s"].values - w["time_s"].values[0]) / 60.0
                    ax.plot(t_norm, w[ch].values, color=color, linewidth=1.2,
                            alpha=0.7, label=band if s_idx == 0 else None)
                    plotted = True
            if plotted:
                ax.legend(fontsize=8)
            ax.set_xlabel("Time within window (min)")
            ax.set_ylabel(ylabel)
            ax.grid(True, alpha=0.3)

    plt.tight_layout()
    out_path = output_dir / f"{dataset_id}_{cell_id}_window_comparison.png"
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  [{dataset_id}/{cell_id}] Window comparison: {out_path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", type=str, default=str(ARTIFACT_DIR))
    parser.add_argument("--dataset", type=str, default=None)
    parser.add_argument("--cell", type=str, default=None)
    parser.add_argument("--output-dir", type=str, default=str(DEFAULT_OUTPUT))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    artifact_dir = Path(args.artifact_dir)
    output_dir = Path(args.output_dir)

    print(f"artifact_dir: {artifact_dir}")
    meta, seq = load_data(artifact_dir)

    if args.dataset:
        meta = meta[meta["dataset_id"] == args.dataset]
    if args.cell:
        meta = meta[meta["cell_id"] == args.cell]

    for (dataset_id, cell_id), cell_meta in meta.groupby(["dataset_id", "cell_id"]):
        soh_range = cell_meta["soh_ratio"].max() - cell_meta["soh_ratio"].min()
        print(f"\n{dataset_id}/{cell_id}: {len(cell_meta)} cycles, "
              f"SOH {cell_meta['soh_ratio'].min():.3f}~{cell_meta['soh_ratio'].max():.3f}")

        plot_soh_trajectory(cell_meta, dataset_id, cell_id, output_dir)

        if soh_range >= 0.15:
            plot_full_sessions(cell_meta, seq, dataset_id, cell_id, output_dir)
            plot_window_comparison(cell_meta, seq, dataset_id, cell_id, output_dir)
        else:
            print(f"  SOH range {soh_range:.3f} < 0.15, session/window plots skipped")

    print(f"\nDone. Output: {output_dir}")


if __name__ == "__main__":
    main()
