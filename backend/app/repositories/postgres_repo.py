import json
from typing import Any, Iterable, Optional

from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from app.core.exceptions import DatabaseOperationException, ResourceConflictException
from app.db.models import (
    BatteryAiResult,
    BatterySession,
    BatteryTelemetry,
    Device,
    SharedReport,
    SohAnalysis,
    User,
)


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


def create_device(db: Session, request: Any) -> Device:
    user = get_user_by_identifier(db, request.user_id)
    if not user:
        raise ValueError(f"user not found: {request.user_id}")

    device = Device(
        user_id=user.id,
        manufacturer=getattr(request, "manufacturer", None),
        model_name=request.model_name,
        powerbank_capacity_mah=request.powerbank_capacity_mah,
        manufacture_date=getattr(request, "manufacture_date", None),
    )
    db.add(device)
    _commit_or_raise(
        db,
        conflict_detail="device already exists",
        operation_detail="failed to create device",
    )
    db.refresh(device)
    return device


def create_session(db: Session, session_id: str, device: Device, request: Any) -> BatterySession:
    session = BatterySession(
        session_id=session_id,
        device_id=device.id,
        user_id=device.user_id,
        android_api_level=request.android_api_level,
        powerbank_id=getattr(request, "powerbank_id", None),
        powerbank_capacity_mah=getattr(request, "powerbank_capacity_mah", None)
        or device.powerbank_capacity_mah,
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
    session_id: str,
    request: Any,
) -> tuple[Device, BatterySession]:
    device = create_device(db, request=request)
    session = create_session(db, session_id=session_id, device=device, request=request)
    return device, session


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

    session_ids = [
        row.session_id
        for row in db.query(BatterySession.session_id)
        .filter(BatterySession.device_id == battery_id)
        .all()
    ]

    if session_ids:
        db.query(BatteryAiResult).filter(BatteryAiResult.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(BatteryTelemetry).filter(BatteryTelemetry.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(SohAnalysis).filter(SohAnalysis.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(BatterySession).filter(BatterySession.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )

    db.query(BatteryTelemetry).filter(BatteryTelemetry.device_id == battery_id).delete(
        synchronize_session=False
    )
    db.query(SohAnalysis).filter(SohAnalysis.device_id == battery_id).delete(
        synchronize_session=False
    )
    db.query(SharedReport).filter(SharedReport.device_id == battery_id).delete(
        synchronize_session=False
    )
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


def save_raw_points(db: Session, session_id: str, device_id: int, points: Iterable[Any]) -> int:
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


def save_ai_result(db: Session, session_id: str, device_id: int, result: dict) -> BatteryAiResult:
    row = BatteryAiResult(
        session_id=session_id,
        soh_percentage=result.get("soh_percentage"),
        condition=result.get("condition"),
        estimated_full_charges=result.get("estimated_full_charges"),
        powerbank_usable_mah=result.get("powerbank_usable_mah"),
        smartphone_received_mah=result.get("smartphone_received_mah"),
        mean_temperature_c=result.get("mean_temperature_c"),
        raw_response_json=json.dumps(result, ensure_ascii=False),
    )
    db.add(row)
    db.add(
        SohAnalysis(
            device_id=device_id,
            session_id=session_id,
            current_soh=result.get("soh_percentage"),
            grade=result.get("condition"),
            recommendation=json.dumps(result, ensure_ascii=False),
        )
    )
    _commit_or_raise(
        db,
        conflict_detail=f"ai result conflict for session: {session_id}",
        operation_detail="failed to save ai result",
    )
    db.refresh(row)
    return row


def get_latest_ai_result(db: Session, session_id: str) -> Optional[BatteryAiResult]:
    return (
        db.query(BatteryAiResult)
        .filter(BatteryAiResult.session_id == session_id)
        .order_by(BatteryAiResult.created_at.desc(), BatteryAiResult.id.desc())
        .first()
    )


def delete_user_by_id(db: Session, user_id: int) -> bool:
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        return False

    device_ids = [
        row.id
        for row in db.query(Device.id)
        .filter(Device.user_id == user_id)
        .all()
    ]
    session_ids = [
        row.session_id
        for row in db.query(BatterySession.session_id)
        .filter(BatterySession.user_id == user_id)
        .all()
    ]

    if session_ids:
        db.query(BatteryAiResult).filter(BatteryAiResult.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(BatteryTelemetry).filter(BatteryTelemetry.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(SohAnalysis).filter(SohAnalysis.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )
        db.query(BatterySession).filter(BatterySession.session_id.in_(session_ids)).delete(
            synchronize_session=False
        )

    if device_ids:
        db.query(BatteryTelemetry).filter(BatteryTelemetry.device_id.in_(device_ids)).delete(
            synchronize_session=False
        )
        db.query(SohAnalysis).filter(SohAnalysis.device_id.in_(device_ids)).delete(
            synchronize_session=False
        )
        db.query(SharedReport).filter(SharedReport.device_id.in_(device_ids)).delete(
            synchronize_session=False
        )
        db.query(Device).filter(Device.id.in_(device_ids)).delete(synchronize_session=False)

    db.query(User).filter(User.id == user_id).delete(synchronize_session=False)
    _commit_or_raise(
        db,
        conflict_detail=f"user delete conflict: {user_id}",
        operation_detail="failed to delete user",
    )
    return True
