from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.ai import SohPredictResponse
from app.schemas.result import SessionResultResponse
from app.services.ai_service import predict_soh_for_session
from app.services.result_service import get_session_result_service

router = APIRouter(prefix="/api/v1/sessions", tags=["results"])


@router.post("/{session_id}/predict-soh", response_model=SohPredictResponse)
def predict_soh(session_id: str, db: Session = Depends(get_db)):
    return predict_soh_for_session(db, session_id)


@router.get("/{session_id}/result", response_model=SessionResultResponse)
def get_result(session_id: str, db: Session = Depends(get_db)):
    return get_session_result_service(db, session_id)