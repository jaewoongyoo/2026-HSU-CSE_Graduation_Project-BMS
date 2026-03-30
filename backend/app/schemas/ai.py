from typing import List

from pydantic import BaseModel, Field


class CycleRecord(BaseModel):
    voltage_mv: float
    current_ma: float
    temperature_c: float
    elapsed_ms: float


class SohPredictRequest(BaseModel):
    cycle_records: List[CycleRecord] = Field(..., min_length=10)
    capacity_ah: float
    powerbank_capacity_mah: int = 10000
    phone_capacity_mah: int = 4000


class SohPredictResponse(BaseModel):
    soh_percentage: float
    condition: str
    estimated_full_charges: float
    powerbank_usable_mah: float
    smartphone_received_mah: float
    mean_temperature_c: float


class SohHealthResponse(BaseModel):
    status: str
    model_loaded: bool
    scaler_loaded: bool
