from typing import Optional

from fastapi import APIRouter, Depends, Path
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.device import DeviceCreateRequest, DeviceResponse
from app.services.device_service import (
    create_device_service,
    delete_device_service,
    get_device_service,
    list_devices_service,
)

router = APIRouter(prefix="/api/v1/devices", tags=["devices"])


@router.post("", response_model=DeviceResponse)
def create_device(request: DeviceCreateRequest, db: Session = Depends(get_db)):
    return create_device_service(db, request)



@router.get("/all", response_model=list[DeviceResponse], summary="List All Devices")
def read_all_devices(db: Session = Depends(get_db)):
    return list_devices_service(db)

@router.get("/{user_id}", response_model=list[DeviceResponse])
def read_devices(user_id: Optional[str] = None, db: Session = Depends(get_db)):
    return list_devices_service(db, user_id=user_id)




@router.delete("/{id}")
def delete_device(
    id: int = Path(...,),
    db: Session = Depends(get_db),
):
    return delete_device_service(db, id)
