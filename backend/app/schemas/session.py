from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class SessionStartRequest(BaseModel):
    user_id: str = Field(..., description="사용자 식별자")
    device_model: str = Field(..., description="스마트폰 기종")
    android_api_level: int = Field(..., description="안드로이드 API 레벨")
    powerbank_id: Optional[str] = Field(default=None, description="보조배터리 식별자")
    cable_id: Optional[str] = Field(default=None, description="케이블 식별자")
    phone_capacity_mah: int = Field(default=4000, description="스마트폰 배터리 용량(mAh)")
    powerbank_capacity_mah: int = Field(default=10000, description="보조배터리 정격 용량(mAh)")
    session_start_ts: datetime = Field(..., description="세션 시작 시각")


class SessionStartResponse(BaseModel):
    session_id: str
    status: str


class SessionFinishRequest(BaseModel):
    session_end_ts: datetime = Field(..., description="세션 종료 시각")
    capacity_ah: float = Field(..., description="이번 세션 총 방전 용량(Ah)")


class SessionFinishResponse(BaseModel):
    session_id: str
    status: str
