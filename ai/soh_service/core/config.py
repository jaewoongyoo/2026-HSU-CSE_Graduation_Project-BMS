"""
전역 설정값 및 모델 경로 관리
"""

from pathlib import Path

AI_DIR = Path(__file__).resolve().parents[2]  # ai/

# 표준 SOH 열화 곡선 JSON 경로
STANDARD_CURVE_JSON_PATH = (
    AI_DIR / "artifacts" / "curve_fit" / "standard_curve.json"
)

# LSTM 체크포인트 (비교 모델용 — 중간고사 이후 재활성화)
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
N_MAX_SESSIONS = 20
