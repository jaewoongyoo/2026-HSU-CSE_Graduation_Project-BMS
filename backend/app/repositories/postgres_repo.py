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
    device = get_device(db, device_id)
    if not device:
        return False

    db.delete(device)
    _commit_or_raise(
        db,
        conflict_detail=f"device delete conflict: {device_id}",
        operation_detail="failed to delete device",
    )
    return True


def get_session_meta(db: Session, session_id: int) -> Optional[BatterySession]:
    return db.query(BatterySession).filter(BatterySession.id == session_id).first()


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


def get_session_soc_bounds(db: Session, session_id: int) -> tuple[Optional[float], Optional[float]]:
    first_point = (
        db.query(BatteryTelemetry)
        .filter(
            BatteryTelemetry.session_id == session_id,
            BatteryTelemetry.soc.isnot(None),
        )
        .order_by(
            BatteryTelemetry.elapsed_ms.asc().nullslast(),
            BatteryTelemetry.timestamp.asc(),
            BatteryTelemetry.id.asc(),
        )
        .first()
    )
    last_point = (
        db.query(BatteryTelemetry)
        .filter(
            BatteryTelemetry.session_id == session_id,
            BatteryTelemetry.soc.isnot(None),
        )
        .order_by(
            BatteryTelemetry.elapsed_ms.desc().nullslast(),
            BatteryTelemetry.timestamp.desc(),
            BatteryTelemetry.id.desc(),
        )
        .first()
    )
    return (
        float(first_point.soc) if first_point and first_point.soc is not None else None,
        float(last_point.soc) if last_point and last_point.soc is not None else None,
    )


