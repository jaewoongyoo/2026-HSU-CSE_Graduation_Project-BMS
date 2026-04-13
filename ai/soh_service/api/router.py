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

class CycleRecord(BaseModel):
    """충전 세션 단일 시점 측정값 (Android BatteryManager 수집 데이터)"""
    voltage_mv: float = Field(..., description="전압 (mV)")
    current_ma: float = Field(..., description="전류 (mA), 충전 시 양수")
    temperature_c: float | None = Field(None, description="온도 (°C), 없으면 null")
    elapsed_ms: float = Field(..., description="세션 시작 후 경과 시간 (ms)")


class PredictRequest(BaseModel):
    cycle_records: list[CycleRecord] = Field(
        ..., min_length=10, description="충전 세션 시계열 데이터 (시간 오름차순)"
    )
    powerbank_capacity_mah: int = Field(
        default=10000, gt=0, description="보조배터리 정격 용량 (mAh)"
    )
    phone_capacity_mah: int = Field(
        default=4000, gt=0, description="스마트폰 배터리 용량 (mAh)"
    )


class MultiSessionPredictRequest(BaseModel):
    sessions: list[list[CycleRecord]] = Field(
        ...,
        min_length=1,
        description=(
            "세션 목록 (오래된 것부터 시간 오름차순). "
            "각 원소는 한 세션의 cycle_records. "
            "10분 미만 세션은 자동 제외됨."
        ),
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


class MultiSessionPredictResponse(BaseModel):
    soh_percentage: float
    condition: str
    estimated_full_charges: float
    powerbank_usable_mah: float
    mean_temperature_c: float | None
    sessions_used: int = Field(description="유효 window가 생성된 세션 수 (10분 미만 제외 후)")


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
            for record in request.cycle_records
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


@router.post("/predict/multi", response_model=MultiSessionPredictResponse)
def predict_soh_multi(
    request: MultiSessionPredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """
    동일 파워뱅크의 여러 충전 세션을 누적해 SOH를 예측합니다.

    - **sessions**: 오래된 세션부터 순서대로 전달 (최대 20개 사용, 초과 시 최근 20개만)
    - 각 세션은 10분(600초) 이상이어야 유효한 window로 처리됩니다
    - 10분 미만 세션은 자동으로 제외되며, `sessions_used`로 실제 반영된 수를 확인할 수 있습니다
    """
    try:
        sessions = [
            [
                TelemetryPoint(
                    time_s=record.elapsed_ms / 1000.0,
                    voltage_v=record.voltage_mv / 1000.0,
                    current_a=record.current_ma / 1000.0,
                    temperature_c=record.temperature_c,
                )
                for record in session
            ]
            for session in request.sessions
        ]
        result = predictor.predict_multi(
            sessions,
            request.powerbank_capacity_mah,
            request.phone_capacity_mah,
        )
        return MultiSessionPredictResponse(**result)

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
