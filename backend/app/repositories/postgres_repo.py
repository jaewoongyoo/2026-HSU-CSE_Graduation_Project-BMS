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


def create_session(db: Session, device: Device) -> BatterySession:
    session = BatterySession(
        device_id=device.id,
        user_id=device.user_id,
        status="in_progress",
    )
    db.add(session)
    _commit_or_raise(
        db,
        conflict_detail=f"session already exists for device: {device.id}",
        operation_detail="failed to create session",
    )
    db.refresh(session)
    return session


def create_device_and_session(
    db: Session,
    *,
    request: Any,
) -> tuple[Device, BatterySession]:
    device = create_device(db, request=request)
    session = create_session(db, device=device)
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
        row.id
        for row in db.query(BatterySession.id)
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
        db.query(BatterySession).filter(BatterySession.id.in_(session_ids)).delete(
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


def delete_device_by_device_id(db: Session, device_id: str) -> bool:
    try:
        battery_id = int(device_id)
    except (TypeError, ValueError):
        return False

    return delete_device_by_pk(db, battery_id)


def get_session_meta(db: Session, session_id: int) -> Optional[BatterySession]:
    return db.query(BatterySession).filter(BatterySession.id == session_id).first()


def count_session_raw_points(db: Session, session_id: int) -> int:
    return db.query(BatteryTelemetry).filter(BatteryTelemetry.session_id == session_id).count()


def get_recent_finished_sessions_for_device(
    db: Session,
    device_id: int,
    limit: int = 20,
) -> list[BatterySession]:
    return (
        db.query(BatterySession)
        .filter(
            BatterySession.device_id == device_id,
            BatterySession.status == "finished",
        )
        .order_by(
            BatterySession.session_end_ts.desc().nullslast(),
            BatterySession.created_at.desc().nullslast(),
            BatterySession.id.desc(),
        )
        .limit(limit)
        .all()
    )


def update_session_finish(db: Session, session_id: int, request: Any) -> Optional[BatterySession]:
    session = get_session_meta(db, session_id)
    if not session:
        return None

    session.session_end_ts = request.session_end_ts
    session.android_api_level = request.android_api_level
    session.powerbank_capacity_start_mah = getattr(request, "powerbank_capacity_start_mah", None)
    session.session_start_ts = getattr(request, "session_start_ts", None)
    session.capacity_ah = request.capacity_ah
    session.powerbank_capacity_end_mah = getattr(request, "powerbank_capacity_end_mah", None)
    session.label_capacity_ah = getattr(request, "label_capacity_ah", None)
    session.status = "finished"
    _commit_or_raise(
        db,
        conflict_detail=f"session update conflict: {session_id}",
        operation_detail="failed to finish session",
    )
    db.refresh(session)
    return session


def finish_session_with_summary(
    db: Session,
    session_id: int,
    *,
    session_start_ts: Any,
    session_end_ts: Any,
    capacity_ah: float,
    powerbank_capacity_start_mah: Optional[float],
    powerbank_capacity_end_mah: Optional[float],
) -> Optional[BatterySession]:
    session = get_session_meta(db, session_id)
    if not session:
        return None

    session.session_start_ts = session_start_ts
    session.session_end_ts = session_end_ts
    session.capacity_ah = capacity_ah
    session.powerbank_capacity_start_mah = powerbank_capacity_start_mah
    session.powerbank_capacity_end_mah = powerbank_capacity_end_mah
    session.status = "finished"
    _commit_or_raise(
        db,
        conflict_detail=f"session update conflict: {session_id}",
        operation_detail="failed to auto-finish session",
    )
    db.refresh(session)
    return session


def save_raw_points(db: Session, session_id: int, device_id: int, points: Iterable[Any]) -> int:
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
            )
        )

    db.add_all(rows)
    _commit_or_raise(
        db,
        conflict_detail=f"raw upload conflict for session: {session_id}",
        operation_detail="failed to save raw points",
    )
    return len(rows)


def get_session_raw_points(db: Session, session_id: int) -> list[BatteryTelemetry]:
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


def save_ai_result(db: Session, session_id: int, device_id: int, result: dict) -> BatteryAiResult:
    raw_response_json = json.dumps(result, ensure_ascii=False)
    row = (
        db.query(BatteryAiResult)
        .filter(BatteryAiResult.session_id == session_id)
        .first()
    )
    if not row:
        row = BatteryAiResult(session_id=session_id)
        db.add(row)

    row.soh_percentage = result.get("soh_percentage")
    row.condition = result.get("condition")
    row.estimated_full_charges = result.get("estimated_full_charges")
    row.powerbank_usable_mah = result.get("powerbank_usable_mah")
    row.smartphone_received_mah = result.get("smartphone_received_mah")
    row.mean_temperature_c = result.get("mean_temperature_c")
    row.raw_response_json = raw_response_json

    analysis = (
        db.query(SohAnalysis)
        .filter(SohAnalysis.session_id == session_id)
        .first()
    )
    if not analysis:
        analysis = SohAnalysis(
            device_id=device_id,
            session_id=session_id,
        )
        db.add(analysis)

    analysis.current_soh = result.get("soh_percentage")
    analysis.grade = result.get("condition")
    analysis.recommendation = raw_response_json

    _commit_or_raise(
        db,
        conflict_detail=f"ai result conflict for session: {session_id}",
        operation_detail="failed to save ai result",
    )
    db.refresh(row)
    return row


def get_latest_ai_result(db: Session, session_id: int) -> Optional[BatteryAiResult]:
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
        row.id
        for row in db.query(BatterySession.id)
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
        db.query(BatterySession).filter(BatterySession.id.in_(session_ids)).delete(
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