def update_session_finish(
    db: Session,
    session_id: int,
    request: Any,
    *,
    start_battery_level_pct: Optional[float] = None,
    end_battery_level_pct: Optional[float] = None,
) -> Optional[BatterySession]:
    session = get_session_meta(db, session_id)
    if not session:
        return None

    session.session_end_ts = request.session_end_ts
    session.android_api_level = request.android_api_level
    session.powerbank_capacity_start_mah = start_battery_level_pct
    session.session_start_ts = getattr(request, "session_start_ts", None)
    session.capacity_ah = request.capacity_ah
    session.powerbank_capacity_end_mah = end_battery_level_pct
    session.status = "finished"
    _commit_or_raise(
        db,
        conflict_detail=f"session update conflict: {session_id}",
        operation_detail="failed to finish session",
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
    row = BatteryAiResult(
        session_id=session_id,
        soh_percentage=result.get("soh_percentage"),
        condition=result.get("condition"),
        estimated_full_charges=result.get("estimated_full_charges"),
        powerbank_usable_mah=result.get("powerbank_usable_mah"),
        mean_temperature_c=result.get("mean_temperature_c"),
        standard_soh_percentage=result.get("standard_soh_percentage"),
        degradation_rate_ratio=result.get("degradation_rate_ratio"),
        sessions_used=result.get("sessions_used"),
        sessions_total=result.get("sessions_total"),
        confidence=result.get("confidence"),
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


# ==================== Community Feed Functions ====================


def get_all_public_shared_reports(
    db: Session,
    limit: int = 50,
    offset: int = 0,
) -> list[tuple[SharedReport, Device, User]]:
    """모든 공개 공유 보고서 조회"""
    return (
        db.query(SharedReport, Device, User)
        .join(Device, SharedReport.device_id == Device.id)
        .join(User, Device.user_id == User.id)
        .filter(SharedReport.is_public == True)
        .order_by(SharedReport.created_at.desc())
        .limit(limit)
        .offset(offset)
        .all()
    )


def get_public_shared_reports_by_filter(
    db: Session,
    phone_models: Optional[list[str]] = None,
    manufacturers: Optional[list[str]] = None,
    capacities: Optional[list[int]] = None,
    soh_min: Optional[float] = None,
    soh_max: Optional[float] = None,
    limit: int = 50,
    offset: int = 0,
) -> list[tuple[SharedReport, Device, User]]:
    """필터링된 공개 공유 보고서 조회"""
    query = (
        db.query(SharedReport, Device, User)
        .join(Device, SharedReport.device_id == Device.id)
        .join(User, Device.user_id == User.id)
        .filter(SharedReport.is_public == True)
    )

    # phone_model 필터
    if phone_models:
        query = query.filter(User.phone_model.in_(phone_models))

    # manufacturer 필터
    if manufacturers:
        query = query.filter(Device.manufacturer.in_(manufacturers))

    # capacity 필터
    if capacities:
        query = query.filter(Device.powerbank_capacity_mah.in_(capacities))

    # SOH 범위 필터
    if soh_min is not None or soh_max is not None:
        from sqlalchemy import exists
        subq = db.query(BatteryAiResult).join(
            BatterySession,
            BatteryAiResult.session_id == BatterySession.id,
        ).filter(
            BatterySession.device_id == Device.id
        )
        if soh_min is not None:
            subq = subq.filter(BatteryAiResult.soh_percentage >= soh_min)
        if soh_max is not None:
            subq = subq.filter(BatteryAiResult.soh_percentage <= soh_max)
        query = query.filter(subq.exists())

    return (
        query
        .order_by(SharedReport.created_at.desc())
        .limit(limit)
        .offset(offset)
        .all()
    )


def get_session_stats_for_device(
    db: Session,
    device_id: int,
) -> dict:
    """디바이스의 세션 통계 계산"""
    sessions = (
        db.query(BatterySession)
        .filter(
            BatterySession.device_id == device_id,
            BatterySession.status == "finished",
        )
        .all()
    )

    total_usage_seconds = 0.0
    total_capacity_ah = 0.0

    for session in sessions:
        if session.session_start_ts and session.session_end_ts:
            duration = (session.session_end_ts - session.session_start_ts).total_seconds()
            total_usage_seconds += duration

        if session.capacity_ah is not None:
            total_capacity_ah += session.capacity_ah

    return {
        "total_usage_hours": round(total_usage_seconds / 3600, 2),
        "total_capacity_ah": round(total_capacity_ah, 4),
        "finished_session_count": len(sessions),
    }


def get_latest_ai_result_for_device(
    db: Session,
    device_id: int,
) -> Optional[BatteryAiResult]:
    """디바이스의 최신 AI 결과 조회"""
    return (
        db.query(BatteryAiResult)
        .join(BatterySession, BatteryAiResult.session_id == BatterySession.id)
        .filter(BatterySession.device_id == device_id)
        .order_by(BatteryAiResult.created_at.desc())
        .first()
    )


def get_latest_soh_analysis_for_device(
    db: Session,
    device_id: int,
) -> Optional[SohAnalysis]:
    """디바이스의 최신 SOH 분석 조회"""
    return (
        db.query(SohAnalysis)
        .filter(SohAnalysis.device_id == device_id)
        .order_by(SohAnalysis.analyzed_at.desc())
        .first()
    )


def create_shared_report(
    db: Session,
    device_id: int,
    is_public: bool = True,
    share_token: str = None,
) -> SharedReport:
    """공유 보고서 생성"""
    import uuid
    
    if share_token is None:
        share_token = str(uuid.uuid4())

    device = get_device_by_pk(db, device_id)
    if not device:
        raise ValueError(f"device not found: {device_id}")

    shared_report = SharedReport(
        device_id=device_id,
        phone_model=device.user.phone_model if device.user else None,
        is_public=is_public,
        share_token=share_token,
    )
    db.add(shared_report)
    _commit_or_raise(
        db,
        conflict_detail=f"shared report already exists for device: {device_id}",
        operation_detail="failed to create shared report",
    )
    db.refresh(shared_report)
    return shared_report


def update_shared_report(
    db: Session,
    shared_report_id: int,
    is_public: bool,
) -> Optional[SharedReport]:
    """공유 보고서 업데이트"""
    shared_report = (
        db.query(SharedReport).filter(SharedReport.id == shared_report_id).first()
    )
    if not shared_report:
        return None

    shared_report.is_public = is_public
    _commit_or_raise(
        db,
        conflict_detail=f"shared report update conflict: {shared_report_id}",
        operation_detail="failed to update shared report",
    )
    db.refresh(shared_report)
    return shared_report


def delete_shared_report(db: Session, shared_report_id: int) -> bool:
    """공유 보고서 삭제"""
    shared_report = (
        db.query(SharedReport).filter(SharedReport.id == shared_report_id).first()
    )
    if not shared_report:
        return False

    db.delete(shared_report)
    _commit_or_raise(
        db,
        conflict_detail=f"shared report delete conflict: {shared_report_id}",
        operation_detail="failed to delete shared report",
    )
    return True


def get_distinct_phone_models(db: Session) -> list[str]:
    """공개 공유된 폰 모델 목록 조회"""
    results = (
        db.query(User.phone_model)
        .join(Device, Device.user_id == User.id)
        .join(SharedReport, SharedReport.device_id == Device.id)
        .filter(
            SharedReport.is_public == True,
            User.phone_model.isnot(None),
        )
        .distinct()
        .all()
    )
    return [r[0] for r in results if r[0]]


def get_distinct_manufacturers(db: Session) -> list[str]:
    """공개 공유된 배터리 제조사 목록 조회"""
    results = (
        db.query(Device.manufacturer)
        .join(SharedReport, SharedReport.device_id == Device.id)
        .filter(
            SharedReport.is_public == True,
            Device.manufacturer.isnot(None),
        )
        .distinct()
        .all()
    )
    return [r[0] for r in results if r[0]]


def get_distinct_capacities(db: Session) -> list[int]:
    """공개 공유된 배터리 용량 목록 조회"""
    results = (
        db.query(Device.powerbank_capacity_mah)
        .join(SharedReport, SharedReport.device_id == Device.id)
        .filter(
            SharedReport.is_public == True,
            Device.powerbank_capacity_mah.isnot(None),
        )
        .distinct()
        .all()
    )
    return [r[0] for r in results if r[0] is not None]
