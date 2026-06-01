from typing import Optional

from fastapi import APIRouter, Depends, Path
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.device import DeviceCreateRequest, DeviceResponse
from app.schemas.result import SessionResultResponse
from app.services.device_service import (
    create_device_service,
    delete_device_service,
    get_device_service,
    list_devices_service,
)
from app.services.result_service import get_latest_device_result_service

router = APIRouter(prefix="/api/v1/devices", tags=["devices"])


@router.post("", response_model=DeviceResponse)
def create_device(request: DeviceCreateRequest, db: Session = Depends(get_db)):
    return create_device_service(db, request)



@router.get("/all", response_model=list[DeviceResponse], summary="List All Devices")
def read_all_devices(db: Session = Depends(get_db)):
    return list_devices_service(db)

@router.get("/{device_id}/latest-result", response_model=SessionResultResponse, summary="Get latest finished session result for device")
def read_latest_device_result(
    device_id: int = Path(..., gt=0),
    db: Session = Depends(get_db),
):
    return get_latest_device_result_service(db, device_id)

@router.get("/{user_id}", response_model=list[DeviceResponse])
def read_devices(user_id: Optional[str] = None, db: Session = Depends(get_db)):
    return list_devices_service(db, user_id=user_id)




@router.delete("/{id}")
def delete_device(
    id: int = Path(...,),
    db: Session = Depends(get_db),
):
    return delete_device_service(db, id)
