import uuid
from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException
from app.repositories.postgres_repo import (
    create_device,
    delete_device_by_device_id,
    get_device,
    list_devices,
)
from app.schemas.battery import BatteryCreateRequest


def create_battery_service(db: Session, request: BatteryCreateRequest):
    device_id = request.device_id or f"dev_{uuid.uuid4().hex[:12]}"
    payload = request.model_copy(update={"device_id": device_id})
    try:
        return create_device(db, device_id=device_id, request=payload)
    except ValueError as exc:
        raise InvalidRawDataException(str(exc)) from exc


def list_batteries_service(db: Session, user_id: Optional[str] = None):
    return list_devices(db, user_identifier=user_id)


def get_battery_service(db: Session, device_id: str):
    device = get_device(db, device_id)
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"battery not found: {device_id}",
        )
    return device


def delete_battery_service(db: Session, device_id: str):
    deleted = delete_device_by_device_id(db, device_id)
    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"battery not found: {device_id}",
        )
    return {"message": "battery deleted"}