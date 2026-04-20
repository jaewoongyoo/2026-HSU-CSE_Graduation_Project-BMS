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

    # canonical artifact의 sequence는 안드로이드 소비자(=스마트폰) 관측 공간과 맞추기 위해
    # **charge 구간**을 시계열로 사용한다 (보조배터리 입장에서는 방전이지만
    # 스마트폰 입장에선 충전). 라벨(SOH)은 별도 방전 사이클에서 계산된
    # soh_ratio에 이미 반영되어 있으므로 여기에서는 다시 선별하지 않는다.
    #
    # 추가 필터:
    #  - baseline_capacity_ah >= 1.0: 연구용으로 파손/열화된 cell(B0041 = 0.056Ah 등) 배제.
    #    실제 보조배터리는 1Ah 이상의 셀을 사용하므로 이 기준으로 고장 cell을 제외.
    #  - 0.6 <= soh_ratio <= 1.1: 보조배터리 실사용 범위(SOH 60%~110%).
    #    NASA는 cell을 SOH 3%까지 파괴적으로 사이클링한 데이터가 포함되어 있고,
    #    baseline 산정 오차로 soh_ratio > 1.1이 되는 초기 사이클도 있다. 이들은 앱 시나리오와
    #    맞지 않아 표준 곡선을 왜곡하므로 제외한다.
    selected_metadata = [
        row
        for row in metadata_rows
        if row["dataset_id"] in allowed_dataset_ids
        and (not only_train_split or split_by_source_id.get(row["source_id"]) == "train")
        and row["soh_ratio"] is not None
        and row["baseline_capacity_ah"] is not None
        and row["sequence_phase"] == "charge"
        and float(row["baseline_capacity_ah"]) >= 1.0
        and 0.6 <= float(row["soh_ratio"]) <= 1.1
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
            # 상위 필터에서 이미 0.6 <= soh_ratio <= 1.1을 보장하지만 안전을 위해 한 번 더 확인.
            if 0.6 <= soh_ratio <= 1.1 and normalized_x >= 0.0:
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
    """단일 충전 사이클의 전달 에너지 적분 (Wh).

    NASA 'charge' 시퀀스 앞부분에는 셀 특성 측정용 방전 펄스(전류 음수)가 섞여 있다.
    순수 충전 구간만 반영하기 위해 **양수 전류 구간만 적분**한다.
    양수 구간을 선별한 뒤 원래의 time_s 간격을 유지해 trapezoid로 적분한다.
    (음수 샘플의 전력 기여분은 0으로 처리)
    """
    cycle_rows = sorted(cycle_rows, key=lambda row: float(row["time_s"]))
    if len(cycle_rows) < 2:
        return 0.0
    times_s = np.array([float(row["time_s"]) for row in cycle_rows], dtype=float)
    voltages_v = np.array([float(row["voltage_v"]) for row in cycle_rows], dtype=float)
    currents_a = np.array([float(row["current_a"]) for row in cycle_rows], dtype=float)
    # 양수 전류(실제 충전)만 남기고 나머지는 0으로 마스킹
    charge_currents_a = np.where(currents_a > 0.0, currents_a, 0.0)
    power_w = voltages_v * charge_currents_a
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
