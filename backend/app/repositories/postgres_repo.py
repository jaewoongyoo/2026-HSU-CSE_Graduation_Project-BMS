import json
from typing import Any, Iterable, Optional

from sqlalchemy.orm import Session

from app.db.models import BatteryTelemetry, Device, SohAnalysis, User


def get_user_by_identifier(db: Session, user_identifier: str) -> Optional[User]:
    if user_identifier.isdigit():
        user = db.query(User).filter(User.id == int(user_identifier)).first()
        if user:
            return user

    return db.query(User).filter(User.username == user_identifier).first()


def create_device(db: Session, device_id: str, request: Any) -> Device:
    user = get_user_by_identifier(db, request.user_id)
    if not user:
        raise ValueError(f"user not found: {request.user_id}")

    device = Device(
        device_id=device_id,
        user_id=user.id,
        manufacturer=None,
        model_name=request.device_model,
        capacity_mah=request.phone_capacity_mah,
        powerbank_capacity_mah=request.powerbank_capacity_mah,
        manufacture_date=None,
    )
    db.add(device)
    db.commit()
    db.refresh(device)
    return device


def get_device(db: Session, device_id: str) -> Optional[Device]:
    return db.query(Device).filter(Device.device_id == device_id).first()


def save_raw_points(db: Session, device_id: str, points: Iterable[Any]) -> int:
    rows = []
    for point in points:
        rows.append(
            BatteryTelemetry(
                device_id=device_id,
                timestamp=point.timestamp,
                soc=point.battery_level,
                voltage=(point.voltage_mv / 1000.0) if point.voltage_mv is not None else None,
                current_ma=point.current_ma,
                temperature_c=point.temperature_c,
                elapsed_ms=point.elapsed_ms,
                battery_status=point.battery_status,
                screen_state=point.screen_state,
                power_w=(
                    (point.voltage_mv / 1000.0) * (point.current_ma / 1000.0)
                    if point.voltage_mv is not None and point.current_ma is not None
                    else None
                ),
            )
        )

    db.add_all(rows)
    db.commit()
    return len(rows)


def get_device_raw_points(db: Session, device_id: str) -> list[BatteryTelemetry]:
    return (
        db.query(BatteryTelemetry)
        .filter(BatteryTelemetry.device_id == device_id)
        .order_by(
            BatteryTelemetry.elapsed_ms.asc().nullslast(),
            BatteryTelemetry.timestamp.asc(),
            BatteryTelemetry.id.asc(),
        )
        .all()
    )


def save_ai_result(db: Session, device_id: str, result: dict) -> SohAnalysis:
    row = SohAnalysis(
        device_id=device_id,
        current_soh=result.get("soh_percentage"),
        grade=result.get("condition"),
        recommendation=json.dumps(result, ensure_ascii=False),
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def get_latest_ai_result(db: Session, device_id: str) -> Optional[SohAnalysis]:
    return (
        db.query(SohAnalysis)
        .filter(SohAnalysis.device_id == device_id)
        .order_by(SohAnalysis.analyzed_at.desc(), SohAnalysis.id.desc())
        .first()
    )
