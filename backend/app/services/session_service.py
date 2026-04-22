from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import create_session, get_device_by_pk, get_session_meta, update_session_finish
from app.schemas.session import SessionFinishRequest, SessionStartRequest


def start_session_service(db: Session, request: SessionStartRequest) -> dict:
    device = get_device_by_pk(db, request.device_id)
    if not device:
        raise InvalidRawDataException(f"device not found: {request.device_id}")

    try:
        session = create_session(db, device=device)
    except ValueError as exc:
        raise InvalidRawDataException(str(exc)) from exc

    return {
        "id": session.id,
        "status": "in_progress",
    }


def finish_session_service(
    db: Session,
    session_id: int,
    request: SessionFinishRequest,
) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)
    if session.status != "in_progress":
        raise InvalidRawDataException("session is not in progress")

    update_session_finish(db, session_id, request)
    return {"id": session_id, "status": "finished"}
