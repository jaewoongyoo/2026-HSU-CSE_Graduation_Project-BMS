import logging

from sqlalchemy.orm import Session

from app.core.exceptions import InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import (
    create_session,
    get_device_by_pk,
    get_session_meta,
    get_session_soc_bounds,
    update_session_finish,
)
from app.schemas.session import SessionFinishRequest, SessionStartRequest


logger = logging.getLogger(__name__)
MIN_CAPACITY_WARNING_DURATION_SECONDS = 20 * 60
SUSPICIOUSLY_LOW_CAPACITY_AH = 0.001


def start_session_service(db: Session, request: SessionStartRequest) -> dict:
    device = get_device_by_pk(db, request.device_id)
    if not device:
        raise InvalidRawDataException(f"device not found: {request.device_id}")

    try:
        session = create_session(db, device=device)
    except ValueError as exc:
        raise InvalidRawDataException(str(exc)) from exc

    return {
        "id": session.id,
        "status": "in_progress",
    }


def finish_session_service(
    db: Session,
    session_id: int,
    request: SessionFinishRequest,
) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)
    if session.status != "in_progress":
        raise InvalidRawDataException("session is not in progress")

    telemetry_start_soc, telemetry_end_soc = get_session_soc_bounds(db, session_id)
    start_battery_level_pct = (
        request.powerbank_capacity_start_mah
        if request.powerbank_capacity_start_mah is not None
        else telemetry_start_soc
    )
    end_battery_level_pct = (
        request.powerbank_capacity_end_mah
        if request.powerbank_capacity_end_mah is not None
        else telemetry_end_soc
    )

    if request.session_start_ts:
        duration_seconds = (request.session_end_ts - request.session_start_ts).total_seconds()
        if (
            duration_seconds >= MIN_CAPACITY_WARNING_DURATION_SECONDS
            and request.capacity_ah <= SUSPICIOUSLY_LOW_CAPACITY_AH
        ):
            logger.warning(
                "suspiciously low capacity_ah for session_id=%s: capacity_ah=%s duration_seconds=%s",
                session_id,
                request.capacity_ah,
                duration_seconds,
            )

    update_session_finish(
        db,
        session_id,
        request,
        start_battery_level_pct=start_battery_level_pct,
        end_battery_level_pct=end_battery_level_pct,
    )
    return {"id": session_id, "status": "finished"}
