from app.repositories.dynamodb_repo import get_session_meta, save_raw_points
from app.core.exceptions import SessionNotFoundException
from app.schemas.raw import RawUploadRequest


def upload_raw_service(session_id: str, request: RawUploadRequest) -> dict:
    meta = get_session_meta(session_id)
    if not meta:
        raise SessionNotFoundException(session_id)

    save_raw_points(session_id, request.data_points)
    return {
        "session_id": session_id,
        "received_count": len(request.data_points),
        "status": "received",
    }
