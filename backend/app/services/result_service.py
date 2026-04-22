from sqlalchemy.orm import Session

from app.core.exceptions import SessionNotFoundException
from app.repositories.postgres_repo import get_latest_ai_result, get_session_meta


def get_session_result_service(db: Session, session_id: int) -> dict:
    session = get_session_meta(db, session_id)
    if not session:
        raise SessionNotFoundException(session_id)

    result = get_latest_ai_result(db, session_id)
    return {
        "id": session_id,
        "status": session.status,
        "soh_percentage": result.soh_percentage if result else None,
        "condition": result.condition if result else None,
        "estimated_full_charges": result.estimated_full_charges if result else None,
        "powerbank_usable_mah": result.powerbank_usable_mah if result else None,
        "smartphone_received_mah": result.smartphone_received_mah if result else None,
        "mean_temperature_c": result.mean_temperature_c if result else None,
        "analyzed_at": result.created_at if result else None,
    }
