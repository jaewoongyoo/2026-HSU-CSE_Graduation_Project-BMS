import json
from typing import Any, Iterable, Optional

from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from app.core.exceptions import DatabaseOperationException, ResourceConflictException
from app.db.models import BatterySession, BatteryTelemetry, Device, SohAnalysis, User


def _commit_or_raise(
    db: Session,
    *,
    conflict_detail: str,
    operation_detail: str,
) -> None:
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise ResourceConflictException(conflict_detail) from exc
    except SQLAlchemyError as exc:
        db.rollback()
        raise DatabaseOperationException(operation_detail) from exc


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

    model_name = getattr(request, "model_name", None) or getattr(request, "device_model", None)
    if not model_name:
        raise ValueError("model_name is required")

    device = Device(
        device_id=device_id,
        user_id=user.id,
        manufacturer=getattr(request, "manufacturer", None),
        model_name=model_name,
        capacity_mah=getattr(request, "capacity_mah", None) or getattr(request, "phone_capacity_mah", None),
        powerbank_capacity_mah=getattr(request, "powerbank_capacity_mah", None),
        manufacture_date=getattr(request, "manufacture_date", None),
    )
    db.add(device)
    _commit_or_raise(
        db,
        conflict_detail=f"device already exists: {device_id}",
        operation_detail="failed to create device",
    )
    db.refresh(device)
    return device


def create_session(db: Session, session_id: str, device: Device, request: Any) -> BatterySession:
    session = BatterySession(
        session_id=session_id,
        device_id=device.device_id,
        user_id=device.user_id,
        android_api_level=request.android_api_level,
        powerbank_id=request.powerbank_id,
        cable_id=request.cable_id,
        phone_capacity_mah=request.phone_capacity_mah,
        powerbank_capacity_mah=request.powerbank_capacity_mah,
        session_start_ts=request.session_start_ts,
        status="in_progress",
    )
    db.add(session)
    _commit_or_raise(
        db,
        conflict_detail=f"session already exists: {session_id}",
        operation_detail="failed to create session",
    )
    db.refresh(session)
    return session


def create_device_and_session(
    db: Session,
    *,
    device_id: str,
    session_id: str,
    request: Any,
) -> tuple[Device, BatterySession]:
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
    try:
        db.flush()
    except IntegrityError as exc:
        db.rollback()
        raise ResourceConflictException(f"device already exists: {device_id}") from exc
    except SQLAlchemyError as exc:
        db.rollback()
        raise DatabaseOperationException("failed to create device") from exc

    session = BatterySession(
        session_id=session_id,
        device_id=device.device_id,
        user_id=device.user_id,
        android_api_level=request.android_api_level,
        powerbank_id=request.powerbank_id,
        cable_id=request.cable_id,
        phone_capacity_mah=request.phone_capacity_mah,
        powerbank_capacity_mah=request.powerbank_capacity_mah,
        session_start_ts=request.session_start_ts,
        status="in_progress",
    )
    db.add(session)
    _commit_or_raise(
        db,
        conflict_detail=f"session already exists: {session_id}",
        operation_detail="failed to create session",
    )
    db.refresh(device)
    db.refresh(session)
    return device, session


def get_device(db: Session, device_id: str) -> Optional[Device]:
    return db.query(Device).filter(Device.device_id == device_id).first()


def get_device_by_pk(db: Session, battery_id: int) -> Optional[Device]:
    return db.query(Device).filter(Device.id == battery_id).first()


def list_devices(db: Session, user_identifier: Optional[str] = None) -> list[Device]:
    query = db.query(Device).order_by(Device.created_at.desc(), Device.id.desc())
    if not user_identifier:
        return query.all()

    user = get_user_by_identifier(db, user_identifier)
    if not user:
        return []
    return query.filter(Device.user_id == user.id).all()


def delete_device_by_pk(db: Session, battery_id: int) -> bool:
    device = get_device_by_pk(db, battery_id)
    if not device:
        return False

    db.delete(device)
    _commit_or_raise(
        db,
        conflict_detail=f"device delete conflict: {battery_id}",
        operation_detail="failed to delete device",
    )
    return True


def get_session_meta(db: Session, session_id: str) -> Optional[BatterySession]:
    return db.query(BatterySession).filter(BatterySession.session_id == session_id).first()


def update_session_finish(db: Session, session_id: str, request: Any) -> Optional[BatterySession]:
    session = get_session_meta(db, session_id)
    if not session:
        return None

    session.session_end_ts = request.session_end_ts
    session.capacity_ah = request.capacity_ah
    session.status = "finished"
    _commit_or_raise(
        db,
        conflict_detail=f"session update conflict: {session_id}",
        operation_detail="failed to finish session",
    )
    db.refresh(session)
    return session


def save_raw_points(db: Session, session_id: str, device_id: str, points: Iterable[Any]) -> int:
    rows = []
    for point in points:
        rows.append(
            BatteryTelemetry(
                device_id=device_id,
                session_id=session_id,
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
    _commit_or_raise(
        db,
        conflict_detail=f"raw upload conflict for session: {session_id}",
        operation_detail="failed to save raw points",
    )
    return len(rows)


def get_session_raw_points(db: Session, session_id: str) -> list[BatteryTelemetry]:
    return (
        db.query(BatteryTelemetry)
        .filter(BatteryTelemetry.session_id == session_id)
        .order_by(
            BatteryTelemetry.elapsed_ms.asc().nullslast(),
            BatteryTelemetry.timestamp.asc(),
            BatteryTelemetry.id.asc(),
        )
        .all()
    )


def save_ai_result(db: Session, session_id: str, device_id: str, result: dict) -> SohAnalysis:
    row = SohAnalysis(
        device_id=device_id,
        session_id=session_id,
        current_soh=result.get("soh_percentage"),
        grade=result.get("condition"),
        recommendation=json.dumps(result, ensure_ascii=False),
    )
    db.add(row)
    _commit_or_raise(
        db,
        conflict_detail=f"ai result conflict for session: {session_id}",
        operation_detail="failed to save ai result",
    )
    db.refresh(row)
    return row


def get_latest_ai_result(db: Session, session_id: str) -> Optional[SohAnalysis]:
    return (
        db.query(SohAnalysis)
        .filter(SohAnalysis.session_id == session_id)
        .order_by(SohAnalysis.analyzed_at.desc(), SohAnalysis.id.desc())
        .first()
    )
