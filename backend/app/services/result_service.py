from app.core.exceptions import SessionNotFoundException
from app.repositories.dynamodb_repo import get_session_meta, get_session_result


def get_session_result_service(session_id: str) -> dict:
    meta = get_session_meta(session_id)
    if not meta:
        raise SessionNotFoundException(session_id)

    result = get_session_result(session_id)
    return {
        "session_id": session_id,
        "status": meta["status"],
        "soh_percentage": result.get("soh_percentage") if result else None,
        "condition": result.get("condition") if result else None,
        "estimated_full_charges": result.get("estimated_full_charges") if result else None,
        "powerbank_usable_mah": result.get("powerbank_usable_mah") if result else None,
        "smartphone_received_mah": result.get("smartphone_received_mah") if result else None,
        "mean_temperature_c": result.get("mean_temperature_c") if result else None,
    }
