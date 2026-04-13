from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import get_device, save_raw_points
from app.schemas.raw import RawUploadRequest


def upload_raw_service(db: Session, session_id: str, request: RawUploadRequest) -> dict:
    device = get_device(db, session_id)
    if not device:
        raise SessionNotFoundException(session_id)

    if not request.data_points:
        raise InvalidRawDataException("data_points must not be empty")

    count = save_raw_points(db, session_id, request.data_points)
    return {
        "session_id": session_id,
        "received_count": count,
        "status": "received",
    }
