from fastapi import APIRouter

from app.schemas.ai import SohHealthResponse
from app.services.ai_service import get_ai_health

router = APIRouter(tags=["health"])


@router.get("/api/v1/health")
def health():
    return {"status": "ok"}


@router.get("/api/v1/ai/health", response_model=SohHealthResponse)
def ai_health():
    return get_ai_health()
