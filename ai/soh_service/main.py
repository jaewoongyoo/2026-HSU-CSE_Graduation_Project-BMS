"""
FastAPI 애플리케이션 진입점
실행: uvicorn soh_service.main:app --reload
"""

from fastapi import FastAPI
from soh_service.api.router import router
from soh_service.core.predictor import get_predictor

app = FastAPI(
    title="BatteryPulse SOH Prediction API",
    description="""
## 보조배터리 수명(SOH) 예측 서비스

Android BatteryManager에서 수집한 충전 세션 데이터를 기반으로
보조배터리의 **State of Health(SOH)** 를 예측합니다.

### 예측 방식
- **표준 곡선 기반**: NASA/CALCE 데이터셋으로 학습한 지수 감소 곡선 `SOH(x) = a·exp(−b·x) + c` 사용
- **개인화 보정**: 다중 세션 데이터에서 유저별 노화 속도를 추정해 표준 곡선을 보정

### 엔드포인트 요약
| 엔드포인트 | 설명 |
|---|---|
| `POST /soh/predict` | 단일 세션 예측 (표준 곡선 fallback) |
| `POST /soh/predict/multi` | 다중 세션 개인화 예측 |
| `GET /soh/health` | 모델 로드 상태 확인 |

### Android 데이터 수집 단위 변환
- `CURRENT_NOW` (μA) ÷ 1000 → mA
- `EXTRA_TEMPERATURE` ÷ 10 → °C
- `EXTRA_VOLTAGE` 단위: mV (변환 불필요)
""",
    version="1.0.0",
)

app.include_router(router)


@app.on_event("startup")
def preload_model():
    """서버 시작 시 모델을 메모리에 미리 로드"""
    get_predictor()
    print("[startup] SOH 모델 로드 완료")
