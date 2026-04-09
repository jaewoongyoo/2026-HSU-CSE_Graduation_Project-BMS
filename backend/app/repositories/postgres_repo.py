import json
from typing import Any, Iterable, Optional

from sqlalchemy.orm import Session

from app.db.models import BatteryAiResult, BatteryRawPoint, BatterySession


def save_session_meta(db: Session, session_id: str, request: Any) -> BatterySession:
    session = BatterySession(
        session_id=session_id,
        user_id=request.user_id,
        device_model=request.device_model,
        android_api_level=request.android_api_level,
        powerbank_id=request.powerbank_id,
        cable_id=request.cable_id,
        phone_capacity_mah=request.phone_capacity_mah,
        powerbank_capacity_mah=request.powerbank_capacity_mah,
        session_start_ts=request.session_start_ts,
        status="in_progress",
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return session


def get_session_meta(db: Session, session_id: str) -> Optional[BatterySession]:
    return (
        db.query(BatterySession)
        .filter(BatterySession.session_id == session_id)
        .first()
    )


def update_session_finish(db: Session, session_id: str, request: Any) -> Optional[BatterySession]:
    session = get_session_meta(db, session_id)
    if not session:
        return None

    session.session_end_ts = request.session_end_ts
    session.capacity_ah = request.capacity_ah
    session.status = "finished"

    db.commit()
    db.refresh(session)
    return session


def save_raw_points(db: Session, session_id: str, points: Iterable[Any]) -> int:
    rows = []
    for point in points:
        rows.append(
            BatteryRawPoint(
                session_id=session_id,
                timestamp=point.timestamp,
                voltage_mv=point.voltage_mv,
                current_ma=point.current_ma,
                temperature_c=point.temperature_c,
                elapsed_ms=point.elapsed_ms,
                battery_level=point.battery_level,
                battery_status=point.battery_status,
                screen_state=point.screen_state,
            )
        )

    db.add_all(rows)
    db.commit()
    return len(rows)


def get_session_raw_points(db: Session, session_id: str) -> list[BatteryRawPoint]:
    return (
        db.query(BatteryRawPoint)
        .filter(BatteryRawPoint.session_id == session_id)
        .order_by(BatteryRawPoint.elapsed_ms.asc())
        .all()
    )


def save_ai_result(db: Session, session_id: str, result: dict) -> BatteryAiResult:
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
    row.raw_response_json = json.dumps(result, ensure_ascii=False)

    db.commit()
    db.refresh(row)
    return row


def get_session_result(db: Session, session_id: str) -> Optional[BatteryAiResult]:
    return (
        db.query(BatteryAiResult)
        .filter(BatteryAiResult.session_id == session_id)
        .first()
    )