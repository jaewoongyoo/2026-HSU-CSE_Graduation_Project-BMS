from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.raw import RawUploadRequest
from app.services.raw_ingest_service import upload_raw_service

router = APIRouter(prefix="/api/v1/sessions", tags=["uploads"])


@router.post("/{session_id}/raw")
def upload_raw(session_id: int, request: RawUploadRequest, db: Session = Depends(get_db)):
    return upload_raw_service(db, session_id, request)
