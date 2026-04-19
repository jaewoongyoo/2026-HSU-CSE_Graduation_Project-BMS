"""
SOH 예측 API 엔드포인트
POST /soh/predict       : 단일 세션 → 표준 곡선 fallback 예측
POST /soh/predict/multi : 다중 세션 → 개인화 SOH 예측 (유저 기울기 반영)
GET  /soh/health        : 모델 로드 상태 확인

※ 기존 LSTM 기반 API와의 하위호환을 위해 deprecated 필드를 일부 유지한다.
  - 요청: `capacity_ah` 필드가 포함돼도 무시된다.
  - 응답: `smartphone_received_mah` 필드는 항상 0.0을 반환한다 (계산되지 않음).
  상세 내용은 soh_service/README.md 참조.
"""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from soh_service.core.predictor import SOHPredictor, get_predictor
from soh_service.curve_fit.predictor import TelemetrySample, UserSessionInput

router = APIRouter(prefix="/soh", tags=["SOH Prediction"])


# ── 요청/응답 스키마 ───────────────────────────────────────────────────────────

class CycleRecord(BaseModel):
    """충전 세션 단일 시점 측정값 (Android BatteryManager 수집 데이터)."""
    model_config = ConfigDict(extra="ignore")

    voltage_mv: float = Field(..., description="전압 (mV)")
    current_ma: float = Field(..., description="전류 (mA), 충전 시 양수")
    temperature_c: float | None = Field(None, description="온도 (°C), 없으면 null")
    elapsed_ms: float = Field(..., description="세션 시작 후 경과 시간 (ms)")


class PredictRequest(BaseModel):
    """단일 세션 예측 요청. 개인화 없이 표준 곡선 fallback 값을 반환."""
    model_config = ConfigDict(extra="ignore")

    cycle_records: list[CycleRecord] = Field(
        ..., min_length=10, description="충전 세션 시계열 데이터 (시간 오름차순)"
    )
    powerbank_capacity_mah: int = Field(
        default=10000, gt=0, description="보조배터리 정격 용량 (mAh)"
    )
    phone_capacity_mah: int = Field(
        default=4000, gt=0, description="스마트폰 배터리 용량 (mAh)"
    )
    # 하위호환: 기존 API에서 필수였던 필드. 현재 구현에선 사용하지 않고 무시됨.
    capacity_ah: float | None = Field(
        default=None,
        description="[Deprecated] 이 세션의 방전 용량 (Ah). 곡선 피팅 기반 예측에서는 사용되지 않는다.",
    )


class SessionInput(BaseModel):
    """다중 세션 요청의 한 세션 단위 입력."""
    model_config = ConfigDict(extra="ignore")

    cycle_records: list[CycleRecord] = Field(
        ..., min_length=2, description="세션 시계열 데이터 (시간 오름차순)"
    )
    start_battery_level_pct: float = Field(
        ...,
        ge=0.0,
        le=100.0,
        description="세션 시작 시 스마트폰 배터리 잔량 (%). 충전 시작 지점의 배터리 용량.",
    )


class MultiSessionPredictRequest(BaseModel):
    """다중 세션 개인화 예측 요청."""
    model_config = ConfigDict(extra="ignore")

    sessions: list[SessionInput] = Field(
        ...,
        min_length=1,
        description=(
            "세션 목록 (오래된 것부터 시간 오름차순). "
            "각 세션은 cycle_records와 start_battery_level_pct를 포함. "
            "필터 조건(10분 이상, 충전 시작 잔량 85% 이하 등) 미통과 세션은 자동 제외."
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
    standard_soh_percentage: float = Field(
        description="표준 곡선 기준 SOH (%). 개인화 전 참조값."
    )
    degradation_rate_ratio: float = Field(
        description="표준 곡선 대비 유저 노화 속도 비율 (1.0 = 표준, >1.0 = 더 빠른 노화)"
    )
    sessions_used: int = Field(description="필터 통과한 유효 세션 수")
    sessions_total: int = Field(description="요청에 포함된 전체 세션 수")
    confidence: str = Field(description="예측 신뢰도: fallback | low | medium | high")
    # 하위호환: 기존 LSTM 응답에 있던 필드. 현재 구현에선 계산하지 않아 항상 0.0을 반환한다.
    smartphone_received_mah: float = Field(
        default=0.0,
        description="[Deprecated] 이번 세션 스마트폰 실수신 용량 (mAh). 항상 0.0 반환.",
    )


# ── 엔드포인트 ─────────────────────────────────────────────────────────────────

@router.post("/predict", response_model=PredictResponse)
def predict_soh(
    request: PredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """단일 세션 기반 SOH 예측 (개인화 없음, 표준 곡선 fallback).

    유저별 노화 속도를 반영하려면 `/soh/predict/multi`를 사용하세요.
    """
    try:
        telemetry = [
            TelemetrySample(
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


@router.post("/predict/multi", response_model=PredictResponse)
def predict_soh_multi(
    request: MultiSessionPredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """다중 세션 기반 개인화 SOH 예측.

    각 세션에서 전달 에너지를 적분한 뒤 '채울 여지 1%p 당 Wh'로 정규화해
    세션 순서에 대한 선형 회귀 기울기를 유저별 노화 속도로 사용한다.

    - 최근 20개 세션만 사용
    - 10분 미만 / 전달 에너지 2Wh 미만 / 시작 잔량 85% 초과 / 채울 여지 15%p 미만 세션은 자동 제외
    - 유효 세션이 3개 미만이면 표준 곡선 fallback 반환 (`confidence = low | fallback`)
    """
    try:
        session_inputs = [
            UserSessionInput(
                samples=[
                    TelemetrySample(
                        time_s=record.elapsed_ms / 1000.0,
                        voltage_v=record.voltage_mv / 1000.0,
                        current_a=record.current_ma / 1000.0,
                        temperature_c=record.temperature_c,
                    )
                    for record in session.cycle_records
                ],
                start_battery_level_pct=session.start_battery_level_pct,
            )
            for session in request.sessions
        ]
        result = predictor.predict_multi(
            session_inputs,
            request.powerbank_capacity_mah,
            request.phone_capacity_mah,
        )
        return PredictResponse(**result)

    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))


@router.get("/health")
def health_check(predictor: SOHPredictor = Depends(get_predictor)):
    """모델 로드 상태 확인."""
    artifact = predictor._predictor.artifact
    return {
        "status": "ok",
        "curve_fit_loaded": True,
        "standard_curve": {
            "dataset_scope": artifact.dataset_scope,
            "fit_point_count": artifact.fit_point_count,
            "cell_count": artifact.cell_count,
            "rmse": round(artifact.rmse, 4),
            "mae": round(artifact.mae, 4),
        },
    }
