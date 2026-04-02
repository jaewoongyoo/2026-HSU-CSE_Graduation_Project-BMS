"""
전역 설정값 및 모델 경로 관리
"""

import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(BASE_DIR, "model")

MODEL_PATH = os.path.join(MODEL_DIR, "soh_model.pkl")
SCALER_PATH = os.path.join(MODEL_DIR, "soh_scaler.pkl")

# 배터리 물리 상수
NOMINAL_CAPACITY_AH = 2.0       # NASA 18650 셀 정격 용량
CONVERTER_EFFICIENCY = 0.85     # DC-DC 컨버터 효율
OUTPUT_VOLTAGE = 5.0            # 스마트폰 수신 전압 (V)

# 사용자 기기 기본값 (API 요청 시 오버라이드 가능)
DEFAULT_PHONE_CAPACITY_MAH = 4000
DEFAULT_POWERBANK_CAPACITY_MAH = 10000

FEATURE_COLS = [
    "mean_voltage", "std_voltage", "min_voltage",
    "mean_current", "max_current",
    "mean_temperature", "max_temperature", "std_temperature",
    "discharge_duration_s",
    "discharged_ah", "energy_wh",
    "smartphone_received_wh", "smartphone_received_mah",
    "voltage_drop_rate", "voltage_start", "voltage_end",
]