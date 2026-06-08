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

MIN_SESSION_DURATION_S = 900.0   # 15분: 일반 충전 최소 세션 길이
MIN_FAST_CHARGE_DURATION_S = 480.0  # 8분: 고속 충전 최소 세션 길이
FAST_CHARGE_THRESHOLD_A = 2.0    # 2A 이상 중앙값 전류 → 고속 충전 세션으로 분류
MIN_SESSION_DELIVERED_WH = 1.0   # 너무 적은 에너지 전달 세션 제외
MIN_BATTERY_LEVEL_GAP_PCT = 15.0  # 스마트폰 배터리가 이 값 이상 남아있으면 제외 (충전 여지가 적음)
MAX_START_BATTERY_LEVEL_PCT = 85.0  # 시작 잔량이 너무 높으면 제외

MIN_USABLE_SESSIONS_FOR_PERSONALIZATION = 3
DEFAULT_NOMINAL_POWERBANK_VOLTAGE_V = 3.7

# 기울기 유의성 판정 임계값.
# t-유사 통계량 = |slope| * sqrt(n) / std(residuals) 가 이 값 이상이어야 유의한 추세로 인정한다.
# 2.0 ≈ 표준 양측 5% 유의수준(대략). 노이즈성 기울기로 인한 잘못된 개인화를 막기 위함.
MIN_SLOPE_SIGNIFICANCE_T = 2.0


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
    # backend가 raw 포인트 전체로 적분해 전달한 고해상도 Wh.
    # None이면 10분 집계점(samples)으로 적분(하위호환). 값이 있으면 이 값을 우선 사용해
    # 집계 해상도 손실로 인한 기울기 노이즈를 줄인다.
    delivered_wh_override: float | None = None


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
        slope_per_session, intercept = np.polyfit(session_indices, normalized_values, 1)
        slope_per_session = float(slope_per_session)

        # ── 기울기 유의성 검사 ────────────────────────────────────────────────
        # 잔차의 표준편차 대비 기울기 크기로 유의성 판단.
        # t_stat = |slope| * sqrt(n) / std(residuals)
        # 값이 임계 미만이면 노이즈로 간주하고 표준 fallback으로 돌린다.
        predicted = slope_per_session * session_indices + float(intercept)
        residuals = normalized_values - predicted
        residual_std = float(np.std(residuals, ddof=1)) if len(residuals) > 1 else 0.0
        if residual_std <= 1e-12:
            t_stat = float("inf") if abs(slope_per_session) > 0 else 0.0
        else:
            t_stat = abs(slope_per_session) * np.sqrt(len(usable)) / residual_std

        x_ref = self._artifact.x_median
        standard_soh_at_ref = self._artifact.params.evaluate(x_ref)

        # 기울기가 유의하지 않거나, slope > 0 (노화 역행 = 의미없는 신호)인 경우 → fallback.
        # 다만 유저에게는 "노화 감지 안 됨 = 표준 수준"으로 해석해 standard_soh_ratio를 그대로 반환.
        if t_stat < MIN_SLOPE_SIGNIFICANCE_T or slope_per_session >= 0.0:
            return PersonalizedPrediction(
                soh_ratio=_clip(standard_soh_at_ref),
                standard_soh_ratio=_clip(standard_soh_at_ref),
                degradation_rate_ratio=1.0,
                estimated_cumulative_x=x_ref,
                sessions_used=len(usable),
                sessions_total=sessions_total,
                # 유효 세션이 많아도 신호가 노이즈면 한 단계 낮춘 등급을 부여
                confidence=_grade_confidence_with_signal(len(usable), has_signal=False),
            )

        # ── 유의한 노화 신호가 있을 때 개인화 계산 ──────────────────────────
        # 유저 기울기는 (Wh/pct)/session 단위, 표준 기울기는 dSOH/dx 단위 → 직접 비교 불가.
        # 유저 기울기의 "상대적 감소율"과 표준 곡선의 "상대적 감소율"을 각각 계산해 비교.
        mean_normalized_value = float(np.mean(normalized_values))
        if mean_normalized_value <= 1e-9:
            # 에너지 측정값이 0 근처면 의미 있는 비교 불가 → fallback
            return PersonalizedPrediction(
                soh_ratio=_clip(standard_soh_at_ref),
                standard_soh_ratio=_clip(standard_soh_at_ref),
                degradation_rate_ratio=1.0,
                estimated_cumulative_x=x_ref,
                sessions_used=len(usable),
                sessions_total=sessions_total,
                confidence=_grade_confidence_with_signal(len(usable), has_signal=False),
            )

        # slope가 음수이므로 -slope는 양수 감소율
        relative_user_decay_rate = -slope_per_session / mean_normalized_value

        # 표준 곡선의 상대 감소율: 중앙값 지점에서 |dSOH/dx| / SOH
        if standard_soh_at_ref <= 1e-6:
            relative_standard_decay_rate = 1.0
        else:
            relative_standard_decay_rate = (
                abs(self._artifact.params.slope(x_ref)) / standard_soh_at_ref
            )

        if relative_standard_decay_rate <= 1e-9:
            degradation_rate_ratio = 1.0
        else:
            # 유저 기울기는 (Wh/pct)/session 단위, 표준은 dSOH/dx 단위.
            # 단위가 다르므로 직접 비율은 의미가 없고, log scale로 완화해 방향성만 보존한다.
            # rate = exp( log(raw_ratio) / 2 ) = sqrt(raw_ratio)
            # 결과적으로 raw=4.0→rate=2.0, raw=9.0→rate=3.0, raw=0.25→rate=0.5 처럼 극단값이 압축된다.
            raw_ratio = relative_user_decay_rate / relative_standard_decay_rate
            degradation_rate_ratio = float(np.sqrt(raw_ratio))
        # 최종 클립. log 완화 이후에도 이론상 범위 초과 가능성이 있어 안전망 유지.
        degradation_rate_ratio = max(0.3, min(float(degradation_rate_ratio), 3.0))

        # 개인화된 x 위치: 표준 중앙값에 노화 속도 비율을 곱해 진척 위치 조정
        estimated_x = x_ref * degradation_rate_ratio
        estimated_x = max(0.0, min(estimated_x, self._artifact.x_p95))
        personalized_soh = self._artifact.params.evaluate(estimated_x)

        return PersonalizedPrediction(
            soh_ratio=_clip(personalized_soh),
            standard_soh_ratio=_clip(standard_soh_at_ref),
            degradation_rate_ratio=degradation_rate_ratio,
            estimated_cumulative_x=float(estimated_x),
            sessions_used=len(usable),
            sessions_total=sessions_total,
            confidence=_grade_confidence_with_signal(len(usable), has_signal=True),
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
        delivered_wh = (
            session.delivered_wh_override
            if session.delivered_wh_override is not None
            else _integrate_delivered_wh(samples)
        )

        is_fast = _is_fast_charging_samples(samples)
        min_duration_s = MIN_FAST_CHARGE_DURATION_S if is_fast else MIN_SESSION_DURATION_S

        # 필터링
        if duration_s < min_duration_s:
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


def _is_fast_charging_samples(samples: list[TelemetrySample]) -> bool:
    """Median |current_a| ≥ FAST_CHARGE_THRESHOLD_A → fast charging session."""
    currents = [abs(s.current_a) for s in samples]
    if not currents:
        return False
    median_a = sorted(currents)[len(currents) // 2]
    return median_a >= FAST_CHARGE_THRESHOLD_A


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


def _grade_confidence_with_signal(usable_count: int, has_signal: bool) -> str:
    """세션 수와 기울기 유의성을 함께 반영한 신뢰도 등급.

    신호가 없으면 한 단계 낮춘다 (high→medium, medium→low, low→fallback).
    이것으로 '세션은 많지만 기울기가 노이즈'인 경우를 구분한다.
    """
    base = _grade_confidence(usable_count)
    if has_signal:
        return base
    downgrade = {"high": "medium", "medium": "low", "low": "fallback"}
    return downgrade.get(base, "fallback")
