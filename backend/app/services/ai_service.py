import requests
from itertools import groupby
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


def aggregate_by_10min(raw_points):
    """2초마다 쌓인 데이터를 10분(600초) 단위로 평균값 집계"""
    sorted_points = sorted(raw_points, key=lambda p: p.elapsed_ms or 0)

    result = []
    for bucket, group in groupby(sorted_points, key=lambda p: int((p.elapsed_ms or 0) // 600000)):
        pts = list(group)

        valid_voltage = [p.voltage for p in pts if p.voltage is not None]
        valid_current = [p.current_ma for p in pts if p.current_ma is not None]
        valid_temp = [p.temperature_c for p in pts if p.temperature_c is not None]

        result.append({
            "voltage_mv": (sum(valid_voltage) / len(valid_voltage) * 1000) if valid_voltage else 0,
            "current_ma": (sum(valid_current) / len(valid_current)) if valid_current else 0,
            "temperature_c": (sum(valid_temp) / len(valid_temp)) if valid_temp else None,
            "elapsed_ms": pts[-1].elapsed_ms or 0,
        })

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

    # 10분 단위로 집계
    cycle_records = aggregate_by_10min(raw_points)

    if len(cycle_records) < 1:
        raise InvalidRawDataException("not enough data after aggregation")

    payload = {
        "cycle_records": cycle_records,
        "powerbank_capacity_mah": session.powerbank_capacity_mah or 10000,
        "phone_capacity_mah": session.phone_capacity_mah or 4000,
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