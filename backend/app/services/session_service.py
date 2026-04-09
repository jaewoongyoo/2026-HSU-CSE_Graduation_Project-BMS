import uuid

from sqlalchemy.orm import Session

from app.core.exceptions import SessionNotFoundException
from app.repositories.postgres_repo import (
    get_session_meta,
    save_session_meta,
    update_session_finish,
)
from app.schemas.session import SessionFinishRequest, SessionStartRequest


def start_session_service(db: Session, request: SessionStartRequest) -> dict:
    session_id = f"sess_{uuid.uuid4().hex[:12]}"
    save_session_meta(db, session_id, request)
    return {"session_id": session_id, "status": "in_progress"}


def finish_session_service(db: Session, session_id: str, request: SessionFinishRequest) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)

    update_session_finish(db, session_id, request)
    return {"session_id": session_id, "status": "finished"}