from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field, field_validator


class BatteryCreateRequest(BaseModel):
    user_id: str = Field(..., description="기기 소유 사용자 ID 또는 username")
    device_id: Optional[str] = Field(default=None, description="직접 지정할 기기 식별자")
    manufacturer: Optional[str] = Field(default=None, description="제조사")
    model_name: str = Field(..., min_length=1, max_length=100, description="기기 모델명 또는 별칭")
    capacity_mah: int = Field(..., gt=0, le=200000, description="배터리 용량(mAh)")
    manufacture_date: Optional[str] = Field(default=None, max_length=20, description="제조일")
    powerbank_capacity_mah: Optional[int] = Field(default=None, gt=0, le=200000, description="보조배터리 용량(mAh)")

    @field_validator("user_id", "model_name")
    @classmethod
    def validate_required_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("must not be blank")
        return value

    @field_validator("device_id", "manufacturer", "manufacture_date")
    @classmethod
    def normalize_optional_text(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        value = value.strip()
        return value or None


class BatteryResponse(BaseModel):
    id: int
    user_id: int
    device_id: str
    manufacturer: Optional[str]
    model_name: str
    capacity_mah: int
    manufacture_date: Optional[str]
    powerbank_capacity_mah: Optional[int]
    created_at: Optional[datetime]

    class Config:
        from_attributes = True
