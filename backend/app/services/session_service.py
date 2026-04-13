import uuid

from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import (
    create_device,
    get_device,
)
from app.schemas.session import SessionFinishRequest, SessionStartRequest


def start_session_service(db: Session, request: SessionStartRequest) -> dict:
    session_id = f"dev_{uuid.uuid4().hex[:12]}"
    try:
        create_device(db, session_id, request)
    except ValueError as exc:
        raise InvalidRawDataException(str(exc)) from exc
    return {"session_id": session_id, "status": "in_progress"}


def finish_session_service(db: Session, session_id: str, request: SessionFinishRequest) -> dict:
    device = get_device(db, session_id)
    if not device:
        raise SessionNotFoundException(session_id)

    return {"session_id": session_id, "status": "finished"}
