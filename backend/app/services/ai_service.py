import requests
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.exceptions import AIServiceException, InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import (
    get_session_meta,
    get_session_raw_points,
    save_ai_result,
)


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


def predict_soh_for_session(db: Session, session_id: str) -> dict:
    meta = get_session_meta(db, session_id)
    if not meta:
        raise SessionNotFoundException(session_id)

    if meta.status != "finished":
        raise InvalidRawDataException("session must be finished before prediction")

    if meta.capacity_ah is None:
        raise InvalidRawDataException("capacity_ah is required before prediction")

    raw_points = get_session_raw_points(db, session_id)
    if len(raw_points) < 10:
        raise InvalidRawDataException("cycle_records must contain at least 10 points")

    payload = {
        "cycle_records": [
            {
                "voltage_mv": point.voltage_mv,
                "current_ma": point.current_ma,
                "temperature_c": point.temperature_c,
                "elapsed_ms": point.elapsed_ms,
            }
            for point in raw_points
        ],
        "capacity_ah": meta.capacity_ah,
        "powerbank_capacity_mah": meta.powerbank_capacity_mah or 10000,
        "phone_capacity_mah": meta.phone_capacity_mah or 4000,
    }

    try:
        response = requests.post(
            f"{settings.AI_SERVER_BASE_URL}/soh/predict",
            json=payload,
            timeout=settings.REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        result = response.json()
        save_ai_result(db, session_id, result)
        return result
    except requests.RequestException as exc:
        raise AIServiceException(str(exc)) from exc