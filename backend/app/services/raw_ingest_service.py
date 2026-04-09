from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import get_session_meta, save_raw_points
from app.schemas.raw import RawUploadRequest


def upload_raw_service(db: Session, session_id: str, request: RawUploadRequest) -> dict:
    meta = get_session_meta(db, session_id)
    if not meta:
        raise SessionNotFoundException(session_id)

    if meta.status != "in_progress":
        raise InvalidRawDataException("raw upload is only allowed for in_progress sessions")

    count = save_raw_points(db, session_id, request.data_points)
    return {
        "session_id": session_id,
        "received_count": count,
        "status": "received",
    }