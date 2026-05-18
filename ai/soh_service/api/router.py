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

    voltage_mv: float = Field(..., description="전압 (mV). Android `EXTRA_VOLTAGE` 값 그대로 전달.")
    current_ma: float = Field(..., description="전류 (mA). Android `CURRENT_NOW`(μA) ÷ 1000 변환 후 전달. 충전 시 양수.")
    temperature_c: float | None = Field(None, description="온도 (°C). Android `EXTRA_TEMPERATURE` ÷ 10 변환 후 전달. 없으면 null.")
    elapsed_ms: float = Field(..., description="세션 시작 후 경과 시간 (ms). 단조 증가해야 함.")


class PredictRequest(BaseModel):
    """단일 세션 예측 요청. 개인화 없이 표준 곡선 fallback 값을 반환."""
    model_config = ConfigDict(extra="ignore")

    cycle_records: list[CycleRecord] = Field(
        ..., min_length=10, description="충전 세션 시계열 데이터 (시간 오름차순). 최소 10개 이상."
    )
    powerbank_capacity_mah: int = Field(
        default=10000, gt=0, description="보조배터리 정격 용량 (mAh). 앱 최초 등록 시 사용자 입력값."
    )
    phone_capacity_mah: int = Field(
        default=4000, gt=0, description="스마트폰 배터리 용량 (mAh). 잔여 충전 횟수 계산에 사용."
    )
    capacity_ah: float | None = Field(
        default=None,
        description="[Deprecated] 이 세션의 방전 용량 (Ah). 곡선 피팅 기반 예측에서는 사용되지 않으며 무시됨.",
    )


class SessionInput(BaseModel):
    """다중 세션 요청의 한 세션 단위 입력."""
    model_config = ConfigDict(extra="ignore")

    cycle_records: list[CycleRecord] = Field(
        ..., min_length=2, description="세션 시계열 데이터 (시간 오름차순)."
    )
    start_battery_level_pct: float = Field(
        ...,
        ge=0.0,
        le=100.0,
        description="세션 시작 시 스마트폰 배터리 잔량 (%). 85% 초과 세션은 자동 제외됨.",
    )


class MultiSessionPredictRequest(BaseModel):
    """다중 세션 개인화 예측 요청."""
    model_config = ConfigDict(extra="ignore")

    sessions: list[SessionInput] = Field(
        ...,
        min_length=1,
        description=(
            "세션 목록 (오래된 것부터 시간 오름차순). "
            "20분 미만 / 전달 에너지 2Wh 미만 / 시작 잔량 85% 초과 / 채울 여지 15%p 미만 세션은 자동 제외. "
            "최근 20개 세션만 사용."
        ),
    )
    powerbank_capacity_mah: int = Field(
        default=10000, gt=0, description="보조배터리 정격 용량 (mAh)."
    )
    phone_capacity_mah: int = Field(
        default=4000, gt=0, description="스마트폰 배터리 용량 (mAh). 잔여 충전 횟수 계산에 사용."
    )


class PredictResponse(BaseModel):
    soh_percentage: float = Field(description="예측 SOH (%). 개인화 보정이 적용된 최종값.")
    condition: str = Field(description="배터리 상태 등급. excellent / good / fair / poor / critical")
    estimated_full_charges: float = Field(description="현재 SOH 기준 스마트폰 완충 가능 횟수.")
    powerbank_usable_mah: float = Field(description="실사용 가능 용량 (mAh). DC-DC 컨버터 손실 반영.")
    mean_temperature_c: float | None = Field(description="세션 평균 온도 (°C). 온도 데이터 없으면 null.")
    standard_soh_percentage: float = Field(
        description="표준 곡선 기준 SOH (%). 개인화 보정 전 참조값."
    )
    degradation_rate_ratio: float = Field(
        description="표준 곡선 대비 유저 노화 속도 비율. 1.0 = 표준, >1.0 = 더 빠른 노화."
    )
    sessions_used: int = Field(description="필터 통과한 유효 세션 수.")
    sessions_total: int = Field(description="요청에 포함된 전체 세션 수.")
    confidence: str = Field(description="예측 신뢰도. fallback | low | medium | high")
    smartphone_received_mah: float = Field(
        default=0.0,
        description="[Deprecated] 항상 0.0 반환. 이전 LSTM 기반 API와의 하위호환용 필드.",
    )


# ── 엔드포인트 ─────────────────────────────────────────────────────────────────

@router.post(
    "/predict",
    response_model=PredictResponse,
    summary="단일 세션 SOH 예측",
)
def predict_soh(
    request: PredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """단일 충전 세션 데이터를 기반으로 보조배터리 SOH를 예측합니다.

    개인화 보정 없이 NASA/CALCE 학습 표준 곡선을 그대로 사용합니다.
    누적 세션 데이터가 있다면 `/soh/predict/multi`를 사용하세요.

    **요청 조건**
    - `cycle_records` 최소 10개 이상
    - `elapsed_ms` 단조 증가 (순서 뒤섞임 시 적분 오류 발생)
    - 충전 중 데이터만 전달 (`current_ma` 양수)
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


@router.post(
    "/predict/multi",
    response_model=PredictResponse,
    summary="다중 세션 개인화 SOH 예측",
)
def predict_soh_multi(
    request: MultiSessionPredictRequest,
    predictor: SOHPredictor = Depends(get_predictor),
):
    """다중 충전 세션 데이터를 기반으로 개인화된 SOH를 예측합니다.

    각 세션의 전달 에너지를 적분한 뒤 '채울 여지 1%p 당 Wh'로 정규화해
    세션 순서에 대한 선형 회귀 기울기를 유저별 노화 속도로 추정합니다.

    **세션 자동 필터링 조건** (미통과 세션은 자동 제외)
    - 세션 길이 20분 미만
    - 전달 에너지 2Wh 미만
    - 시작 배터리 잔량 85% 초과
    - 채울 여지 15%p 미만

    **신뢰도 기준**
    - 유효 세션 0~2개: `fallback` (표준 곡선 반환)
    - 유효 세션 3~4개: `low`
    - 유효 세션 5~9개: `medium`
    - 유효 세션 10개 이상: `high`
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


@router.get("/health", summary="모델 상태 확인")
def health_check(predictor: SOHPredictor = Depends(get_predictor)):
    """서버에 로드된 SOH 예측 모델의 상태와 표준 곡선 메타데이터를 반환합니다."""
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
