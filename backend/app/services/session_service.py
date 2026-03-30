import uuid

from app.repositories.dynamodb_repo import save_session_meta, update_session_finish
from app.schemas.session import SessionFinishRequest, SessionStartRequest


def start_session_service(request: SessionStartRequest) -> dict:
    session_id = f"sess_{uuid.uuid4().hex[:12]}"
    save_session_meta(session_id, request)
    return {"session_id": session_id, "status": "in_progress"}


def finish_session_service(session_id: str, request: SessionFinishRequest) -> dict:
    update_session_finish(session_id, request)
    return {"session_id": session_id, "status": "finished"}
