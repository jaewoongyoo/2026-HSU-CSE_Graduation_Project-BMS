"""
가상 Android BatteryManager 데이터 생성 및 API 테스트
실행: python test_api.py
"""

import random
import json
import urllib.request
import urllib.error

API_URL = "http://127.0.0.1:8000/soh/predict"


def generate_discharge_cycle(
    soh_level: float = 1.0,
    duration_steps: int = 60,
) -> list[dict]:
    """
    Android BatteryManager 수집 데이터를 모사한 가상 방전 시계열 생성

    Args:
        soh_level: 배터리 노후도 (1.0=신품, 0.6=수명 종료 근접)
        duration_steps: 시계열 포인트 수
    """
    records = []

    voltage_start = 4150 + random.uniform(-30, 30)
    voltage_end   = 2700 + random.uniform(-50, 50)
    current_base  = -1950 * soh_level + random.uniform(-50, 50)
    temp_start    = 25.0 + random.uniform(-2, 2)

    for i in range(duration_steps):
        progress = i / (duration_steps - 1)

        # 전압: 초반 완만 → 후반 급강하 (방전 곡선)
        if progress < 0.8:
            voltage = voltage_start - (voltage_start - 3200) * (progress / 0.8)
        else:
            voltage = 3200 - (3200 - voltage_end) * ((progress - 0.8) / 0.2)
        voltage += random.uniform(-15, 15)

        # 전류: 초반 일정 → 후반 감소
        if progress < 0.85:
            current = current_base + random.uniform(-30, 30)
        else:
            current = current_base * (1 - (progress - 0.85) / 0.15 * 0.3)
            current += random.uniform(-20, 20)

        # 온도: 방전 중 서서히 상승
        temperature = temp_start + 10 * progress + random.uniform(-0.5, 0.5)

        elapsed_ms = int(progress * 3600 * 1000)

        records.append({
            "voltage_mv":    round(voltage, 1),
            "current_ma":    round(current, 1),
            "temperature_c": round(temperature, 2),
            "elapsed_ms":    elapsed_ms,
        })

    return records


def call_predict_api(payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    req  = urllib.request.Request(
        API_URL,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read())


def run_tests():
    scenarios = [
        {"label": "신품 배터리  (SOH ~100%)", "soh_level": 1.00, "capacity_ah": 2.00},
        {"label": "양호 배터리  (SOH ~85%)",  "soh_level": 0.85, "capacity_ah": 1.70},
        {"label": "주의 배터리  (SOH ~72%)",  "soh_level": 0.72, "capacity_ah": 1.44},
        {"label": "교체 권장    (SOH ~60%)",  "soh_level": 0.60, "capacity_ah": 1.20},
    ]

    print("=" * 55)
    print("  SOH Predict API 테스트")
    print("=" * 55)

    for s in scenarios:
        records = generate_discharge_cycle(soh_level=s["soh_level"])
        payload = {
            "cycle_records":        records,
            "capacity_ah":          s["capacity_ah"],
            "powerbank_capacity_mah": 10000,
            "phone_capacity_mah":     4000,
        }

        print(f"\n▶ {s['label']}")
        print(f"  포인트 수: {len(records)}개 | capacity_ah: {s['capacity_ah']}")

        try:
            result = call_predict_api(payload)
            print(f"  SOH:            {result['soh_percentage']}%")
            print(f"  상태:           {result['condition']}")
            print(f"  완충 가능 횟수:  {result['estimated_full_charges']}회")
            print(f"  사용 가능 용량:  {result['powerbank_usable_mah']} mAh")
            print(f"  평균 온도:       {result['mean_temperature_c']}°C")
        except urllib.error.URLError:
            print("  [오류] 서버에 연결할 수 없습니다. uvicorn이 실행 중인지 확인하세요.")
        except Exception as e:
            print(f"  [오류] {e}")

    print("\n" + "=" * 55)


if __name__ == "__main__":
    run_tests()