from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException
from app.repositories.postgres_repo import create_device, delete_device_by_pk, get_device_by_pk, list_devices
from app.schemas.device import DeviceCreateRequest


def create_device_service(db: Session, request: DeviceCreateRequest):
    try:
        return create_device(db, request=request)
    except ValueError as exc:
        raise InvalidRawDataException(str(exc)) from exc


def list_devices_service(db: Session, user_id: Optional[str] = None):
    return list_devices(db, user_identifier=user_id)


def get_device_service(db: Session, device_id: int):
    device = get_device_by_pk(db, device_id)
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"device not found: {device_id}",
        )
    return device


def delete_device_service(db: Session, id: int):
    deleted = delete_device_by_pk(db, id)
    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"device not found: {id}",
        )
    return {"message": "device deleted"}
