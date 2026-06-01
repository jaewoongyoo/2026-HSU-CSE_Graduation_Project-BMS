from typing import List, Optional

from pydantic import BaseModel, Field


class CycleRecord(BaseModel):
    voltage_mv: float
    current_ma: float
    temperature_c: Optional[float] = None
    elapsed_ms: float


class SohPredictRequest(BaseModel):
    cycle_records: List[CycleRecord] = Field(..., min_length=10)
    powerbank_capacity_mah: int = 10000
    phone_capacity_mah: int = 4000


class SohPredictResponse(BaseModel):
    soh_percentage: float
    condition: str
    estimated_full_charges: float
    powerbank_usable_mah: float
    mean_temperature_c: Optional[float] = None
    standard_soh_percentage: Optional[float] = None
    degradation_rate_ratio: Optional[float] = None
    sessions_used: Optional[int] = None
    sessions_total: Optional[int] = None
    confidence: Optional[str] = None


class SohHealthResponse(BaseModel):
    status: str
    model_loaded: bool
