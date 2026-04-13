"""
전역 설정값 및 모델 경로 관리
"""

from pathlib import Path

AI_DIR = Path(__file__).resolve().parents[2]  # ai/

# 기본 체크포인트: NASA+CALCE V3, seed=1 (best val MAE 0.039)
LSTM_CHECKPOINT_DIR = (
    AI_DIR
    / "artifacts"
    / "checkpoints"
    / "lstm"
    / "nasa_calce"
    / "v3"
    / "nc_v3_s1_cosine_ep30"
)

# 배터리 물리 상수
CONVERTER_EFFICIENCY = 0.85     # DC-DC 컨버터 효율
OUTPUT_VOLTAGE = 5.0            # 스마트폰 수신 전압 (V)

# 사용자 기기 기본값 (API 요청 시 오버라이드 가능)
DEFAULT_PHONE_CAPACITY_MAH = 4000
DEFAULT_POWERBANK_CAPACITY_MAH = 10000

# Multi-session inference: 최근 N개 세션만 사용 (슬라이딩 윈도우)
# 너무 오래된 세션은 현재 SOH와 다른 상태를 반영할 수 있음
N_MAX_SESSIONS = 20
