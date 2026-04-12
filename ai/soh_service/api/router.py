"""
SOH 예측 API 엔드포인트
POST /predict  : 충전 세션 텔레메트리 → SOH 예측
GET  /health   : 모델 로드 상태 확인
"""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from soh_service.core.predictor import SOHPredictor, get_predictor
from soh_service.inference.predictor import TelemetryPoint

router = APIRouter(prefix="/soh", tags=["SOH Prediction"])


# ── 요청/응답 스키마 ───────────────────────────────────────────────────────────

class TelemetryRecord(BaseModel):
    """충전 세션 단일 시점 측정값 (Android BatteryManager 수집 데이터)"""
    voltage_mv: float = Field(..., description="전압 (mV)")
    current_ma: float = Field(..., description="전류 (mA), 충전 시 양수")
    temperature_c: float | None = Field(None, description="온도 (°C), 없으면 null")
    elapsed_ms: float = Field(..., description="세션 시작 후 경과 시간 (ms)")


class PredictRequest(BaseModel):
    telemetry: list[TelemetryRecord] = Field(
        ..., min_length=10, description="충전 세션 시계열 데이터 (시간 오름차순)"
    )
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
    mean_temperature_c: float | None


# ── 엔드포인트 ─────────────────────────────────────────────────────────────────

@router.post("/predict", response_model=PredictResponse)
def predict_soh(
    request: PredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """
    Android 앱에서 수집한 충전 세션 텔레메트리를 받아 SOH를 예측합니다.

    - **telemetry**: BatteryManager API로 수집한 시계열 데이터 (최소 600초 이상 권장)
    - **powerbank_capacity_mah**: 사용자 보조배터리 정격 용량
    - **phone_capacity_mah**: 사용자 스마트폰 배터리 용량
    """
    try:
        telemetry = [
            TelemetryPoint(
                time_s=record.elapsed_ms / 1000.0,
                voltage_v=record.voltage_mv / 1000.0,
                current_a=record.current_ma / 1000.0,
                temperature_c=record.temperature_c,
            )
            for record in request.telemetry
        ]
        result = predictor.predict(
            telemetry,
            request.powerbank_capacity_mah,
            request.phone_capacity_mah,
        )
        return PredictResponse(**result)

    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@router.get("/health")
def health_check(predictor: SOHPredictor = Depends(get_predictor)):
    """모델 로드 상태 확인"""
    return {
        "status": "ok",
        "model_loaded": predictor._predictor is not None,
    }
