from fastapi import APIRouter

from app.schemas.session import (
    SessionFinishRequest,
    SessionFinishResponse,
    SessionStartRequest,
    SessionStartResponse,
)
from app.services.session_service import finish_session_service, start_session_service

router = APIRouter(prefix="/api/v1/sessions", tags=["sessions"])


@router.post("/start", response_model=SessionStartResponse)
def start_session(request: SessionStartRequest):
    return start_session_service(request)


@router.post("/{session_id}/finish", response_model=SessionFinishResponse)
def finish_session(session_id: str, request: SessionFinishRequest):
    return finish_session_service(session_id, request)
