import uuid

from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import (
    create_device_and_session,
    get_session_meta,
    update_session_finish,
)
from app.schemas.session import SessionFinishRequest, SessionStartRequest


def start_session_service(db: Session, request: SessionStartRequest) -> dict:
    device_id = f"dev_{uuid.uuid4().hex[:12]}"
    session_id = f"sess_{uuid.uuid4().hex[:12]}"
    try:
        create_device_and_session(
            db,
            device_id=device_id,
            session_id=session_id,
            request=request,
        )
    except ValueError as exc:
        raise InvalidRawDataException(str(exc)) from exc
    return {"session_id": session_id, "status": "in_progress"}


def finish_session_service(db: Session, session_id: str, request: SessionFinishRequest) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)
    if session.status != "in_progress":
        raise InvalidRawDataException("session is not in progress")

    update_session_finish(db, session_id, request)
    return {"session_id": session_id, "status": "finished"}
