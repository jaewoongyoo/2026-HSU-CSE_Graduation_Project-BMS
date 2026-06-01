from itertools import groupby

import requests
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.exceptions import AIServiceException, InvalidRawDataException, SessionNotFoundException
from app.repositories.postgres_repo import (
    get_recent_finished_sessions_for_device,
    get_session_meta,
    get_session_raw_points,
    save_ai_result,
)


RECENT_SESSION_LIMIT = 20
MIN_VALID_SESSION_COUNT = 3
MIN_SESSION_DURATION_MS = 5 * 60 * 1000
MIN_PERSONALIZATION_DURATION_MS = 15 * 60 * 1000
MIN_FAST_CHARGE_PERSONALIZATION_DURATION_MS = 8 * 60 * 1000
FAST_CHARGE_THRESHOLD_MA = 2000.0
# delivered Wh 체크 해제: 저성능 핸드폰/보조배터리 환경에서 2Wh 기준이 과도하게 세션을 제외했음
MIN_DELIVERED_WH = 0.0
MAX_START_BATTERY_LEVEL_PCT = 85.0
MIN_FILLABLE_GAP_PCT = 15.0
AGGREGATION_WINDOW_MS = 10 * 60 * 1000
DEFAULT_POWERBANK_CAPACITY_MAH = 10000
DEFAULT_PHONE_CAPACITY_MAH = 4000


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


