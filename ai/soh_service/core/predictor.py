"""
곡선 피팅 기반 SOH 예측 서비스 계층
- 서버 시작 시 표준 곡선 1회 로드 후 메모리에 유지 (싱글턴 패턴)
"""

from __future__ import annotations

from soh_service.core.config import (
    CONVERTER_EFFICIENCY,
    DEFAULT_PHONE_CAPACITY_MAH,
    DEFAULT_POWERBANK_CAPACITY_MAH,
    N_MAX_SESSIONS,
    STANDARD_CURVE_JSON_PATH,
)
from soh_service.curve_fit.predictor import (
    CurveFitPredictor,
    PersonalizedPrediction,
    TelemetrySample,
    UserSessionInput,
    load_curve_fit_predictor,
)


class SOHPredictor:
    """표준 곡선을 로드하고 SOH 예측을 수행하는 서비스 클래스."""

    def __init__(self) -> None:
        self._predictor: CurveFitPredictor = load_curve_fit_predictor(
            STANDARD_CURVE_JSON_PATH
        )

    def predict(
        self,
        telemetry: list[TelemetrySample],
        powerbank_capacity_mah: int = DEFAULT_POWERBANK_CAPACITY_MAH,
        phone_capacity_mah: int = DEFAULT_PHONE_CAPACITY_MAH,
    ) -> dict:
        """단일 세션 입력 → 표준 곡선 fallback 결과."""
        prediction = self._predictor.predict_single_session(telemetry)
        return self._build_response(
            prediction=prediction,
            telemetry_for_mean_temp=telemetry,
            powerbank_capacity_mah=powerbank_capacity_mah,
            phone_capacity_mah=phone_capacity_mah,
        )

    def predict_multi(
        self,
        sessions: list[UserSessionInput],
        powerbank_capacity_mah: int = DEFAULT_POWERBANK_CAPACITY_MAH,
        phone_capacity_mah: int = DEFAULT_PHONE_CAPACITY_MAH,
    ) -> dict:
        """다중 세션 입력 → 개인화 SOH 결과.

        최근 N_MAX_SESSIONS개만 사용 (오래된 세션 희석 방지).
        """
        trimmed = sessions[-N_MAX_SESSIONS:] if len(sessions) > N_MAX_SESSIONS else sessions
        prediction = self._predictor.predict_personalized(
            trimmed,
            powerbank_capacity_mah=powerbank_capacity_mah,
        )
        all_telemetry = [sample for session in trimmed for sample in session.samples]
        return self._build_response(
            prediction=prediction,
            telemetry_for_mean_temp=all_telemetry,
            powerbank_capacity_mah=powerbank_capacity_mah,
            phone_capacity_mah=phone_capacity_mah,
        )

    def _build_response(
        self,
        prediction: PersonalizedPrediction,
        telemetry_for_mean_temp: list[TelemetrySample],
        powerbank_capacity_mah: int,
        phone_capacity_mah: int,
    ) -> dict:
        soh = prediction.soh_ratio
        powerbank_usable_mah = powerbank_capacity_mah * soh * CONVERTER_EFFICIENCY
        estimated_full_charges = powerbank_usable_mah / phone_capacity_mah
        mean_temperature_c = _mean_temperature(telemetry_for_mean_temp)

        return {
            "soh_percentage": round(soh * 100, 2),
            "condition": _condition_label(soh),
            "estimated_full_charges": round(estimated_full_charges, 2),
            "powerbank_usable_mah": round(powerbank_usable_mah, 1),
            "mean_temperature_c": (
                round(mean_temperature_c, 1) if mean_temperature_c is not None else None
            ),
            "standard_soh_percentage": round(prediction.standard_soh_ratio * 100, 2),
            "degradation_rate_ratio": round(prediction.degradation_rate_ratio, 3),
            "sessions_used": prediction.sessions_used,
            "sessions_total": prediction.sessions_total,
            "confidence": prediction.confidence,
        }


def _condition_label(soh: float) -> str:
    if soh >= 0.90:
        return "우수"
    elif soh >= 0.80:
        return "양호"
    elif soh >= 0.70:
        return "주의"
    else:
        return "교체 권장"


def _mean_temperature(telemetry: list[TelemetrySample]) -> float | None:
    temps = [p.temperature_c for p in telemetry if p.temperature_c is not None]
    if not temps:
        return None
    return sum(temps) / len(temps)


# 서버 기동 시 1회만 로드되는 싱글턴 인스턴스
_predictor: SOHPredictor | None = None


def get_predictor() -> SOHPredictor:
    """FastAPI dependency injection용 getter."""
    global _predictor
    if _predictor is None:
        _predictor = SOHPredictor()
    return _predictor
