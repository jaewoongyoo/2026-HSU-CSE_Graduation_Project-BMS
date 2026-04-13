import json

from sqlalchemy.orm import Session

from app.core.exceptions import SessionNotFoundException
from app.repositories.postgres_repo import get_device, get_latest_ai_result


def get_session_result_service(db: Session, session_id: str) -> dict:
    device = get_device(db, session_id)
    if not device:
        raise SessionNotFoundException(session_id)

    result = get_latest_ai_result(db, session_id)
    raw_payload = {}
    if result and result.recommendation:
        try:
            raw_payload = json.loads(result.recommendation)
        except json.JSONDecodeError:
            raw_payload = {}

    return {
        "session_id": session_id,
        "status": "finished" if result else "in_progress",
        "soh_percentage": result.current_soh if result else None,
        "condition": result.grade if result else None,
        "estimated_full_charges": raw_payload.get("estimated_full_charges"),
        "powerbank_usable_mah": raw_payload.get("powerbank_usable_mah"),
        "smartphone_received_mah": raw_payload.get("smartphone_received_mah"),
        "mean_temperature_c": raw_payload.get("mean_temperature_c"),
        "sessions_used": raw_payload.get("sessions_used"),
    }
