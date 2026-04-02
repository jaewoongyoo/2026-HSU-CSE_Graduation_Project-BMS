# BatteryInsight AI 서버 API 가이드라인

> 백엔드 · 프론트엔드 개발자용 입출력 명세서

---

## 목차

1. [개요](#1-개요)
2. [서버 실행](#2-서버-실행)
3. [엔드포인트 목록](#3-엔드포인트-목록)
4. [POST /soh/predict](#4-post-sohpredict)
5. [GET /soh/health](#5-get-sohhealth)
6. [연동 코드 예시](#6-연동-코드-예시)
7. [주의사항](#7-주의사항)

---

## 1. 개요

Android 앱이 수집한 보조배터리 방전 데이터를 분석하여 SOH(State of Health, 배터리 수명)를 예측합니다.

| 항목 | 내용 |
|---|---|
| Base URL (로컬) | `http://127.0.0.1:8000` |
| Base URL (배포 후) | `http://{EC2_IP}:8000` |
| API 문서 (Swagger) | `http://127.0.0.1:8000/docs` |
| Content-Type | `application/json` |
| 인증 | 없음 (내부망 전용) |

---

## 2. 서버 실행

```bash
# BatteryInsight_Android/ 루트에서 실행
uvicorn soh_service.main:app --reload
```

Windows 환경은 `start_server.bat` 더블클릭으로 실행 가능합니다.

---

## 3. 엔드포인트 목록

| 메서드 | 경로 | 용도 |
|---|---|---|
| `POST` | `/soh/predict` | SOH 예측 (핵심) |
| `GET` | `/soh/health` | 서버 상태 확인 |

---

## 4. POST /soh/predict

Android BatteryManager가 수집한 방전 사이클 시계열 데이터를 전송하면 SOH를 예측하여 반환합니다.

### 요청 (Request)

**헤더**

```
Content-Type: application/json
```

**바디**

```json
{
  "cycle_records": [
    {
      "voltage_mv":    4190,
      "current_ma":   -2000,
      "temperature_c":  25.3,
      "elapsed_ms":       0
    },
    {
      "voltage_mv":    3900,
      "current_ma":   -1980,
      "temperature_c":  27.1,
      "elapsed_ms":  500000
    }
  ],
  "capacity_ah":            1.8,
  "powerbank_capacity_mah": 10000,
  "phone_capacity_mah":     4000
}
```

**필드 상세**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|:---:|---|---|
| `cycle_records` | Array | ✓ | - | 방전 시계열. 최소 10개 이상 |
| `└ voltage_mv` | float | ✓ | - | 전압 (mV). `EXTRA_VOLTAGE` |
| `└ current_ma` | float | ✓ | - | 전류 (mA). 방전 시 음수. `CURRENT_NOW ÷ 1000` |
| `└ temperature_c` | float | ✓ | - | 온도 (°C). `EXTRA_TEMPERATURE ÷ 10` |
| `└ elapsed_ms` | float | ✓ | - | 세션 시작 후 경과 시간 (ms) |
| `capacity_ah` | float | ✓ | - | 이번 세션 총 방전 용량 (Ah) |
| `powerbank_capacity_mah` | int | | `10000` | 보조배터리 정격 용량 (mAh) |
| `phone_capacity_mah` | int | | `4000` | 스마트폰 배터리 용량 (mAh) |

**Android BatteryManager 단위 변환**

| API 필드 | Android 원본 | 변환식 |
|---|---|---|
| `voltage_mv` | `EXTRA_VOLTAGE` (mV) | 그대로 사용 |
| `current_ma` | `BATTERY_PROPERTY_CURRENT_NOW` (μA) | `÷ 1000` → mA |
| `temperature_c` | `EXTRA_TEMPERATURE` (0.1°C) | `÷ 10` → °C |
| `elapsed_ms` | `System.currentTimeMillis()` | 세션 시작 기준 차분 |

---

### 응답 (Response)

**HTTP 200**

```json
{
  "soh_percentage":          82.15,
  "condition":               "양호",
  "estimated_full_charges":   1.75,
  "powerbank_usable_mah":  6982.8,
  "smartphone_received_mah": 1153.2,
  "mean_temperature_c":       28.6
}
```

**응답 필드 상세**

| 필드 | 타입 | 설명 |
|---|---|---|
| `soh_percentage` | float | 예측 SOH (%). 100에 가까울수록 신품 |
| `condition` | string | 상태 라벨 (아래 표 참조) |
| `estimated_full_charges` | float | 스마트폰 완충 가능 횟수 |
| `powerbank_usable_mah` | float | 실사용 가능 용량 (mAh). 컨버터 손실 15% 반영 |
| `smartphone_received_mah` | float | 이번 세션 스마트폰 실수신 용량 (mAh) |
| `mean_temperature_c` | float | 이번 세션 평균 온도 (°C) |

**condition 기준표**

| condition | SOH 범위 | 권장 대응 | UI 색상 |
|---|---|---|---|
| `우수` | 90% 이상 | 정상 사용 | 초록 |
| `양호` | 80% 이상 ~ 90% 미만 | 정상 사용 | 파랑 |
| `주의` | 70% 이상 ~ 80% 미만 | 장거리 여행 시 보조 준비 | 주황 |
| `교체 권장` | 70% 미만 | 교체 권장 | 빨강 |

**오류 응답**

| HTTP 상태 | 발생 조건 | 응답 예시 |
|---|---|---|
| `422` | 요청 형식 오류 / 포인트 10개 미만 | `{"detail": "..."}` |
| `500` | 서버 내부 오류 | `{"detail": "Internal Server Error"}` |

---

## 5. GET /soh/health

서버 기동 여부 및 AI 모델 로드 상태를 확인합니다. 앱 시작 시 연결 체크용으로 활용합니다.

**요청**

```
GET /soh/health
```

**응답 (HTTP 200)**

```json
{
  "status":        "ok",
  "model_loaded":  true,
  "scaler_loaded": true
}
```

---

## 6. 연동 코드 예시

### Android (Kotlin — Retrofit)

```kotlin
// 데이터 클래스
data class CycleRecord(
    val voltage_mv:    Float,
    val current_ma:    Float,
    val temperature_c: Float,
    val elapsed_ms:    Float
)

data class PredictRequest(
    val cycle_records:          List<CycleRecord>,
    val capacity_ah:            Float,
    val powerbank_capacity_mah: Int = 10000,
    val phone_capacity_mah:     Int = 4000
)

data class PredictResponse(
    val soh_percentage:          Float,
    val condition:               String,
    val estimated_full_charges:  Float,
    val powerbank_usable_mah:    Float,
    val smartphone_received_mah: Float,
    val mean_temperature_c:      Float
)

// Retrofit 인터페이스
interface SohApi {
    @POST("soh/predict")
    suspend fun predictSoh(@Body request: PredictRequest): PredictResponse

    @GET("soh/health")
    suspend fun health(): Map<String, Any>
}

// BatteryManager 단위 변환 예시
val voltageMv    = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).toFloat()
val currentMa    = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000f
val temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
```

> 에뮬레이터: `10.0.2.2:8000` / 실기기: `{PC_LAN_IP}:8000`

---

### 백엔드 (Python — requests)

```python
import requests

payload = {
    "cycle_records": [
        {"voltage_mv": 4190, "current_ma": -2000, "temperature_c": 25.0, "elapsed_ms": 0},
        {"voltage_mv": 3900, "current_ma": -1980, "temperature_c": 27.5, "elapsed_ms": 1000000},
        # 최소 10개 이상
    ],
    "capacity_ah": 1.8,
    "powerbank_capacity_mah": 10000,
    "phone_capacity_mah": 4000
}

response = requests.post("http://127.0.0.1:8000/soh/predict", json=payload)
result = response.json()
print(result["soh_percentage"], result["condition"])
```

---

### curl (터미널 테스트)

```bash
curl -X POST http://127.0.0.1:8000/soh/predict \
  -H "Content-Type: application/json" \
  -d '{
    "cycle_records": [
      {"voltage_mv":4190,"current_ma":-2000,"temperature_c":25.0,"elapsed_ms":0},
      {"voltage_mv":3900,"current_ma":-1980,"temperature_c":27.5,"elapsed_ms":500000},
      {"voltage_mv":3700,"current_ma":-1950,"temperature_c":29.0,"elapsed_ms":1000000},
      {"voltage_mv":3500,"current_ma":-1900,"temperature_c":30.5,"elapsed_ms":1500000},
      {"voltage_mv":3300,"current_ma":-1850,"temperature_c":31.5,"elapsed_ms":2000000},
      {"voltage_mv":3100,"current_ma":-1800,"temperature_c":32.5,"elapsed_ms":2500000},
      {"voltage_mv":2900,"current_ma":-1750,"temperature_c":33.0,"elapsed_ms":3000000},
      {"voltage_mv":2750,"current_ma":-1700,"temperature_c":33.5,"elapsed_ms":3300000},
      {"voltage_mv":2650,"current_ma":-1650,"temperature_c":34.0,"elapsed_ms":3500000},
      {"voltage_mv":2600,"current_ma":-1600,"temperature_c":34.5,"elapsed_ms":3600000}
    ],
    "capacity_ah": 1.8,
    "powerbank_capacity_mah": 10000,
    "phone_capacity_mah": 4000
  }'
```

---

## 7. 주의사항

**데이터 품질**
- `cycle_records`는 최소 10개 이상이어야 합니다. 부족하면 422 오류가 반환됩니다.
- 포인트가 많을수록 (권장 30개 이상) 예측 정확도가 높아집니다.
- `elapsed_ms`는 세션 내 단조 증가해야 합니다. 순서가 뒤섞이면 적분 오류가 발생합니다.
- `current_ma`는 방전 중 반드시 음수여야 합니다. Android `CURRENT_NOW`는 μA 단위이므로 `÷ 1000` 변환이 필요합니다.

**서버 환경**
- 서버 기동 시 AI 모델을 메모리에 로드합니다. 첫 요청 응답이 약간 느릴 수 있습니다.
- 에뮬레이터에서는 `localhost` 대신 `10.0.2.2`를 사용하세요.
- 실기기 테스트 시 PC와 동일한 Wi-Fi에 연결 후 PC의 로컬 IP를 사용하세요.

**예측 결과 해석**
- SOH는 NASA Battery Dataset 기반 모델 예측값입니다. 실제 보조배터리와 ±5~10% 오차가 있을 수 있습니다.
- `estimated_full_charges`는 `phone_capacity_mah` 기준입니다. 기종이 다를 경우 요청에 해당 용량을 명시하세요.
- `powerbank_usable_mah`는 DC-DC 컨버터 효율 15% 손실이 반영된 값입니다.

---

*BatteryInsight Android Project — AI 팀*