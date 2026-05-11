"""
/soh/predict/multi 개인화 예측 동작 검증 스크립트 (v2).

이전 v1은 랜덤 노이즈만으로 세션을 생성해 기울기가 의미 없이 튀었다.
v2는 각 세션의 delivered_wh에 **의도적 시간 경과 열화 추세**를 주입한다:

- 세션 index가 커질수록 "채울 여지 1%p 당 전달 에너지"가 점점 감소
- 유저 프로필마다 이 감소 속도를 다르게 설정 → degradation_rate_ratio가 다르게 나와야 정상

프로필:
  heavy  : 세션당 1.5% 씩 에너지 감소 (빠른 노화)
  normal : 세션당 0.6% 감소 (표준 근처)
  light  : 세션당 0.15% 감소 (느린 노화)

각 유저 세션 12개를 보낸다 (medium 신뢰도 확보용).
"""

import json
import os
import random
import urllib.error
import urllib.request

_BASE_URL = os.environ.get("SOH_API_URL", "http://127.0.0.1:8000")
API_URL = f"{_BASE_URL}/soh/predict/multi"


def generate_charging_session(
    target_delivered_wh: float,
    start_battery_pct: float,
    session_duration_sec: float = 2400.0,
    sample_interval_sec: float = 30.0,
) -> list[dict]:
    """목표 전달 에너지(Wh)를 달성하도록 평균 전류를 역산해 세션 시계열 생성.

    Power = V * I, Energy = Power * duration
    → 평균 전류 = target_wh * 3600 / (duration * V)
    """
    voltage_mean = 5.0
    average_current_a = (target_delivered_wh * 3600) / (session_duration_sec * voltage_mean)
    average_current_ma = average_current_a * 1000

    n_samples = max(2, int(session_duration_sec / sample_interval_sec))
    records = []
    for i in range(n_samples):
        progress = i / (n_samples - 1)
        elapsed_ms = progress * session_duration_sec * 1000

        # 충전 곡선: 초반 일정 전류, 후반 Taper
        if progress < 0.75:
            # CC 구간: 평균을 유지하면서 약한 노이즈
            current_ma = average_current_ma * 1.1 + random.uniform(-30, 30)
        else:
            # CV 구간: 전류가 감소
            taper = (progress - 0.75) / 0.25
            current_ma = average_current_ma * 1.1 * (1.0 - 0.6 * taper) + random.uniform(-20, 20)

        voltage_mv = 5000 + random.uniform(-30, 30)
        temperature_c = 27.0 + progress * 3.5 + random.uniform(-0.3, 0.3)

        records.append({
            "voltage_mv": round(voltage_mv, 1),
            "current_ma": round(current_ma, 1),
            "temperature_c": round(temperature_c, 2),
            "elapsed_ms": round(elapsed_ms, 1),
        })
    return records


def build_user_sessions(profile: str, n_sessions: int = 12) -> list[dict]:
    """유저 프로필별로 세션 생성.

    세션 index가 늘어날수록 delivered_wh_per_pct가 감소하도록 구성.
    """
    # 프로필별 파라미터
    if profile == "heavy":
        # 빈번한 깊은 충전, 노화 빠름
        start_pct_range = (8, 18)
        initial_wh_per_pct = 0.13   # 처음엔 채울 여지 1%p당 0.13Wh
        decay_per_session = 0.015   # 세션당 1.5% 감소
        duration_sec = 3600
    elif profile == "normal":
        start_pct_range = (28, 38)
        initial_wh_per_pct = 0.11
        decay_per_session = 0.006
        duration_sec = 2400
    elif profile == "light":
        start_pct_range = (40, 55)
        initial_wh_per_pct = 0.09
        decay_per_session = 0.0015
        duration_sec = 1500
    else:
        raise ValueError(f"Unknown profile: {profile}")

    sessions = []
    for session_idx in range(n_sessions):
        start_pct = random.uniform(*start_pct_range)
        fillable_pct = 100.0 - start_pct

        # 시간 경과 열화: 세션이 진행될수록 wh_per_pct 감소
        decay_factor = max(0.5, 1.0 - decay_per_session * session_idx)
        current_wh_per_pct = initial_wh_per_pct * decay_factor
        target_wh = current_wh_per_pct * fillable_pct
        # 약한 랜덤 노이즈
        target_wh *= random.uniform(0.97, 1.03)

        records = generate_charging_session(
            target_delivered_wh=target_wh,
            start_battery_pct=start_pct,
            session_duration_sec=duration_sec,
        )
        sessions.append({
            "cycle_records": records,
            "start_battery_level_pct": round(start_pct, 2),
        })
    return sessions


def call_multi_api(payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        API_URL,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read())


def run_tests():
    random.seed(42)  # 재현 가능한 결과

    scenarios = [
        {"label": "헤비 유저  (빠른 노화, 세션당 1.5% 감소)", "profile": "heavy"},
        {"label": "일반 유저  (표준 근처, 세션당 0.6% 감소)", "profile": "normal"},
        {"label": "라이트 유저 (느린 노화, 세션당 0.15% 감소)", "profile": "light"},
    ]

    print("=" * 72)
    print("  SOH Predict Multi API 개인화 동작 검증 (v2)")
    print("=" * 72)

    for s in scenarios:
        sessions = build_user_sessions(s["profile"], n_sessions=12)
        payload = {
            "sessions": sessions,
            "powerbank_capacity_mah": 10000,
            "phone_capacity_mah": 4000,
        }

        print(f"\n▶ {s['label']}")
        print(f"  세션 수: {len(sessions)}개")

        try:
            result = call_multi_api(payload)
            print(f"  SOH:                      {result['soh_percentage']}%")
            print(f"  표준 곡선 SOH:             {result['standard_soh_percentage']}%")
            print(f"  노화 속도 비율:            {result['degradation_rate_ratio']} (1.0 = 표준)")
            print(f"  상태:                      {result['condition']}")
            print(f"  완충 가능 횟수:            {result['estimated_full_charges']}회")
            print(f"  사용 가능 용량:            {result['powerbank_usable_mah']} mAh")
            print(f"  사용된 세션 / 전체:        {result['sessions_used']} / {result['sessions_total']}")
            print(f"  신뢰도:                    {result['confidence']}")
            print(f"  평균 온도:                 {result['mean_temperature_c']}°C")
        except urllib.error.HTTPError as exc:
            print(f"  [HTTP {exc.code}] {exc.read().decode('utf-8', errors='ignore')}")
        except urllib.error.URLError:
            print("  [오류] 서버에 연결할 수 없습니다. uvicorn이 실행 중인지 확인하세요.")
        except Exception as exc:
            print(f"  [오류] {type(exc).__name__}: {exc}")

    print("\n" + "=" * 72)


if __name__ == "__main__":
    run_tests()
