"""
LSTM 기반 SOH 예측
- 서버 시작 시 1회 로드 후 메모리에 유지 (싱글턴 패턴)
"""

from __future__ import annotations

from soh_service.core.config import (
    LSTM_CHECKPOINT_DIR,
    CONVERTER_EFFICIENCY,
    DEFAULT_POWERBANK_CAPACITY_MAH,
    DEFAULT_PHONE_CAPACITY_MAH,
    N_MAX_SESSIONS,
)
from soh_service.inference.predictor import (
    LstmSOHPredictor,
    TelemetryPoint,
    load_lstm_predictor,
)


class SOHPredictor:
    """학습된 LSTM 모델을 로드하고 SOH 예측을 수행하는 클래스"""

    def __init__(self) -> None:
        self._predictor: LstmSOHPredictor = load_lstm_predictor(LSTM_CHECKPOINT_DIR)

    def predict_multi(
        self,
        sessions: list[list[TelemetryPoint]],
        powerbank_capacity_mah: int = DEFAULT_POWERBANK_CAPACITY_MAH,
        phone_capacity_mah: int = DEFAULT_PHONE_CAPACITY_MAH,
    ) -> dict:
        """
        여러 세션 텔레메트리 → SOH 예측 결과

        최근 N_MAX_SESSIONS개 세션만 사용한다 (오래된 세션 희석 방지).
        10분 미만 세션은 자동 제외된다.

        Returns:
            predict()와 동일한 구조 + sessions_used (유효 세션 수)
        """
        trimmed = sessions[-N_MAX_SESSIONS:] if len(sessions) > N_MAX_SESSIONS else sessions
        all_telemetry = [t for s in trimmed for t in s]
        soh, sessions_used = self._predictor.predict_multi(trimmed)

        powerbank_usable_mah = powerbank_capacity_mah * soh * CONVERTER_EFFICIENCY
        estimated_full_charges = powerbank_usable_mah / phone_capacity_mah
        mean_temperature_c = _mean_temperature(all_telemetry)

        return {
            "soh_percentage": round(soh * 100, 2),
            "condition": _condition_label(soh),
            "estimated_full_charges": round(estimated_full_charges, 2),
            "powerbank_usable_mah": round(powerbank_usable_mah, 1),
            "mean_temperature_c": (
                round(mean_temperature_c, 1) if mean_temperature_c is not None else None
            ),
            "sessions_used": sessions_used,
        }

    def predict(
        self,
        telemetry: list[TelemetryPoint],
        powerbank_capacity_mah: int = DEFAULT_POWERBANK_CAPACITY_MAH,
        phone_capacity_mah: int = DEFAULT_PHONE_CAPACITY_MAH,
    ) -> dict:
        """
        충전 텔레메트리 시퀀스 → SOH 예측 결과 반환

        Returns:
            {
                soh_percentage: float,         # 예측 SOH (%)
                condition: str,                # 상태 라벨
                estimated_full_charges: float, # 완충 가능 횟수 추정
                powerbank_usable_mah: float,   # 실사용 가능 용량
                mean_temperature_c: float | None,
            }
        """
        soh = self._predictor.predict(telemetry)

        powerbank_usable_mah = powerbank_capacity_mah * soh * CONVERTER_EFFICIENCY
        estimated_full_charges = powerbank_usable_mah / phone_capacity_mah
        mean_temperature_c = _mean_temperature(telemetry)

        return {
            "soh_percentage": round(soh * 100, 2),
            "condition": _condition_label(soh),
            "estimated_full_charges": round(estimated_full_charges, 2),
            "powerbank_usable_mah": round(powerbank_usable_mah, 1),
            "mean_temperature_c": (
                round(mean_temperature_c, 1) if mean_temperature_c is not None else None
            ),
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


def _mean_temperature(telemetry: list[TelemetryPoint]) -> float | None:
    temps = [p.temperature_c for p in telemetry if p.temperature_c is not None]
    if not temps:
        return None
    return sum(temps) / len(temps)


# 서버 기동 시 1회만 로드되는 싱글턴 인스턴스
_predictor: SOHPredictor | None = None


def get_predictor() -> SOHPredictor:
    """FastAPI dependency injection용 getter"""
    global _predictor
    if _predictor is None:
        _predictor = SOHPredictor()
    return _predictor
