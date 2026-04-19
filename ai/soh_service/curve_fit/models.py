"""표준 곡선 및 세션 관측값 데이터 모델."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class StandardCurveParams:
    """지수 감쇠 모델 파라미터: soh(x) = a * exp(-b * x) + c

    x 축은 '정규화된 누적 전달 에너지'로, cell별 정격 에너지 대비 누적 비율.
    즉 x=1.0은 셀이 정격 용량을 한 번 완전히 방출한 양과 동일.
    """

    a: float
    b: float
    c: float

    def evaluate(self, x: float) -> float:
        """x 지점에서의 표준 SOH 값."""
        import math

        return self.a * math.exp(-self.b * x) + self.c

    def slope(self, x: float) -> float:
        """x 지점에서의 dSOH/dx (음수)."""
        import math

        return -self.a * self.b * math.exp(-self.b * x)


@dataclass(frozen=True)
class StandardCurveArtifact:
    """표준 곡선 피팅 산출물. JSON으로 저장/로드됨."""

    params: StandardCurveParams
    fit_point_count: int
    cell_count: int
    dataset_scope: str
    x_median: float
    x_p95: float
    mean_slope_over_fit_range: float
    rmse: float
    mae: float

    def to_dict(self) -> dict:
        return {
            "params": {
                "a": self.params.a,
                "b": self.params.b,
                "c": self.params.c,
            },
            "fit_point_count": self.fit_point_count,
            "cell_count": self.cell_count,
            "dataset_scope": self.dataset_scope,
            "x_median": self.x_median,
            "x_p95": self.x_p95,
            "mean_slope_over_fit_range": self.mean_slope_over_fit_range,
            "rmse": self.rmse,
            "mae": self.mae,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "StandardCurveArtifact":
        params_data = data["params"]
        return cls(
            params=StandardCurveParams(
                a=float(params_data["a"]),
                b=float(params_data["b"]),
                c=float(params_data["c"]),
            ),
            fit_point_count=int(data["fit_point_count"]),
            cell_count=int(data["cell_count"]),
            dataset_scope=str(data["dataset_scope"]),
            x_median=float(data["x_median"]),
            x_p95=float(data["x_p95"]),
            mean_slope_over_fit_range=float(data["mean_slope_over_fit_range"]),
            rmse=float(data["rmse"]),
            mae=float(data["mae"]),
        )


@dataclass(frozen=True)
class SessionObservation:
    """한 세션에서 추출된 에너지 전달 관측값 (필터링 전)."""

    duration_s: float
    delivered_wh: float
    start_battery_level_pct: float
    passes_filter: bool
    filter_reject_reason: str | None
    normalized_wh_per_pct: float | None
