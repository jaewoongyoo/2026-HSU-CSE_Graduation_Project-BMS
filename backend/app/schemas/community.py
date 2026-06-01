from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class UserInfo(BaseModel):
    """사용자 기본 정보"""
    username: str
    phone_model: Optional[str] = None


class DeviceInfo(BaseModel):
    """배터리 기본 정보"""
    manufacturer: Optional[str]
    model_name: str
    powerbank_capacity_mah: Optional[int]


class SessionStats(BaseModel):
    """세션 누적 통계"""
    total_usage_hours: float = Field(..., description="누적 사용 시간 (시간 단위)")
    total_capacity_ah: float = Field(..., description="누적 충전량 (Ah)")
    finished_session_count: int = Field(..., description="완료된 세션 수")


class EfficiencyStats(BaseModel):
    """효율 및 SOH 통계"""
    soh_percentage: Optional[float] = Field(None, description="현재 SOH (%)")
    efficiency_pct: Optional[float] = Field(None, description="전력 전달 효율 (%)")
    mean_temperature_c: Optional[float] = Field(None, description="평균 온도 (°C)")


class CommunityCardResponse(BaseModel):
    """커뮤니티 카드 응답 DTO"""
    shared_report_id: int
    user: UserInfo
    device: DeviceInfo
    session_stats: SessionStats
    efficiency_stats: EfficiencyStats
    created_at: datetime
    
    class Config:
        from_attributes = True


class CommunitySohHistoryPoint(BaseModel):
    """커뮤니티 SOH 그래프 단일 지점"""
    measured_at: datetime
    soh_percentage: float


class CommunitySohHistoryResponse(BaseModel):
    """커뮤니티 SOH 그래프 응답 DTO"""
    shared_report_id: int
    points: list[CommunitySohHistoryPoint]


class CommunityFilterRequest(BaseModel):
    """필터링 요청 DTO"""
    phone_models: Optional[list[str]] = Field(default=None, description="폰 모델 필터 (OR 조건)")
    manufacturers: Optional[list[str]] = Field(default=None, description="배터리 제조사 필터 (OR 조건)")
    capacities: Optional[list[int]] = Field(default=None, description="배터리 용량 필터 (mAh, OR 조건)")
    soh_min: Optional[float] = Field(default=None, ge=0, le=100, description="SOH 최소값")
    soh_max: Optional[float] = Field(default=None, ge=0, le=100, description="SOH 최대값")
    limit: int = Field(default=50, ge=1, le=200, description="조회 수 제한")
    offset: int = Field(default=0, ge=0, description="오프셋 (페이지네이션)")


class CommunityShareRequest(BaseModel):
    """공유 요청 DTO"""
    device_id: int
    is_public: bool = True


class CommunityShareResponse(BaseModel):
    """공유 응답 DTO"""
    shared_report_id: int
    device_id: int
    is_public: bool
    share_token: str
    created_at: datetime
    
    class Config:
        from_attributes = True


class CommunityShareUpdateRequest(BaseModel):
    """공유 수정 요청 DTO"""
    is_public: bool = Field(..., description="공개 여부")


class CommunityDeleteResponse(BaseModel):
    """삭제 응답 DTO"""
    success: bool
    message: str
