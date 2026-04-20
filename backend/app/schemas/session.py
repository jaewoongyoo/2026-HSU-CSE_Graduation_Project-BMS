from datetime import datetime

from pydantic import BaseModel, Field


class SessionStartRequest(BaseModel):
    device_id: int = Field(..., gt=0, description="devices.id")
    android_api_level: int = Field(..., ge=1, description="Android API level")
    powerbank_id: str | None = Field(default=None, description="Power bank identifier")
    powerbank_capacity_start_mah: float | None = Field(
        default=None,
        ge=0,
        description="Stored in battery_sessions.powerbank_capacity_start_mah",
    )
    session_start_ts: datetime = Field(..., description="Session start timestamp")


class SessionStartResponse(BaseModel):
    session_id: str
    status: str
    device_id: int
    user_id: int


class SessionFinishRequest(BaseModel):
    session_end_ts: datetime = Field(..., description="Session end timestamp")
    capacity_ah: float = Field(..., gt=0, description="Measured delivered capacity in Ah")
    powerbank_capacity_end_mah: float | None = Field(
        default=None,
        ge=0,
        description="Stored in battery_sessions.powerbank_capacity_end_mah",
    )
    label_capacity_ah: float | None = Field(
        default=None,
        ge=0,
        description="Stored in battery_sessions.label_capacity_ah",
    )


class SessionFinishResponse(BaseModel):
    session_id: str
    status: str
