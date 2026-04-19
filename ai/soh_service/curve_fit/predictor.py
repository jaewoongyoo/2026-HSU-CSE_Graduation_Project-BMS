"""표준 곡선 + 유저 세션 → 개인화 SOH 예측."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from soh_service.curve_fit.models import (
    SessionObservation,
    StandardCurveArtifact,
)


# ── 필터 상수 ─────────────────────────────────────────────────────────────────

MIN_SESSION_DURATION_S = 600.0   # 10분 미만 세션 제외
MIN_SESSION_DELIVERED_WH = 2.0   # 너무 적은 에너지 전달 세션 제외
MIN_BATTERY_LEVEL_GAP_PCT = 15.0  # 스마트폰 배터리가 이 값 이상 남아있으면 제외 (충전 여지가 적음)
MAX_START_BATTERY_LEVEL_PCT = 85.0  # 시작 잔량이 너무 높으면 제외

MIN_USABLE_SESSIONS_FOR_PERSONALIZATION = 3
DEFAULT_NOMINAL_POWERBANK_VOLTAGE_V = 3.7


@dataclass(frozen=True)
class TelemetrySample:
    """단일 시점 충전 텔레메트리 (스마트폰 수신 측)."""

    time_s: float
    voltage_v: float
    current_a: float
    temperature_c: float | None = None


@dataclass(frozen=True)
class UserSessionInput:
    """API 요청에서 받는 한 세션의 원시 입력."""

    samples: list[TelemetrySample]
    start_battery_level_pct: float


@dataclass(frozen=True)
class PersonalizedPrediction:
    """개인화 예측 결과."""

    soh_ratio: float
    standard_soh_ratio: float
    degradation_rate_ratio: float  # user_slope / standard_slope
    estimated_cumulative_x: float  # 표준 곡선 상 현재 위치 (정규화 누적 에너지)
    sessions_used: int
    sessions_total: int
    confidence: str  # "high" | "medium" | "low" | "fallback"


def load_curve_fit_predictor(curve_json_path: str | Path) -> "CurveFitPredictor":
    """표준 곡선 JSON을 로드해 예측기 생성."""
    curve_json_path = Path(curve_json_path)
    if not curve_json_path.is_file():
        raise FileNotFoundError(f"표준 곡선 파일을 찾을 수 없습니다: {curve_json_path}")

    data = json.loads(curve_json_path.read_text(encoding="utf-8"))
    artifact = StandardCurveArtifact.from_dict(data)
    return CurveFitPredictor(artifact=artifact)


class CurveFitPredictor:
    """표준 곡선과 유저 세션 데이터로 개인화 SOH를 예측한다."""

    def __init__(
        self,
        artifact: StandardCurveArtifact,
        powerbank_nominal_voltage_v: float = DEFAULT_NOMINAL_POWERBANK_VOLTAGE_V,
    ) -> None:
        self._artifact = artifact
        self._powerbank_nominal_voltage_v = powerbank_nominal_voltage_v

    @property
    def artifact(self) -> StandardCurveArtifact:
        return self._artifact

    def predict_single_session(
        self,
        telemetry: list[TelemetrySample],
    ) -> PersonalizedPrediction:
        """단일 세션 입력 → 표준 곡선 fallback 예측.

        유저 기울기를 계산할 수 없으므로 표준 곡선의 현재 진척 지점(예: 중앙값)을 사용.
        """
        if len(telemetry) < 2:
            raise ValueError("텔레메트리 포인트가 최소 2개 이상이어야 합니다.")

        # fallback: 표준 곡선의 중앙값 지점에서 SOH를 반환
        x_fallback = self._artifact.x_median
        standard_soh = self._artifact.params.evaluate(x_fallback)
        return PersonalizedPrediction(
            soh_ratio=_clip(standard_soh),
            standard_soh_ratio=_clip(standard_soh),
            degradation_rate_ratio=1.0,
            estimated_cumulative_x=x_fallback,
            sessions_used=0,
            sessions_total=1,
            confidence="fallback",
        )

    def predict_personalized(
        self,
        sessions: list[UserSessionInput],
        powerbank_capacity_mah: int,
    ) -> PersonalizedPrediction:
        """다중 세션 → 필터링 → 유저 기울기 → 개인화 SOH."""
        if not sessions:
            raise ValueError("sessions는 최소 1개 이상이어야 합니다.")

        observations = [
            self._observe_session(session, powerbank_capacity_mah)
            for session in sessions
        ]
        usable = [obs for obs in observations if obs.passes_filter]
        sessions_total = len(observations)

        if len(usable) < MIN_USABLE_SESSIONS_FOR_PERSONALIZATION:
            # 충분한 유효 세션 없음 → 표준 곡선 중앙값 fallback
            x_fallback = self._artifact.x_median
            standard_soh = self._artifact.params.evaluate(x_fallback)
            confidence = "fallback" if len(usable) == 0 else "low"
            return PersonalizedPrediction(
                soh_ratio=_clip(standard_soh),
                standard_soh_ratio=_clip(standard_soh),
                degradation_rate_ratio=1.0,
                estimated_cumulative_x=x_fallback,
                sessions_used=len(usable),
                sessions_total=sessions_total,
                confidence=confidence,
            )

        # 유저 기울기 계산: session_index 대비 normalized_wh_per_pct 선형 회귀
        session_indices = np.arange(len(usable), dtype=float)
        normalized_values = np.array(
            [obs.normalized_wh_per_pct for obs in usable], dtype=float
        )
        slope_per_session, _intercept = np.polyfit(session_indices, normalized_values, 1)
        user_slope_abs = abs(float(slope_per_session))

        # 표준 곡선 대비 노화 속도 비율
        standard_slope_abs = max(self._artifact.mean_slope_over_fit_range, 1e-9)

        # 단위 정합을 위한 스케일링: 유저 기울기는 (Wh/pct)/session 단위,
        # 표준 기울기는 dSOH/dx 단위 → 직접 비교 불가하므로
        # 유저 기울기의 "상대적 감소율"을 대신 사용한다.
        # 첫 세션 대비 감소율 = slope_per_session / (mean normalized value)
        mean_normalized_value = float(np.mean(normalized_values))
        if mean_normalized_value <= 1e-9:
            relative_user_decay_rate = 0.0
        else:
            # slope가 음수이면 감소 중. 상대 감소율을 양수로 변환.
            relative_user_decay_rate = -float(slope_per_session) / mean_normalized_value

        # 표준 곡선의 "상대적 감소율": 중앙값 지점에서 dSOH/dx / SOH
        x_ref = self._artifact.x_median
        standard_soh_at_ref = self._artifact.params.evaluate(x_ref)
        if standard_soh_at_ref <= 1e-6:
            relative_standard_decay_rate = 1.0
        else:
            relative_standard_decay_rate = (
                abs(self._artifact.params.slope(x_ref)) / standard_soh_at_ref
            )

        if relative_standard_decay_rate <= 1e-9:
            degradation_rate_ratio = 1.0
        else:
            degradation_rate_ratio = (
                relative_user_decay_rate / relative_standard_decay_rate
            )
        # 과도한 값 방지 클립
        degradation_rate_ratio = max(0.1, min(degradation_rate_ratio, 10.0))

        # 개인화된 x 위치: 표준 중앙값에 노화 속도 비율을 곱해 진척 위치 조정
        estimated_x = x_ref * degradation_rate_ratio
        estimated_x = max(0.0, min(estimated_x, self._artifact.x_p95))
        personalized_soh = self._artifact.params.evaluate(estimated_x)

        confidence = _grade_confidence(len(usable))

        return PersonalizedPrediction(
            soh_ratio=_clip(personalized_soh),
            standard_soh_ratio=_clip(self._artifact.params.evaluate(x_ref)),
            degradation_rate_ratio=float(degradation_rate_ratio),
            estimated_cumulative_x=float(estimated_x),
            sessions_used=len(usable),
            sessions_total=sessions_total,
            confidence=confidence,
        )

    def _observe_session(
        self,
        session: UserSessionInput,
        powerbank_capacity_mah: int,
    ) -> SessionObservation:
        samples = sorted(session.samples, key=lambda s: s.time_s)
        if len(samples) < 2:
            return SessionObservation(
                duration_s=0.0,
                delivered_wh=0.0,
                start_battery_level_pct=session.start_battery_level_pct,
                passes_filter=False,
                filter_reject_reason="too_few_samples",
                normalized_wh_per_pct=None,
            )

        duration_s = samples[-1].time_s - samples[0].time_s
        delivered_wh = _integrate_delivered_wh(samples)

        # 필터링
        if duration_s < MIN_SESSION_DURATION_S:
            return SessionObservation(
                duration_s=duration_s,
                delivered_wh=delivered_wh,
                start_battery_level_pct=session.start_battery_level_pct,
                passes_filter=False,
                filter_reject_reason="duration_too_short",
                normalized_wh_per_pct=None,
            )
        if delivered_wh < MIN_SESSION_DELIVERED_WH:
            return SessionObservation(
                duration_s=duration_s,
                delivered_wh=delivered_wh,
                start_battery_level_pct=session.start_battery_level_pct,
                passes_filter=False,
                filter_reject_reason="delivered_wh_too_low",
                normalized_wh_per_pct=None,
            )
        if session.start_battery_level_pct > MAX_START_BATTERY_LEVEL_PCT:
            return SessionObservation(
                duration_s=duration_s,
                delivered_wh=delivered_wh,
                start_battery_level_pct=session.start_battery_level_pct,
                passes_filter=False,
                filter_reject_reason="start_level_too_high",
                normalized_wh_per_pct=None,
            )

        # 충전 시작 배터리 용량 고려: 채울 여지가 큰 세션일수록 전달량이 커짐
        # 따라서 "채울 여지 1%p 당 전달 에너지"로 정규화
        fillable_pct = 100.0 - session.start_battery_level_pct
        if fillable_pct < MIN_BATTERY_LEVEL_GAP_PCT:
            return SessionObservation(
                duration_s=duration_s,
                delivered_wh=delivered_wh,
                start_battery_level_pct=session.start_battery_level_pct,
                passes_filter=False,
                filter_reject_reason="fillable_gap_too_small",
                normalized_wh_per_pct=None,
            )

        normalized_wh_per_pct = delivered_wh / fillable_pct
        return SessionObservation(
            duration_s=duration_s,
            delivered_wh=delivered_wh,
            start_battery_level_pct=session.start_battery_level_pct,
            passes_filter=True,
            filter_reject_reason=None,
            normalized_wh_per_pct=normalized_wh_per_pct,
        )


def _integrate_delivered_wh(samples: list[TelemetrySample]) -> float:
    """사다리꼴 적분으로 세션 전달 에너지(Wh) 계산."""
    if len(samples) < 2:
        return 0.0
    times_s = np.array([s.time_s for s in samples], dtype=float)
    voltages_v = np.array([s.voltage_v for s in samples], dtype=float)
    currents_a = np.array([abs(s.current_a) for s in samples], dtype=float)
    power_w = voltages_v * currents_a
    energy_ws = float(np.trapezoid(power_w, times_s))
    return energy_ws / 3600.0


def _clip(value: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(float(value), high))


def _grade_confidence(usable_count: int) -> str:
    if usable_count >= 10:
        return "high"
    if usable_count >= 5:
        return "medium"
    return "low"
