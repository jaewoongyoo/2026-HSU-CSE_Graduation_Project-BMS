from datetime import datetime

from pydantic import BaseModel, Field


class SessionStartRequest(BaseModel):
    user_id: str = Field(..., description="User ID or username")
    device_id: int = Field(..., gt=0, description="devices.id")
    android_api_level: int = Field(..., ge=1, description="Android API level")
    powerbank_id: str | None = Field(default=None, description="Power bank identifier")
    powerbank_capacity_mah: int | None = Field(
        default=None,
        gt=0,
        le=200000,
        description="Power bank capacity override in mAh",
    )
    session_start_ts: datetime = Field(..., description="Session start timestamp")


class SessionStartResponse(BaseModel):
    session_id: str
    status: str


class SessionFinishRequest(BaseModel):
    session_end_ts: datetime = Field(..., description="Session end timestamp")
    capacity_ah: float = Field(..., gt=0, description="Measured delivered capacity in Ah")


class SessionFinishResponse(BaseModel):
    session_id: str
    status: str
