from fastapi import APIRouter

from app.schemas.ai import SohPredictResponse
from app.schemas.result import SessionResultResponse
from app.services.ai_service import predict_soh_for_session
from app.services.result_service import get_session_result_service

router = APIRouter(prefix="/api/v1/sessions", tags=["results"])


@router.post("/{session_id}/predict-soh", response_model=SohPredictResponse)
def predict_soh(session_id: str):
    return predict_soh_for_session(session_id)


@router.get("/{session_id}/result", response_model=SessionResultResponse)
def get_result(session_id: str):
    return get_session_result_service(session_id)
