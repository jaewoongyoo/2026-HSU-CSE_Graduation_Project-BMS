from typing import Any, Iterable

import boto3
from boto3.dynamodb.conditions import Key

from app.core.config import settings


dynamodb = boto3.resource("dynamodb", region_name=settings.AWS_REGION)
session_table = dynamodb.Table(settings.DDB_SESSION_TABLE)
raw_table = dynamodb.Table(settings.DDB_RAW_TABLE)


def save_session_meta(session_id: str, request: Any) -> None:
    session_table.put_item(
        Item={
            "PK": f"SESSION#{session_id}",
            "SK": "META",
            "session_id": session_id,
            "user_id": request.user_id,
            "device_model": request.device_model,
            "android_api_level": request.android_api_level,
            "powerbank_id": request.powerbank_id,
            "cable_id": request.cable_id,
            "phone_capacity_mah": request.phone_capacity_mah,
            "powerbank_capacity_mah": request.powerbank_capacity_mah,
            "session_start_ts": request.session_start_ts.isoformat(),
            "session_end_ts": None,
            "capacity_ah": None,
            "status": "in_progress",
        }
    )


def update_session_finish(session_id: str, request: Any) -> None:
    session_table.update_item(
        Key={"PK": f"SESSION#{session_id}", "SK": "META"},
        UpdateExpression="SET session_end_ts = :end_ts, capacity_ah = :cap, #st = :status",
        ExpressionAttributeNames={"#st": "status"},
        ExpressionAttributeValues={
            ":end_ts": request.session_end_ts.isoformat(),
            ":cap": request.capacity_ah,
            ":status": "finished",
        },
    )


def save_raw_points(session_id: str, points: Iterable[Any]) -> None:
    with raw_table.batch_writer() as batch:
        for point in points:
            batch.put_item(
                Item={
                    "PK": f"SESSION#{session_id}",
                    "SK": f"POINT#{int(point.elapsed_ms):010d}",
                    "session_id": session_id,
                    "timestamp": point.timestamp.isoformat(),
                    "voltage_mv": point.voltage_mv,
                    "current_ma": point.current_ma,
                    "temperature_c": point.temperature_c,
                    "elapsed_ms": point.elapsed_ms,
                    "battery_level": point.battery_level,
                    "battery_status": point.battery_status,
                    "screen_state": point.screen_state,
                }
            )


def get_session_meta(session_id: str) -> dict | None:
    response = session_table.get_item(Key={"PK": f"SESSION#{session_id}", "SK": "META"})
    return response.get("Item")


def get_session_raw_points(session_id: str) -> list[dict]:
    response = raw_table.query(
        KeyConditionExpression=Key("PK").eq(f"SESSION#{session_id}"),
        ScanIndexForward=True,
    )
    return response.get("Items", [])


def save_ai_result(session_id: str, result: dict) -> None:
    session_table.put_item(
        Item={
            "PK": f"SESSION#{session_id}",
            "SK": "RESULT",
            "session_id": session_id,
            **result,
        }
    )


def get_session_result(session_id: str) -> dict | None:
    response = session_table.get_item(Key={"PK": f"SESSION#{session_id}", "SK": "RESULT"})
    return response.get("Item")
