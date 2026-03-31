"""
SOH 예측 API 엔드포인트
POST /predict  : 방전 사이클 데이터 → SOH 예측
GET  /health   : 모델 로드 상태 확인
"""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
import pandas as pd

from soh_service.core.predictor import SOHPredictor, get_predictor

router = APIRouter(prefix="/soh", tags=["SOH Prediction"])


# ── 요청/응답 스키마 ───────────────────────────────────────────────────────────
class CycleRecord(BaseModel):
    """방전 사이클 단일 시점 측정값 (Android BatteryManager 수집 데이터 구조)"""
    voltage_mv: float = Field(..., description="전압 (mV)")
    current_ma: float = Field(..., description="전류 (mA), 방전 시 음수")
    temperature_c: float = Field(..., description="온도 (°C)")
    elapsed_ms: float = Field(..., description="사이클 시작 후 경과 시간 (ms)")


class PredictRequest(BaseModel):
    cycle_records: list[CycleRecord] = Field(
        ..., min_length=10, description="방전 사이클 시계열 데이터"
    )
    capacity_ah: float = Field(..., gt=0, description="이번 사이클 측정 방전 용량 (Ah)")
    powerbank_capacity_mah: int = Field(
        default=10000, gt=0, description="보조배터리 정격 용량 (mAh)"
    )
    phone_capacity_mah: int = Field(
        default=4000, gt=0, description="스마트폰 배터리 용량 (mAh)"
    )


class PredictResponse(BaseModel):
    soh_percentage: float
    condition: str
    estimated_full_charges: float
    powerbank_usable_mah: float
    smartphone_received_mah: float
    mean_temperature_c: float


# ── 엔드포인트 ─────────────────────────────────────────────────────────────────
@router.post("/predict", response_model=PredictResponse)
def predict_soh(
    request: PredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """
    Android 앱에서 수집한 방전 사이클 데이터를 받아 SOH를 예측합니다.

    - **cycle_records**: BatteryManager API로 수집한 시계열 데이터
    - **capacity_ah**: 해당 충전 세션에서 측정된 총 방전 용량
    - **powerbank_capacity_mah**: 사용자 보조배터리 정격 용량
    - **phone_capacity_mah**: 사용자 스마트폰 배터리 용량
    """
    try:
        # Android 단위 → NASA 데이터 단위 변환 (mV→V, mA→A, ms→s)
        cycle_df = pd.DataFrame([
            {
                "Voltage_measured": r.voltage_mv / 1000,
                "Current_measured": r.current_ma / 1000,
                "Temperature_measured": r.temperature_c,
                "Time": r.elapsed_ms / 1000,
            }
            for r in request.cycle_records
        ])

        result = predictor.predict(
            cycle_df,
            request.capacity_ah,
            request.powerbank_capacity_mah,
            request.phone_capacity_mah,
        )
        return PredictResponse(**result)

    except Exception as e:
        raise HTTPException(status_code=422, detail=str(e))


@router.get("/health")
def health_check(predictor: SOHPredictor = Depends(get_predictor)):
    """모델 로드 상태 확인"""
    return {
        "status": "ok",
        "model_loaded": predictor.model is not None,
        "scaler_loaded": predictor.scaler is not None,
    }