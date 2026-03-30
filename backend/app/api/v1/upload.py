from fastapi import APIRouter

from app.schemas.raw import RawUploadRequest
from app.services.raw_ingest_service import upload_raw_service

router = APIRouter(prefix="/api/v1/sessions", tags=["uploads"])


@router.post("/{session_id}/raw")
def upload_raw(session_id: str, request: RawUploadRequest):
    return upload_raw_service(session_id, request)
