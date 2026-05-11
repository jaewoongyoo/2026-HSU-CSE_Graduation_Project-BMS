from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field, field_validator


class DeviceCreateRequest(BaseModel):
    user_id: str = Field(..., description="User ID or username")
    manufacturer: Optional[str] = Field(default=None, max_length=100, description="Device manufacturer")
    model_name: str = Field(..., min_length=1, max_length=100, description="Device model name")
    manufacture_date: Optional[str] = Field(default=None, max_length=20, description="Manufacture date")
    powerbank_capacity_mah: Optional[int] = Field(
        default=None,
        gt=0,
        le=200000,
        description="Power bank capacity in mAh",
    )

    @field_validator("user_id", "model_name")
    @classmethod
    def validate_required_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("must not be blank")
        return value

    @field_validator("manufacturer", "manufacture_date")
    @classmethod
    def normalize_optional_text(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        value = value.strip()
        return value or None


class DeviceResponse(BaseModel):
    id: int
    user_id: int
    manufacturer: Optional[str]
    model_name: str
    powerbank_capacity_mah: Optional[int]
    manufacture_date: Optional[str]
    created_at: Optional[datetime]

    class Config:
        from_attributes = True
