from sqlalchemy.orm import Session

from app.core.exceptions import AIServiceException, InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import (
    count_session_raw_points,
    create_session,
    finish_session_with_summary,
    get_session_meta,
    get_session_raw_points,
    save_raw_points,
)
from app.schemas.raw import RawUploadRequest
from app.services.ai_service import predict_soh_for_session


SESSION_TARGET_POINT_COUNT = 300
SESSION_TARGET_DURATION_MS = 10 * 60 * 1000


def _sorted_points(raw_points):
    return sorted(
        raw_points,
        key=lambda point: (
            point.elapsed_ms if point.elapsed_ms is not None else float("inf"),
            point.timestamp,
            point.id,
        ),
    )


def _session_duration_ms(raw_points) -> float:
    elapsed_values = [
        float(point.elapsed_ms)
        for point in raw_points
        if point.elapsed_ms is not None
    ]
    if len(elapsed_values) < 2:
        return 0.0
    return max(elapsed_values) - min(elapsed_values)


def _calculate_capacity_ah(raw_points) -> float:
    points = [
        point
        for point in _sorted_points(raw_points)
        if point.elapsed_ms is not None and point.current_ma is not None
    ]
    if len(points) < 2:
        return 0.0

    total_mah = 0.0
    for previous, current in zip(points, points[1:]):
        delta_h = (float(current.elapsed_ms) - float(previous.elapsed_ms)) / 3600000.0
        if delta_h <= 0:
            continue

        previous_current_ma = abs(float(previous.current_ma))
        current_current_ma = abs(float(current.current_ma))
        total_mah += ((previous_current_ma + current_current_ma) / 2.0) * delta_h

    return total_mah / 1000.0


def _finish_session_from_raw_points(db: Session, session, raw_points):
    points = _sorted_points(raw_points)
    capacity_ah = _calculate_capacity_ah(points)
    start_capacity_mah = session.powerbank_capacity_start_mah
    if start_capacity_mah is None and session.device:
        start_capacity_mah = session.device.powerbank_capacity_mah

    end_capacity_mah = None
    if start_capacity_mah is not None:
        end_capacity_mah = max(float(start_capacity_mah) - (capacity_ah * 1000.0), 0.0)

    return finish_session_with_summary(
        db,
        session.id,
        session_start_ts=points[0].timestamp,
        session_end_ts=points[-1].timestamp,
        capacity_ah=capacity_ah,
        powerbank_capacity_start_mah=start_capacity_mah,
        powerbank_capacity_end_mah=end_capacity_mah,
    )


def upload_raw_service(db: Session, session_id: int, request: RawUploadRequest) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)
    if session.status != "in_progress":
        raise InvalidRawDataException("raw upload is only allowed for in_progress sessions")
    if not request.data_points:
        raise InvalidRawDataException("data_points must not be empty")

    count = save_raw_points(db, session_id, session.device_id, request.data_points)
    raw_points = get_session_raw_points(db, session_id)
    total_count = count_session_raw_points(db, session_id)
    duration_ms = _session_duration_ms(raw_points)
    is_complete = (
        total_count >= SESSION_TARGET_POINT_COUNT
        or duration_ms >= SESSION_TARGET_DURATION_MS
    )

    if not is_complete:
        return {
            "id": session_id,
            "received_count": count,
            "total_count": total_count,
            "duration_ms": duration_ms,
            "status": "received",
        }

    finished_session = _finish_session_from_raw_points(db, session, raw_points)
    ai_result = None
    ai_error = None
    try:
        ai_result = predict_soh_for_session(db, session_id, prefer_multi=False)
    except (AIServiceException, InvalidRawDataException) as exc:
        ai_error = str(exc)

    next_session = create_session(db, device=finished_session.device)
    return {
        "id": session_id,
        "received_count": count,
        "total_count": total_count,
        "duration_ms": duration_ms,
        "status": "finished",
        "next_session_id": next_session.id,
        "ai_status": "saved" if ai_result else "failed",
        "ai_error": ai_error,
    }
