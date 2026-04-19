# BatteryInsight AI 서버 API 가이드라인

> 백엔드 · 프론트엔드 개발자용 입출력 명세서

---

## 목차

1. [개요](#1-개요)
2. [주요 변경 사항 (v2.0)](#2-주요-변경-사항-v20)
3. [서버 실행](#3-서버-실행)
4. [엔드포인트 목록](#4-엔드포인트-목록)
5. [POST /soh/predict](#5-post-sohpredict)
6. [POST /soh/predict/multi](#6-post-sohpredictmulti)
7. [GET /soh/health](#7-get-sohhealth)
8. [하위호환 정책](#8-하위호환-정책)
9. [연동 코드 예시](#9-연동-코드-예시)
10. [주의사항](#10-주의사항)

---

## 1. 개요

Android 앱이 수집한 보조배터리 충전 세션 데이터를 분석하여 SOH(State of Health, 배터리 수명)를 예측합니다.

내부적으로 NASA + CALCE 데이터셋으로 피팅한 **표준 SOH 열화 곡선**을 기준선으로 사용하고, 유저별로 누적된 세션 데이터에서 **에너지 전달량 감소 추세(기울기)**를 계산해 개인화된 SOH 값을 반환합니다.

| 항목 | 내용 |
|---|---|
| Base URL (로컬) | `http://127.0.0.1:8000` |
| Base URL (배포 후) | `http://{EC2_IP}:8000` |
| API 문서 (Swagger) | `http://127.0.0.1:8000/docs` |
| Content-Type | `application/json` |
| 인증 | 없음 (내부망 전용) |
| 예측 방식 | 표준 곡선 피팅 (지수 감쇠) + 유저 기울기 선형 회귀 |

---

## 2. 주요 변경 사항 (v2.0)

이전 버전(LSTM 기반)과 달라진 점입니다. **기존 클라이언트 코드는 하위호환 정책에 따라 거의 수정 없이 동작하지만**, 개인화 예측을 쓰려면 `/soh/predict/multi`로 전환해야 합니다.

### 추가된 엔드포인트
- `POST /soh/predict/multi` — 다중 세션을 받아 개인화 SOH 예측

### `/soh/predict` 동작 변경
- 단일 세션으로는 개인화 불가. 이제 **표준 곡선 fallback 값만 반환**합니다.
- 응답의 `confidence`는 항상 `"fallback"`, `sessions_used: 0`.
- 정확한 SOH를 얻으려면 `/soh/predict/multi`를 사용하세요.

### 응답에 추가된 필드
- `standard_soh_percentage` — 표준 곡선 기준 SOH
- `degradation_rate_ratio` — 유저 노화 속도 / 표준 노화 속도
- `sessions_used`, `sessions_total` — 필터 통과 / 전체 세션 수
- `confidence` — 예측 신뢰도 (`fallback` / `low` / `medium` / `high`)

### `/soh/health` 응답 변경
- 기존: `model_loaded`, `scaler_loaded`
- 현재: `curve_fit_loaded`, `standard_curve` (피팅 통계)

### 하위호환을 위해 유지한 것
- 요청의 `capacity_ah` 필드 — 받기는 하나 내부적으로 무시
- 응답의 `smartphone_received_mah` 필드 — 계산되지 않아 항상 `0.0` 반환
- 클라이언트가 이 필드들을 보내거나 파싱하고 있어도 에러 없이 동작

---

## 3. 서버 실행

### 사전 준비 (최초 1회)

```bash
# 1. 원시 데이터셋 준비
#    - ai/data/external/Li-ion_Battery_Aging_Datasets/cleaned_dataset/
#    - ai/data/external/CALCE_Battery_Research_Data/

# 2. canonical 중간 데이터 생성
python scripts/export_canonical_artifacts.py

# 3. 표준 SOH 열화 곡선 피팅
python scripts/fit_standard_curve.py --dataset-scope nasa_calce
#    → artifacts/curve_fit/standard_curve.json 생성
```

### 서버 기동

```bash
# ai/ 루트에서 실행
uvicorn soh_service.main:app --reload
```

Windows 환경은 `start_server.bat` 더블클릭으로 실행 가능합니다.

---

## 4. 엔드포인트 목록

| 메서드 | 경로 | 용도 |
|---|---|---|
| `POST` | `/soh/predict` | 단일 세션 → 표준 곡선 fallback 예측 |
| `POST` | `/soh/predict/multi` | 다중 세션 → 개인화 SOH 예측 (핵심) |
| `GET` | `/soh/health` | 서버 상태 및 표준 곡선 통계 확인 |

---

## 5. POST /soh/predict

**단일 충전 세션을 받아 SOH의 표준 곡선 기준값(fallback)을 반환합니다.**

이 엔드포인트는 앱 최초 사용자나 유효 세션이 쌓이기 전 상황을 위한 것입니다. 유저별 노화 속도는 반영되지 않으므로 어떤 유저가 호출하든 동일한 결과가 나옵니다. 개인화된 SOH가 필요하면 [`/soh/predict/multi`](#6-post-sohpredictmulti)를 사용하세요.

### 요청

```json
{
  "cycle_records": [
    {"voltage_mv": 5000, "current_ma": -1600, "temperature_c": 28.0, "elapsed_ms": 0},
    {"voltage_mv": 5000, "current_ma": -1580, "temperature_c": 28.5, "elapsed_ms": 60000},
    "... (최소 10개)"
  ],
  "powerbank_capacity_mah": 10000,
  "phone_capacity_mah": 4000
}
```

**필드 상세**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|:---:|---|---|
| `cycle_records` | Array | ✓ | - | 충전 시계열. 최소 10개 이상 |
| `└ voltage_mv` | float | ✓ | - | 전압 (mV). `EXTRA_VOLTAGE` |
| `└ current_ma` | float | ✓ | - | 전류 (mA). `CURRENT_NOW ÷ 1000`. 충전 시 양수 (부호 규약은 현재 단순 절댓값 처리) |
| `└ temperature_c` | float\|null | | `null` | 온도 (°C). `EXTRA_TEMPERATURE ÷ 10` |
| `└ elapsed_ms` | float | ✓ | - | 세션 시작 후 경과 시간 (ms) |
| `powerbank_capacity_mah` | int | | `10000` | 보조배터리 정격 용량 (mAh) |
| `phone_capacity_mah` | int | | `4000` | 스마트폰 배터리 용량 (mAh) |
| `capacity_ah` | float | | - | **[Deprecated]** 하위호환용. 전달되어도 무시됨 |

### 응답 (HTTP 200)

```json
{
  "soh_percentage": 84.23,
  "condition": "양호",
  "estimated_full_charges": 1.79,
  "powerbank_usable_mah": 7159.5,
  "mean_temperature_c": 28.3,
  "standard_soh_percentage": 84.23,
  "degradation_rate_ratio": 1.0,
  "sessions_used": 0,
  "sessions_total": 1,
  "confidence": "fallback",
  "smartphone_received_mah": 0.0
}
```

---

## 6. POST /soh/predict/multi

**다중 세션을 받아 유저별 노화 속도를 반영한 개인화 SOH를 반환합니다.** (핵심 엔드포인트)

### 내부 동작

1. 각 세션에서 전달 에너지(Wh)를 적분으로 계산
2. 다음 세션은 자동 제외됩니다:
   - 10분 미만
   - 전달 에너지 2Wh 미만
   - `start_battery_level_pct` 85% 초과
   - 채울 여지(100 - start_battery_level_pct) 15%p 미만
3. 필터 통과한 세션의 `delivered_wh / (100 - start_battery_level_pct)`를 "채울 여지 1%p 당 전달 에너지"로 정규화
4. 세션 순서(index) 대비 정규화값을 선형 회귀 → 유저 기울기
5. 표준 곡선 대비 상대 노화 속도 비율로 변환 → 개인화된 SOH 반환
6. 유효 세션이 3개 미만이면 표준 곡선 fallback으로 대체

### 요청

```json
{
  "sessions": [
    {
      "cycle_records": [
        {"voltage_mv": 5000, "current_ma": -1600, "temperature_c": 27.0, "elapsed_ms": 0},
        {"voltage_mv": 5000, "current_ma": -1580, "temperature_c": 27.5, "elapsed_ms": 60000},
        "..."
      ],
      "start_battery_level_pct": 20.0
    },
    {
      "cycle_records": ["..."],
      "start_battery_level_pct": 15.0
    }
  ],
  "powerbank_capacity_mah": 10000,
  "phone_capacity_mah": 4000
}
```

**필드 상세**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|:---:|---|---|
| `sessions` | Array | ✓ | - | 세션 목록 (오래된 것부터 시간 오름차순). 최근 20개까지만 사용 |
| `└ cycle_records` | Array | ✓ | - | 세션 내부 시계열. 최소 2개 이상. 형식은 `/predict`와 동일 |
| `└ start_battery_level_pct` | float | ✓ | - | 세션 시작 시점 스마트폰 배터리 잔량 (%, 0~100) |
| `powerbank_capacity_mah` | int | | `10000` | 보조배터리 정격 용량 (mAh) |
| `phone_capacity_mah` | int | | `4000` | 스마트폰 배터리 용량 (mAh) |

### 응답 (HTTP 200)

```json
{
  "soh_percentage": 82.15,
  "condition": "양호",
  "estimated_full_charges": 1.74,
  "powerbank_usable_mah": 6982.8,
  "mean_temperature_c": 28.6,
  "standard_soh_percentage": 84.23,
  "degradation_rate_ratio": 1.15,
  "sessions_used": 7,
  "sessions_total": 8,
  "confidence": "medium",
  "smartphone_received_mah": 0.0
}
```

**응답 필드 상세**

| 필드 | 타입 | 설명 |
|---|---|---|
| `soh_percentage` | float | 개인화 SOH (%). 유저 기울기 반영된 최종 예측값 |
| `condition` | string | SOH 구간별 상태 라벨 (아래 표 참조) |
| `estimated_full_charges` | float | 스마트폰 완충 가능 횟수 추정 |
| `powerbank_usable_mah` | float | 실사용 가능 용량 (mAh). 컨버터 효율 85% 반영 |
| `mean_temperature_c` | float\|null | 요청 세션들의 평균 온도 (°C) |
| `standard_soh_percentage` | float | 표준 곡선 기준 SOH (%). 개인화 전 참조값 |
| `degradation_rate_ratio` | float | 표준 대비 노화 속도 비율. `1.0` = 표준, `>1.0` = 더 빠른 노화, `<1.0` = 더 느린 노화. 범위 `[0.1, 10.0]` |
| `sessions_used` | int | 필터 통과한 유효 세션 수 |
| `sessions_total` | int | 요청에 포함된 전체 세션 수 |
| `confidence` | string | `fallback` / `low` / `medium` / `high` (아래 표 참조) |
| `smartphone_received_mah` | float | **[Deprecated]** 하위호환용. 항상 `0.0` |

**condition 기준표**

| condition | SOH 범위 | 권장 대응 | UI 색상 |
|---|---|---|---|
| `우수` | 90% 이상 | 정상 사용 | 초록 |
| `양호` | 80% 이상 ~ 90% 미만 | 정상 사용 | 파랑 |
| `주의` | 70% 이상 ~ 80% 미만 | 장거리 여행 시 보조 준비 | 주황 |
| `교체 권장` | 70% 미만 | 교체 권장 | 빨강 |

**confidence 기준표**

| confidence | 조건 | 권장 UI 표시 |
|---|---|---|
| `high` | 유효 세션 10개 이상 | SOH 값 그대로 제공 |
| `medium` | 유효 세션 5~9개 | SOH 값 제공 + "추후 더 정확해질 수 있습니다" 안내 |
| `low` | 유효 세션 1~4개 | SOH 값 제공 + "신뢰도 낮음. 몇 번 더 사용 후 재확인 권장" |
| `fallback` | 유효 세션 0개 또는 단일 세션 요청 | "표준 기준값" 표시 + "개인화 데이터가 부족합니다" 안내 |

**오류 응답**

| HTTP 상태 | 발생 조건 | 응답 예시 |
|---|---|---|
| `422` | 요청 형식 오류 / `cycle_records` 10개 미만 / `start_battery_level_pct` 범위 초과 | `{"detail": "..."}` |
| `500` | 서버 내부 오류 (표준 곡선 파일 누락 등) | `{"detail": "..."}` |

---

## 7. GET /soh/health

서버 기동 여부와 표준 곡선 로드 상태 및 피팅 통계를 반환합니다.

### 요청

```
GET /soh/health
```

### 응답 (HTTP 200)

```json
{
  "status": "ok",
  "curve_fit_loaded": true,
  "standard_curve": {
    "dataset_scope": "nasa_calce",
    "fit_point_count": 380,
    "cell_count": 12,
    "rmse": 0.0342,
    "mae": 0.0261
  }
}
```

**응답 필드 상세**

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | string | 서버 상태 |
| `curve_fit_loaded` | bool | 표준 곡선 JSON 로드 성공 여부 |
| `standard_curve.dataset_scope` | string | 피팅에 사용한 데이터셋 범위 |
| `standard_curve.fit_point_count` | int | 피팅에 사용된 (누적 에너지, SOH) 데이터 포인트 수 |
| `standard_curve.cell_count` | int | 피팅에 포함된 고유 셀 수 |
| `standard_curve.rmse` | float | 피팅 잔차의 RMSE |
| `standard_curve.mae` | float | 피팅 잔차의 MAE |

---

## 8. 하위호환 정책

기존 LSTM 기반 API 클라이언트가 코드 수정 없이 동작하도록 다음 규칙을 유지합니다.

### 요청 측

- **미지의 필드가 포함돼도 무시됩니다** (Pydantic `extra="ignore"`). 예를 들어 Android 앱이 요청 바디에 `capacity_ah`, `session_id`, `device_model` 같은 필드를 추가로 보내도 검증 에러가 발생하지 않습니다.
- `capacity_ah`는 이전 API의 필수 필드였으나 현재 구현에선 계산에 사용되지 않습니다. 있어도 없어도 결과는 동일합니다.

### 응답 측

- `smartphone_received_mah` 필드는 응답에 항상 포함되며 값은 `0.0`입니다. 기존 클라이언트가 이 필드를 참조하더라도 파싱 에러가 발생하지 않도록 유지합니다.
- 새 클라이언트는 이 필드 대신 `powerbank_usable_mah`와 `estimated_full_charges`를 사용하세요.

### 권장 마이그레이션 순서 (Android)

1. **즉시 적용 가능**: 서버만 교체하고 기존 Android 코드 그대로 두기 → 기존 `/soh/predict` 호출은 정상 동작 (단, fallback 값만 반환).
2. **단기**: 응답 모델에 `confidence`, `sessions_used`, `degradation_rate_ratio` 필드 추가. `confidence`가 `fallback`이면 UI에 안내 표시.
3. **중기**: 세션 경계 감지 로직 구현 + 로컬 DB에 세션 히스토리 누적 + `/soh/predict/multi` 호출로 전환.

---

## 9. 연동 코드 예시

### Android (Kotlin — Retrofit)

```kotlin
// ── 데이터 클래스 ─────────────────────────────────────────────────────────

data class CycleRecord(
    val voltage_mv: Float,
    val current_ma: Float,
    val temperature_c: Float?,  // nullable
    val elapsed_ms: Float
)

// 단일 세션 요청 (기존 API 호환)
data class PredictRequest(
    val cycle_records: List<CycleRecord>,
    val powerbank_capacity_mah: Int = 10000,
    val phone_capacity_mah: Int = 4000,
    val capacity_ah: Float? = null  // 선택, 보내도 무시됨
)

// 다중 세션 입력 단위
data class SessionInput(
    val cycle_records: List<CycleRecord>,
    val start_battery_level_pct: Float
)

// 다중 세션 요청
data class MultiSessionPredictRequest(
    val sessions: List<SessionInput>,
    val powerbank_capacity_mah: Int = 10000,
    val phone_capacity_mah: Int = 4000
)

// 응답 (단일/다중 공용)
data class PredictResponse(
    val soh_percentage: Float,
    val condition: String,
    val estimated_full_charges: Float,
    val powerbank_usable_mah: Float,
    val mean_temperature_c: Float?,
    val standard_soh_percentage: Float,
    val degradation_rate_ratio: Float,
    val sessions_used: Int,
    val sessions_total: Int,
    val confidence: String,  // fallback | low | medium | high
    val smartphone_received_mah: Float = 0f  // deprecated
)

// ── Retrofit 인터페이스 ──────────────────────────────────────────────────

interface SohApi {
    @POST("soh/predict")
    suspend fun predictSoh(@Body request: PredictRequest): PredictResponse

    @POST("soh/predict/multi")
    suspend fun predictSohMulti(@Body request: MultiSessionPredictRequest): PredictResponse

    @GET("soh/health")
    suspend fun health(): Map<String, Any>
}

// ── BatteryManager 수집 예시 ─────────────────────────────────────────────

// 단일 샘플 수집
val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).toFloat()
val currentMa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000f
val temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

// 세션 시작 시 배터리 잔량 저장
val startLevelPct = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0).toFloat()
```

> 에뮬레이터: `10.0.2.2:8000` / 실기기: `{PC_LAN_IP}:8000`

---

### 백엔드 (Python — requests)

```python
import requests

# 다중 세션 요청 예시
payload = {
    "sessions": [
        {
            "cycle_records": [
                {"voltage_mv": 5000, "current_ma": -1600, "temperature_c": 27.0, "elapsed_ms": 0},
                {"voltage_mv": 5000, "current_ma": -1580, "temperature_c": 27.5, "elapsed_ms": 60000},
                # ... (최소 2개)
            ],
            "start_battery_level_pct": 20.0
        },
        # ... (여러 세션)
    ],
    "powerbank_capacity_mah": 10000,
    "phone_capacity_mah": 4000
}

response = requests.post("http://127.0.0.1:8000/soh/predict/multi", json=payload)
result = response.json()

print(f"SOH: {result['soh_percentage']}% ({result['condition']})")
print(f"노화 속도: 표준 대비 {result['degradation_rate_ratio']}배")
print(f"유효 세션: {result['sessions_used']}/{result['sessions_total']}, 신뢰도: {result['confidence']}")
```

---

### curl (터미널 테스트)

단일 세션 (fallback 확인용):

```bash
curl -X POST http://127.0.0.1:8000/soh/predict \
  -H "Content-Type: application/json" \
  -d '{
    "cycle_records": [
      {"voltage_mv":5000,"current_ma":-1600,"temperature_c":28.0,"elapsed_ms":0},
      {"voltage_mv":5000,"current_ma":-1580,"temperature_c":28.2,"elapsed_ms":60000},
      {"voltage_mv":5000,"current_ma":-1560,"temperature_c":28.5,"elapsed_ms":120000},
      {"voltage_mv":5000,"current_ma":-1540,"temperature_c":29.0,"elapsed_ms":180000},
      {"voltage_mv":5000,"current_ma":-1520,"temperature_c":29.3,"elapsed_ms":240000},
      {"voltage_mv":5000,"current_ma":-1500,"temperature_c":29.5,"elapsed_ms":300000},
      {"voltage_mv":5000,"current_ma":-1480,"temperature_c":29.8,"elapsed_ms":360000},
      {"voltage_mv":5000,"current_ma":-1460,"temperature_c":30.0,"elapsed_ms":420000},
      {"voltage_mv":5000,"current_ma":-1440,"temperature_c":30.2,"elapsed_ms":480000},
      {"voltage_mv":5000,"current_ma":-1420,"temperature_c":30.5,"elapsed_ms":540000}
    ],
    "powerbank_capacity_mah":10000,
    "phone_capacity_mah":4000
  }'
```

다중 세션 (개인화 테스트용):

```bash
curl -X POST http://127.0.0.1:8000/soh/predict/multi \
  -H "Content-Type: application/json" \
  -d '{
    "sessions": [
      {
        "cycle_records": [
          {"voltage_mv":5000,"current_ma":-1600,"temperature_c":28,"elapsed_ms":0},
          {"voltage_mv":5000,"current_ma":-1580,"temperature_c":28,"elapsed_ms":600000},
          {"voltage_mv":5000,"current_ma":-1560,"temperature_c":28,"elapsed_ms":1200000}
        ],
        "start_battery_level_pct": 20.0
      }
    ],
    "powerbank_capacity_mah":10000,
    "phone_capacity_mah":4000
  }'
```

---

## 10. 주의사항

### 데이터 품질

- `cycle_records`는 `/predict`는 최소 10개, `/predict/multi`의 각 세션은 최소 2개 이상 필요합니다.
- 포인트가 많을수록 예측이 안정됩니다. 10분 이상 세션이 이상적입니다.
- `elapsed_ms`는 세션 내 단조 증가해야 합니다. 순서가 뒤섞이면 에너지 적분이 음수로 떨어집니다.
- `/predict/multi`는 **오래된 세션 → 최신 세션 순서**로 배열되어야 합니다. 순서가 반대면 기울기 부호가 뒤집힙니다.

### 개인화 예측의 필터 조건

`/predict/multi`에서 세션이 제외되는 조건은 다음과 같습니다. 클라이언트는 `sessions_used < sessions_total`인 경우 일부 세션이 제외됐음을 인지해야 합니다.

| 필터 | 임계값 |
|---|---|
| 세션 최소 길이 | 10분 (600초) |
| 세션 최소 전달 에너지 | 2 Wh |
| 시작 배터리 잔량 상한 | 85% |
| 채울 여지 하한 | 15%p |

### 서버 환경

- 서버 기동 시 `artifacts/curve_fit/standard_curve.json`을 메모리에 로드합니다. 파일이 없으면 서버가 기동되지 않습니다.
- 에뮬레이터에서는 `localhost` 대신 `10.0.2.2`를 사용하세요.
- 실기기 테스트 시 PC와 동일한 Wi-Fi에 연결 후 PC의 로컬 IP를 사용하세요.

### 예측 결과 해석

- `/predict`의 SOH는 표준 곡선의 중앙값 지점 값이라 유저 상태를 반영하지 않습니다. UI에서는 `confidence: fallback` 응답일 때 "기본 참조값" 임을 명시하세요.
- `degradation_rate_ratio`는 `[0.1, 10.0]` 범위로 클립됩니다. 극단값은 데이터 부족 또는 노이즈일 가능성이 높습니다.
- `smartphone_received_mah` 필드는 하위호환용이며 의미 있는 값을 반환하지 않습니다. 새 코드에서는 참조하지 마세요.

---

*BatteryInsight Android Project — AI 팀*
