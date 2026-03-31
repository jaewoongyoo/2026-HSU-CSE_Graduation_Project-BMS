"""
FastAPI 애플리케이션 진입점
실행: uvicorn soh_service.main:app --reload
"""

from fastapi import FastAPI
from soh_service.api.router import router
from soh_service.core.predictor import get_predictor

app = FastAPI(
    title="SOH Prediction API",
    description="보조배터리 수명(SOH) 예측 서비스",
    version="1.0.0",
)

app.include_router(router)


@app.on_event("startup")
def preload_model():
    """서버 시작 시 모델을 메모리에 미리 로드"""
    get_predictor()
    print("[startup] SOH 모델 로드 완료")