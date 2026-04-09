from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.session import (
    SessionFinishRequest,
    SessionFinishResponse,
    SessionStartRequest,
    SessionStartResponse,
)
from app.services.session_service import finish_session_service, start_session_service

router = APIRouter(prefix="/api/v1/sessions", tags=["sessions"])


@router.post("/start", response_model=SessionStartResponse)
def start_session(request: SessionStartRequest, db: Session = Depends(get_db)):
    return start_session_service(db, request)


@router.post("/{session_id}/finish", response_model=SessionFinishResponse)
def finish_session(session_id: str, request: SessionFinishRequest, db: Session = Depends(get_db)):
    return finish_session_service(db, session_id, request)