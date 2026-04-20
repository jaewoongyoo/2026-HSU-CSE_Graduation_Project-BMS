from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import get_session_meta, save_raw_points
from app.schemas.raw import RawUploadRequest


def upload_raw_service(db: Session, session_id: int, request: RawUploadRequest) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)
    if session.status != "in_progress":
        raise InvalidRawDataException("raw upload is only allowed for in_progress sessions")
    if not request.data_points:
        raise InvalidRawDataException("data_points must not be empty")

    count = save_raw_points(db, session_id, session.device_id, request.data_points)
    return {
        "id": session_id,
        "received_count": count,
        "status": "received",
    }