def _is_fast_charging(raw_points) -> bool:
    """Median current ≥ FAST_CHARGE_THRESHOLD_MA → fast charging session."""
    currents = [
        abs(float(point.current_ma))
        for point in raw_points
        if point.current_ma is not None
    ]
    if not currents:
        return False
    median_current = sorted(currents)[len(currents) // 2]
    return median_current >= FAST_CHARGE_THRESHOLD_MA


def _sorted_points(raw_points):
    return sorted(
        raw_points,
        key=lambda point: (
            point.elapsed_ms if point.elapsed_ms is not None else float("inf"),
            point.timestamp,
            point.id,
        ),
    )


def _get_start_battery_level_pct(raw_points) -> float | None:
    for point in _sorted_points(raw_points):
        if point.soc is not None:
            return float(point.soc)
    return None


def _calculate_duration_ms(raw_points) -> float:
    elapsed_values = [
        float(point.elapsed_ms)
        for point in raw_points
        if point.elapsed_ms is not None
    ]
    if len(elapsed_values) < 2:
        return 0.0
    return max(elapsed_values) - min(elapsed_values)


def _calculate_delivered_wh(raw_points) -> float:
    points = [
        point
        for point in _sorted_points(raw_points)
        if point.elapsed_ms is not None
        and point.voltage is not None
        and point.current_ma is not None
    ]
    if len(points) < 2:
        return 0.0

    total_ws = 0.0
    for previous, current in zip(points, points[1:]):
        delta_s = (float(current.elapsed_ms) - float(previous.elapsed_ms)) / 1000.0
        if delta_s <= 0:
            continue

        previous_power_w = float(previous.voltage) * abs(float(previous.current_ma) / 1000.0)
        current_power_w = float(current.voltage) * abs(float(current.current_ma) / 1000.0)
        total_ws += ((previous_power_w + current_power_w) / 2.0) * delta_s

    return total_ws / 3600.0


def aggregate_by_10min(raw_points) -> list[dict]:
    """Aggregate raw telemetry into 10-minute windows for SOH inference."""
    sorted_points = [
        point for point in _sorted_points(raw_points) if point.elapsed_ms is not None
    ]

    result = []
    for _, group in groupby(
        sorted_points,
        key=lambda point: int(float(point.elapsed_ms) // AGGREGATION_WINDOW_MS),
    ):
        points = list(group)

        valid_voltage = [point.voltage for point in points if point.voltage is not None]
        valid_current = [point.current_ma for point in points if point.current_ma is not None]
        valid_temp = [point.temperature_c for point in points if point.temperature_c is not None]

        if not valid_voltage or not valid_current:
            continue

        result.append(
            {
                "voltage_mv": sum(valid_voltage) / len(valid_voltage) * 1000,
                "current_ma": sum(valid_current) / len(valid_current),
                "temperature_c": (sum(valid_temp) / len(valid_temp)) if valid_temp else None,
                "elapsed_ms": points[-1].elapsed_ms,
            }
        )

    return result


def _get_session_reject_reason(raw_points, cycle_records: list[dict]) -> str | None:
    if len(raw_points) < 2:
        return "too_few_raw_points"

    start_battery_level_pct = _get_start_battery_level_pct(raw_points)
    if start_battery_level_pct is None:
        return "missing_start_battery_level"

    duration_ms = _calculate_duration_ms(raw_points)
    if duration_ms < MIN_SESSION_DURATION_MS:
        return "duration_too_short"
    min_personal_ms = (
        MIN_FAST_CHARGE_PERSONALIZATION_DURATION_MS
        if _is_fast_charging(raw_points)
        else MIN_PERSONALIZATION_DURATION_MS
    )
    if duration_ms < min_personal_ms:
        return "duration_too_short_for_personalization"

    if start_battery_level_pct > MAX_START_BATTERY_LEVEL_PCT:
        return "start_level_too_high"

    fillable_gap_pct = 100.0 - start_battery_level_pct
    if fillable_gap_pct < MIN_FILLABLE_GAP_PCT:
        return "fillable_gap_too_small"

    if len(cycle_records) < 2:
        return "too_few_cycle_records"

    return None


def _build_valid_ai_session_input(raw_points) -> dict | None:
    cycle_records = aggregate_by_10min(raw_points)
    reject_reason = _get_session_reject_reason(raw_points, cycle_records)
    if reject_reason:
        return None

    return {
        "cycle_records": cycle_records,
        "start_battery_level_pct": _get_start_battery_level_pct(raw_points),
    }


def _build_multi_session_payload(db: Session, session) -> tuple[dict, int]:
    recent_sessions = get_recent_finished_sessions_for_device(
        db,
        session.device_id,
        limit=RECENT_SESSION_LIMIT,
    )

    valid_sessions = []
    for recent_session in reversed(recent_sessions):
        raw_points = get_session_raw_points(db, recent_session.id)
        session_input = _build_valid_ai_session_input(raw_points)
        if session_input:
            valid_sessions.append(session_input)

    # 주석: Swagger 스펙(유효 세션 0~2개 시 fallback 제공)에 부합하도록 3개 미만 시 에러 차단막을 제거합니다.


    payload = {
        "sessions": valid_sessions,
        "powerbank_capacity_mah": (
            session.device.powerbank_capacity_mah
            if session.device and session.device.powerbank_capacity_mah is not None
            else DEFAULT_POWERBANK_CAPACITY_MAH
        ),
        "phone_capacity_mah": DEFAULT_PHONE_CAPACITY_MAH,
    }
    return payload, len(recent_sessions)


def _request_ai_multi_prediction(payload: dict) -> dict:
    try:
        response = requests.post(
            f"{settings.AI_SERVER_BASE_URL}/soh/predict/multi",
            json=payload,
            timeout=settings.REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        return response.json()
    except requests.RequestException as exc:
        raise AIServiceException(str(exc)) from exc


def predict_soh_for_session(db: Session, session_id: int) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)

    if session.status != "finished":
        raise InvalidRawDataException("session must be finished before prediction")

    payload, sessions_total = _build_multi_session_payload(db, session)
    
    # 🛡️ 유효 세션이 아예 0개인 경우, AI 서버(minItems=1) 에러 방지를 위해 백엔드에서 즉시 SOH 100% 표준 Fallback 결과를 구성하여 저장합니다.
    if not payload["sessions"]:
        capacity_mah = session.device.powerbank_capacity_mah if (session.device and session.device.powerbank_capacity_mah) else DEFAULT_POWERBANK_CAPACITY_MAH
        usable_mah = capacity_mah * 1.0 * 0.85 # 효율 85% 반영
        result = {
            "soh_percentage": 100.0,
            "condition": "우수",
            "estimated_full_charges": round(usable_mah / DEFAULT_PHONE_CAPACITY_MAH, 2),
            "powerbank_usable_mah": float(usable_mah),
            "mean_temperature_c": None,
            "standard_soh_percentage": 100.0,
            "degradation_rate_ratio": 1.0,
            "sessions_used": 0,
            "sessions_total": sessions_total,
            "confidence": "fallback"
        }
        save_ai_result(db, session_id, session.device_id, result)
        return result

    result = _request_ai_multi_prediction(payload)
    result.setdefault("sessions_total", sessions_total)
    result.setdefault("sessions_used", len(payload["sessions"]))
    save_ai_result(db, session_id, session.device_id, result)
    return result
