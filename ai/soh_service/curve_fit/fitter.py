"""NASA/CALCE canonical artifact → 표준 SOH 열화 곡선 피팅."""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq
from scipy.optimize import curve_fit

from soh_service.curve_fit.models import StandardCurveArtifact, StandardCurveParams


NOMINAL_CELL_VOLTAGE_V = 3.7  # 리튬이온 셀 공칭 전압


def fit_standard_curve_from_artifact(
    artifact_dir: str | Path,
    dataset_scope: str = "nasa_calce",
    only_train_split: bool = True,
) -> StandardCurveArtifact:
    """Canonical parquet artifact에서 (정규화된 누적 에너지, SOH) 쌍을 추출해 곡선을 피팅.

    Args:
        artifact_dir: canonical_nasa_calce_v1 디렉토리 경로
        dataset_scope: "nasa_only" | "calce_only" | "nasa_calce"
        only_train_split: True면 train split에 속한 cell만 사용
    """
    artifact_dir = Path(artifact_dir)
    metadata_rows = pq.read_table(artifact_dir / "metadata.parquet").to_pylist()
    sequence_rows = pq.read_table(artifact_dir / "sequences.parquet").to_pylist()
    split_rows = pq.read_table(artifact_dir / "splits.parquet").to_pylist()

    allowed_dataset_ids = _resolve_dataset_scope(dataset_scope)
    split_by_source_id = {row["source_id"]: row["split"] for row in split_rows}

    selected_metadata = [
        row
        for row in metadata_rows
        if row["dataset_id"] in allowed_dataset_ids
        and (not only_train_split or split_by_source_id.get(row["source_id"]) == "train")
        and row["soh_ratio"] is not None
        and row["baseline_capacity_ah"] is not None
        and row["sequence_phase"] == "discharge"
    ]

    sequences_by_source_id: dict[str, list[dict]] = defaultdict(list)
    for row in sequence_rows:
        sequences_by_source_id[row["source_id"]].append(row)

    # cell별로 사이클 순서대로 정렬
    metadata_by_cell: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for meta_row in selected_metadata:
        key = (meta_row["dataset_id"], meta_row["cell_id"])
        metadata_by_cell[key].append(meta_row)

    fit_x_values: list[float] = []
    fit_soh_values: list[float] = []

    for (dataset_id, cell_id), cell_metadata_rows in metadata_by_cell.items():
        cell_metadata_rows.sort(
            key=lambda row: row["cycle_index"] if row["cycle_index"] is not None else 0
        )
        baseline_capacity_ah = float(cell_metadata_rows[0]["baseline_capacity_ah"])
        if baseline_capacity_ah <= 0.0:
            continue
        nominal_cell_energy_wh = baseline_capacity_ah * NOMINAL_CELL_VOLTAGE_V

        cumulative_energy_wh = 0.0
        for meta_row in cell_metadata_rows:
            source_id = meta_row["source_id"]
            cycle_rows = sequences_by_source_id.get(source_id, [])
            if not cycle_rows:
                continue
            cycle_energy_wh = _integrate_cycle_energy_wh(cycle_rows)
            if cycle_energy_wh <= 0.0:
                continue

            cumulative_energy_wh += cycle_energy_wh
            normalized_x = cumulative_energy_wh / nominal_cell_energy_wh
            soh_ratio = float(meta_row["soh_ratio"])
            if 0.0 < soh_ratio <= 1.2 and normalized_x >= 0.0:
                fit_x_values.append(normalized_x)
                fit_soh_values.append(soh_ratio)

    if len(fit_x_values) < 10:
        raise ValueError(
            f"곡선 피팅에 필요한 최소 포인트 수 미달: {len(fit_x_values)}개. "
            "artifact_dir 및 dataset_scope를 확인하세요."
        )

    x_array = np.array(fit_x_values, dtype=float)
    y_array = np.array(fit_soh_values, dtype=float)

    params = _fit_exponential_decay(x_array, y_array)
    predicted = params.a * np.exp(-params.b * x_array) + params.c
    residuals = y_array - predicted
    rmse = float(np.sqrt(np.mean(residuals**2)))
    mae = float(np.mean(np.abs(residuals)))

    x_median = float(np.median(x_array))
    x_p95 = float(np.percentile(x_array, 95))

    # 피팅 범위 내 평균 기울기(절댓값) — 유저 기울기 정규화 기준
    mean_slope_abs = float(np.mean(np.abs(params.a * params.b * np.exp(-params.b * x_array))))

    unique_cells = len(metadata_by_cell)
    return StandardCurveArtifact(
        params=params,
        fit_point_count=len(fit_x_values),
        cell_count=unique_cells,
        dataset_scope=dataset_scope,
        x_median=x_median,
        x_p95=x_p95,
        mean_slope_over_fit_range=mean_slope_abs,
        rmse=rmse,
        mae=mae,
    )


def _resolve_dataset_scope(dataset_scope: str) -> set[str]:
    if dataset_scope == "nasa_only":
        return {"nasa"}
    if dataset_scope == "calce_only":
        return {"calce"}
    if dataset_scope == "nasa_calce":
        return {"nasa", "calce"}
    raise ValueError(f"Unsupported dataset_scope: {dataset_scope}")


def _integrate_cycle_energy_wh(cycle_rows: list[dict]) -> float:
    """단일 방전 사이클의 전달 에너지 적분 (Wh). 전류는 방전 시 음수이므로 절댓값."""
    cycle_rows = sorted(cycle_rows, key=lambda row: float(row["time_s"]))
    if len(cycle_rows) < 2:
        return 0.0
    times_s = np.array([float(row["time_s"]) for row in cycle_rows], dtype=float)
    voltages_v = np.array([float(row["voltage_v"]) for row in cycle_rows], dtype=float)
    currents_a = np.array([abs(float(row["current_a"])) for row in cycle_rows], dtype=float)
    power_w = voltages_v * currents_a
    energy_ws = float(np.trapezoid(power_w, times_s))
    return energy_ws / 3600.0


def _fit_exponential_decay(
    x_array: np.ndarray,
    y_array: np.ndarray,
) -> StandardCurveParams:
    """soh(x) = a*exp(-b*x) + c 피팅.

    초기값과 경계를 명시해 수렴 안정성 확보.
    """

    def model(x, a, b, c):
        return a * np.exp(-b * x) + c

    # 초기값: SOH가 대략 1.0에서 시작해 0.7 근처로 떨어진다고 가정
    initial_guess = [0.3, 0.5, 0.7]
    # 경계: a > 0, b > 0, 0 < c < 1.5
    lower_bounds = [0.0, 0.0, 0.0]
    upper_bounds = [2.0, 20.0, 1.5]

    popt, _ = curve_fit(
        model,
        x_array,
        y_array,
        p0=initial_guess,
        bounds=(lower_bounds, upper_bounds),
        maxfev=20000,
    )
    return StandardCurveParams(a=float(popt[0]), b=float(popt[1]), c=float(popt[2]))
