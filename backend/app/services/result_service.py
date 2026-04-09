from sqlalchemy.orm import Session

from app.core.exceptions import SessionNotFoundException
from app.repositories.postgres_repo import get_session_meta, get_session_result


def get_session_result_service(db: Session, session_id: str) -> dict:
    meta = get_session_meta(db, session_id)
    if not meta:
        raise SessionNotFoundException(session_id)

    result = get_session_result(db, session_id)

    return {
        "session_id": session_id,
        "status": meta.status,
        "soh_percentage": result.soh_percentage if result else None,
        "condition": result.condition if result else None,
        "estimated_full_charges": result.estimated_full_charges if result else None,
        "powerbank_usable_mah": result.powerbank_usable_mah if result else None,
        "smartphone_received_mah": result.smartphone_received_mah if result else None,
        "mean_temperature_c": result.mean_temperature_c if result else None,
    }