from itertools import groupby

import requests
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.exceptions import AIServiceException, InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import get_session_meta, get_session_raw_points, save_ai_result


def get_ai_health() -> dict:
    try:
        response = requests.get(
            f"{settings.AI_SERVER_BASE_URL}/soh/health",
            timeout=settings.REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        return response.json()
    except requests.RequestException as exc:
        raise AIServiceException(str(exc)) from exc


def aggregate_by_10min(raw_points):
    """Aggregate raw telemetry into 10-minute windows for SOH inference."""
    sorted_points = sorted(raw_points, key=lambda point: point.elapsed_ms or 0)

    result = []
    for _, group in groupby(sorted_points, key=lambda point: int((point.elapsed_ms or 0) // 600000)):
        points = list(group)

        valid_voltage = [point.voltage for point in points if point.voltage is not None]
        valid_current = [point.current_ma for point in points if point.current_ma is not None]
        valid_temp = [point.temperature_c for point in points if point.temperature_c is not None]

        result.append(
            {
                "voltage_mv": (sum(valid_voltage) / len(valid_voltage) * 1000) if valid_voltage else 0,
                "current_ma": (sum(valid_current) / len(valid_current)) if valid_current else 0,
                "temperature_c": (sum(valid_temp) / len(valid_temp)) if valid_temp else None,
                "elapsed_ms": points[-1].elapsed_ms or 0,
            }
        )

    return result


def predict_soh_for_session(db: Session, session_id: str) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)

    if session.status != "finished":
        raise InvalidRawDataException("session must be finished before prediction")

    raw_points = get_session_raw_points(db, session_id)
    if len(raw_points) < 10:
        raise InvalidRawDataException("cycle_records must contain at least 10 points")

    cycle_records = aggregate_by_10min(raw_points)
    if not cycle_records:
        raise InvalidRawDataException("not enough data after aggregation")

    payload = {
        "cycle_records": cycle_records,
        "powerbank_capacity_mah": session.powerbank_capacity_mah or 10000,
        "phone_capacity_mah": 4000,
    }

    try:
        response = requests.post(
            f"{settings.AI_SERVER_BASE_URL}/soh/predict",
            json=payload,
            timeout=settings.REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        result = response.json()
        save_ai_result(db, session_id, session.device_id, result)
        return result
    except requests.RequestException as exc:
        raise AIServiceException(str(exc)) from exc
