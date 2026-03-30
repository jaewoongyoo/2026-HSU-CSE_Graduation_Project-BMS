from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator


class RawDataPoint(BaseModel):
    timestamp: datetime
    voltage_mv: float = Field(..., description="전압(mV)")
    current_ma: float = Field(..., description="전류(mA), 방전 중 음수")
    temperature_c: float = Field(..., description="온도(°C)")
    elapsed_ms: float = Field(..., description="세션 시작 후 경과 시간(ms)")
    battery_level: Optional[int] = Field(default=None, description="배터리 잔량(%)")
    battery_status: Optional[str] = Field(default=None, description="배터리 상태")
    screen_state: Optional[bool] = Field(default=None, description="화면 on/off")


class RawUploadRequest(BaseModel):
    session_id: str
    user_id: str
    data_points: List[RawDataPoint] = Field(..., min_length=1)

    @field_validator("data_points")
    @classmethod
    def validate_elapsed_ms_monotonic(cls, value: List[RawDataPoint]) -> List[RawDataPoint]:
        elapsed_list = [item.elapsed_ms for item in value]
        if elapsed_list != sorted(elapsed_list):
            raise ValueError("data_points.elapsed_ms must be monotonically increasing")
        return value
