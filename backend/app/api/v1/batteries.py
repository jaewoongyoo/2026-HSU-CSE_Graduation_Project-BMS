from typing import Optional

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.schemas.battery import BatteryCreateRequest, BatteryResponse
from app.services.battery_service import (
    create_battery_service,
    delete_battery_service,
    get_battery_service,
    list_batteries_service,
)

router = APIRouter(prefix="/api/v1/batteries", tags=["batteries"])


@router.post("", response_model=BatteryResponse)
def create_battery(request: BatteryCreateRequest, db: Session = Depends(get_db)):
    return create_battery_service(db, request)


@router.get("", response_model=list[BatteryResponse])
def read_batteries(user_id: Optional[str] = None, db: Session = Depends(get_db)):
    return list_batteries_service(db, user_id=user_id)


@router.get("/{device_id}", response_model=BatteryResponse)
def read_battery(device_id: str, db: Session = Depends(get_db)):
    return get_battery_service(db, device_id)


@router.delete("/{device_id}")
def delete_battery(device_id: str, db: Session = Depends(get_db)):
    return delete_battery_service(db, device_id)
