"""
모델 로드 및 SOH 예측
- 서버 시작 시 1회 로드 후 메모리에 유지 (싱글턴 패턴)
"""

import numpy as np
import pandas as pd
import joblib

from soh_service.core.config import (
    MODEL_PATH,
    SCALER_PATH,
    FEATURE_COLS,
    DEFAULT_POWERBANK_CAPACITY_MAH,
    DEFAULT_PHONE_CAPACITY_MAH,
)
from soh_service.core.features import extract_features


class SOHPredictor:
    """학습된 모델을 로드하고 SOH 예측을 수행하는 클래스"""

    def __init__(self):
        self.model = joblib.load(MODEL_PATH)
        self.scaler = joblib.load(SCALER_PATH)

    def predict(
        self,
        cycle_df: pd.DataFrame,
        capacity_ah: float,
        powerbank_capacity_mah: int = DEFAULT_POWERBANK_CAPACITY_MAH,
        phone_capacity_mah: int = DEFAULT_PHONE_CAPACITY_MAH,
    ) -> dict:
        """
        단일 방전 사이클 → SOH 예측 결과 반환

        Returns:
            {
                soh_percentage: float,         # 예측 SOH (%)
                condition: str,                # 상태 라벨
                estimated_full_charges: float, # 완충 가능 횟수
                powerbank_usable_mah: float,   # 실사용 가능 용량
                smartphone_received_mah: float,# 스마트폰 수신 용량
                mean_temperature_c: float,     # 평균 온도
            }
        """
        features = extract_features(
            cycle_df, capacity_ah, powerbank_capacity_mah, phone_capacity_mah
        )

        X = np.array([[features[col] for col in FEATURE_COLS]])
        X_scaled = self.scaler.transform(X)
        soh_pred = float(np.clip(self.model.predict(X_scaled)[0], 0.0, 1.0))

        powerbank_usable_mah = powerbank_capacity_mah * soh_pred * 0.85
        estimated_full_charges = powerbank_usable_mah / phone_capacity_mah

        return {
            "soh_percentage": round(soh_pred * 100, 2),
            "condition": _condition_label(soh_pred),
            "estimated_full_charges": round(estimated_full_charges, 2),
            "powerbank_usable_mah": round(powerbank_usable_mah, 1),
            "smartphone_received_mah": round(features["smartphone_received_mah"], 1),
            "mean_temperature_c": round(features["mean_temperature"], 1),
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


# 서버 기동 시 1회만 로드되는 싱글턴 인스턴스
_predictor: SOHPredictor | None = None


def get_predictor() -> SOHPredictor:
    """FastAPI dependency injection용 getter"""
    global _predictor
    if _predictor is None:
        _predictor = SOHPredictor()
    return _predictor