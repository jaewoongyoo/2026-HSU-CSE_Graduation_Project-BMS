"""
방전 사이클 시계열 데이터 → 모델 입력 피처 변환
"""

import numpy as np
import pandas as pd
from scipy import integrate

from soh_service.core.config import (
    NOMINAL_CAPACITY_AH,
    CONVERTER_EFFICIENCY,
    OUTPUT_VOLTAGE,
    DEFAULT_POWERBANK_CAPACITY_MAH,
    DEFAULT_PHONE_CAPACITY_MAH,
)


def extract_features(
    cycle_df: pd.DataFrame,
    capacity_ah: float,
    powerbank_capacity_mah: int = DEFAULT_POWERBANK_CAPACITY_MAH,
    phone_capacity_mah: int = DEFAULT_PHONE_CAPACITY_MAH,
) -> dict:
    """
    방전 사이클 DataFrame → 스칼라 피처 딕셔너리

    Args:
        cycle_df: 컬럼 [Voltage_measured, Current_measured,
                         Temperature_measured, Time] 포함 DataFrame
        capacity_ah: 해당 사이클에서 측정된 실제 방전 용량 (Ah)
        powerbank_capacity_mah: 보조배터리 정격 용량
        phone_capacity_mah: 스마트폰 배터리 용량
    """
    v = cycle_df["Voltage_measured"].values.astype(float)
    i = np.abs(cycle_df["Current_measured"].values.astype(float))
    t = cycle_df["Time"].values.astype(float)
    temp = cycle_df["Temperature_measured"].values.astype(float)

    discharged_ah = integrate.trapezoid(i, t) / 3600
    energy_wh = integrate.trapezoid(v * i, t) / 3600

    # NASA 셀 → 스마트폰 수신 환경 변환 (5V 고정, 컨버터 손실 반영)
    smartphone_received_wh = energy_wh * CONVERTER_EFFICIENCY
    smartphone_received_mah = (smartphone_received_wh / OUTPUT_VOLTAGE) * 1000

    soh = float(capacity_ah) / NOMINAL_CAPACITY_AH
    powerbank_usable_mah = powerbank_capacity_mah * soh * CONVERTER_EFFICIENCY
    estimated_full_charges = powerbank_usable_mah / phone_capacity_mah

    return {
        "mean_voltage": float(np.mean(v)),
        "std_voltage": float(np.std(v)),
        "min_voltage": float(np.min(v)),
        "mean_current": float(np.mean(i)),
        "max_current": float(np.max(i)),
        "mean_temperature": float(np.mean(temp)),
        "max_temperature": float(np.max(temp)),
        "std_temperature": float(np.std(temp)),
        "discharge_duration_s": float(t[-1] - t[0]),
        "discharged_ah": float(discharged_ah),
        "energy_wh": float(energy_wh),
        "smartphone_received_wh": float(smartphone_received_wh),
        "smartphone_received_mah": float(smartphone_received_mah),
        "voltage_drop_rate": float((v[0] - v[-1]) / (t[-1] - t[0] + 1e-9)),
        "voltage_start": float(v[0]),
        "voltage_end": float(v[-1]),
        # 타겟 (학습용, 예측 시에는 불필요)
        "soh": soh,
        "estimated_full_charges": estimated_full_charges,
        "powerbank_usable_mah": powerbank_usable_mah,
    }