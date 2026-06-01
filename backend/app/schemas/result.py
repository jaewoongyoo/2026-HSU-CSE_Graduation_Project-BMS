from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class SessionResultResponse(BaseModel):
    id: int
    status: str
    soh_percentage: Optional[float] = None
    condition: Optional[str] = None
    estimated_full_charges: Optional[float] = None
    powerbank_usable_mah: Optional[float] = None
    mean_temperature_c: Optional[float] = None
    analyzed_at: Optional[datetime] = None
