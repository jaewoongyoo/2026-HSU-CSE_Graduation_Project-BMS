# BACKEND Detailed Report — BatteryInsight

작성일: 2026-06-15

이 문서는 `backend` 폴더의 핵심 기능, 데이터 흐름, AI 연동 구조, 발표용 PPT 주요내용을 정리한 리포트입니다.

---

## 요약
- 프로젝트: BatteryInsight 백엔드( FastAPI )
- 위치: `backend/`
- 주요 역할: 텔레메트리 수집 → 세션 관리 → AI SOH 예측 연계 → 결과 저장 및 공유
- 포함: 핵심 기능 설명, 백엔드 구조, 데이터 처리 로직, 재현/운영 참고, PPT 주요내용

---

## 코드 대조 후 보정/보강 요약

실제 백엔드 코드를 다시 확인한 결과, 문서와 발표에서 특히 정확히 짚어야 할 구현 포인트는 다음과 같습니다.

- `POST /api/v1/community/{device_id}/share`는 경로의 `device_id`와 body의 `device_id`가 모두 존재합니다. 경로/본문 값이 다르면 다른 기기를 공유할 수 있으므로, 코드에 불일치 검증을 추가했습니다.
- `SessionFinishRequest.powerbank_capacity_start_mah` / `powerbank_capacity_end_mah`는 이름은 `mah`지만, 현재 스키마 설명과 검증 범위상 실제 의미는 **세션 시작/종료 배터리 잔량 퍼센트(0~100)** 입니다. DB 컬럼명은 과거 호환을 위해 유지됩니다.
- `RawUploadRequest`는 `data_points` 최소 1개뿐 아니라 `elapsed_ms`가 단조 증가해야 한다는 Pydantic validator를 갖습니다. 즉, 백엔드는 raw 데이터 저장 전에 시간 순서 품질도 1차 검증합니다.
- AI SOH fallback은 “AI 서버가 실패할 때마다”가 아니라 **유효 세션이 0개라 AI 서버 `minItems=1` 조건을 만족하지 못하는 경우**에만 백엔드에서 즉시 생성합니다. AI 서버 네트워크/HTTP 실패는 `AIServiceException`으로 전파됩니다.
- 커뮤니티 필터는 같은 필드 안에서는 OR 조건(`in_`)이고, 서로 다른 필터 묶음은 순차적으로 `filter()`가 적용되어 AND에 가깝게 동작합니다.
- `MIN_VALID_SESSION_COUNT = 3` 상수는 남아 있지만, 최신 커밋 흐름에 따라 실제 3개 미만 세션 차단은 제거되어 있습니다. 현재는 유효 세션이 1~2개라도 AI 서버에 요청하고, 0개일 때만 fallback 처리합니다.

---

## 핵심 기능 설명

### 1. 사용자 인증 (Authentication)
- **회원가입 / 로그인**: `app/api/v1/auth.py` → `app/services/auth_service.py`
- Bcrypt 기반 비밀번호 해싱 (`app/core/security.py`)
- 사용자명 중복 검사 및 암호 검증
- 응답: user_id, username, phone_model, phone_uid

### 2. 디바이스 관리 (Device Management)
- **디바이스 생성**: `POST /api/v1/devices`
  - 사용자 ID, 모델명, 제조사, 배터리 용량(mAh) 저장
  - `app/services/device_service.py` → `app/repositories/postgres_repo.py`의 `create_device`
- **디바이스 조회/삭제**:
  - `GET /api/v1/devices/all`: 전체 디바이스 목록 조회
  - `GET /api/v1/devices/{user_id}`: 사용자 ID 또는 username 기준 디바이스 목록 조회
  - `DELETE /api/v1/devices/{id}`: 디바이스와 관련 세션/텔레메트리/AI 결과/공유 보고서 삭제
- **최신 결과 조회**: `GET /api/v1/devices/{device_id}/latest-result` — 해당 디바이스의 최신 완료 세션 결과

### 3. 세션 관리 (Session Lifecycle)
- **세션 시작**: `POST /api/v1/sessions/start`
  - 기존 device를 기준으로 `battery_sessions` 테이블에 `in_progress` 상태의 세션 생성
  - 응답: session_id(=내부적으로는 BatterySession.id)
- **원시 데이터 업로드**: `POST /api/v1/sessions/{session_id}/raw`
  - timestamp, battery_level(SOC), voltage_mv, current_ma, elapsed_ms 등 포인트 수집
  - `battery_telemetry` 테이블에 저장
  - `raw_ingest_service`에서 세션 상태(`in_progress`) 검증
  - `RawUploadRequest`에서 `elapsed_ms` 단조 증가 검증
- **세션 종료**: `POST /api/v1/sessions/{session_id}/finish`
  - 세션 상태를 `finished`로 전환
  - session_start_ts, session_end_ts, capacity_ah, 시작/종료 SOC 퍼센트 저장
  - 시작/종료 SOC는 `powerbank_capacity_start_mah`, `powerbank_capacity_end_mah` 컬럼에 저장되지만 실제 의미는 mAh가 아니라 퍼센트 값
  - `session_service.finish_session_service`에서 용량 부족 경고(로깅)
- **결과 조회**: `GET /api/v1/sessions/{session_id}/result`
  - SOH%, condition, estimated_full_charges 등 반환

### 4. AI SOH 예측 (Artificial Intelligence Inference)
- **예측 트리거**: `POST /api/v1/sessions/{session_id}/predict-soh`
  - 해당 세션이 `finished` 상태여야 함
  - `ai_service.predict_soh_for_session` 호출
- **전처리 파이프라인**:
  1. 동일 디바이스의 최근 완료 세션들 수집 (최대 20개)
  2. 각 세션에 대해:
     - 원시 포인트 정렬 (`_sorted_points`)
     - 10분 윈도우 집계 (`aggregate_by_10min`) → cycle_records (voltage, current, temperature 평균)
     - 전달 에너지 계산 (`_calculate_delivered_wh`) — trapezoidal 적분으로 Wh 산출
  3. 세션 유효성 검사 (`_get_session_reject_reason`):
     - raw 포인트 최소 2개 이상
     - 시작 SOC 필수
     - 최소 duration(5분 기본, 빠른 충전은 8분)
     - 시작 SOC > 85% 제외
     - fillable gap >= 15%
     - cycle_records 최소 2개 이상
  4. 유효한 세션들만 모아 `payload` 구성 (sessions 배열)
- **AI 호출**:
  - AI 서버 `POST /soh/predict/multi` 엔드포인트로 payload 전달
  - 응답: soh_percentage, condition, estimated_full_charges, powerbank_usable_mah, mean_temperature_c, confidence 등
- **Fallback 동작**:
  - 유효한 세션이 0개인 경우, AI 호출 대신 백엔드에서 fallback 결과 생성
  - soh_percentage=100%, condition='우수', confidence='fallback'
  - DB에 저장하여 서비스 가용성 확보
  - AI 서버 네트워크/HTTP 오류는 fallback으로 바꾸지 않고 `AIServiceException`으로 처리
- **결과 저장**:
  - `battery_ai_results` 테이블: soh_percentage, condition, estimated_full_charges 등 개별 컬럼
  - `soh_analysis` 테이블: current_soh, grade, recommendation(원본 JSON)

### 5. 커뮤니티 공유 (Community Sharing)
- **공유 생성**: `POST /api/v1/community/{device_id}/share`
  - `shared_reports` 테이블에 share_token(UUID) 생성
  - is_public 플래그로 공개 여부 결정
- **공개 피드 조회**: `GET /api/v1/community`
  - 공개된 모든 공유 보고서 목록(페이지네이션: limit, offset)
  - 각 카드: 사용자명, 폰 모델, 디바이스 정보, SOH, 세션 통계 포함
- **필터링**: `POST /api/v1/community/filter`
  - phone_models, manufacturers, capacities, soh_min/max 필터
  - 같은 필드 내부는 OR 조건, 서로 다른 필터 묶음은 AND 방식으로 누적 적용
- **SOH 이력 조회**: `GET /api/v1/community/{shared_report_id}/soh-history`
  - 공유된 디바이스의 과거 SOH 추세 그래프 데이터
  - 시간순 정렬된 points 배열(measured_at, soh_percentage)
- **필터 옵션 조회**: `GET /api/v1/community/filter-options`
  - 공개된 디바이스 중 사용 가능한 폰 모델, 제조사, 용량 목록
- **공유 상태 업데이트/삭제**: `PATCH`, `DELETE /api/v1/community/{shared_report_id}`
- **공유 생성 주의점**:
  - route path의 `{device_id}`와 body의 `device_id`가 일치해야 함
  - 불일치 시 `400 Bad Request` 반환하도록 코드 보완

### 6. 헬스 체크 (Health Check)
- **백엔드 상태**: `GET /api/v1/health` → `{"status": "ok"}`
- **AI 서버 상태**: `GET /api/v1/ai/health` → AI 서버의 `/soh/health` 응답 프록시

### 7. 데이터 저장 및 조회
- **데이터 모델** (주요 테이블):
  - `users`: id, username, password_hash, phone_model, phone_uid, created_at
  - `devices`: id, user_id, manufacturer, model_name, powerbank_capacity_mah, created_at
  - `battery_sessions`: id, device_id, user_id, status, capacity_ah, session_start_ts, session_end_ts, ...
  - `battery_telemetry`: id, device_id, session_id, timestamp, soc, voltage, current_ma, temperature_c, elapsed_ms
  - `battery_ai_results`: id, session_id, soh_percentage, condition, estimated_full_charges, powerbank_usable_mah, ...
  - `soh_analysis`: id, device_id, session_id, current_soh, grade, recommendation(JSON)
  - `shared_reports`: id, device_id, share_token, is_public, created_at
- **마이그레이션 관리**: Alembic 기반 스키마 버전 관리 (`backend/alembic/versions/`)

---

## 백엔드 레이어별 책임 구조

현재 백엔드는 FastAPI 라우터, 서비스, 저장소, ORM 모델이 비교적 명확히 분리되어 있습니다.

```text
Android App / Swagger Client
        ↓ HTTP
app/api/v1/*
        ↓ 요청 검증 후 서비스 호출
app/services/*
        ↓ 비즈니스 규칙, 세션 상태 검증, AI payload 조립
app/repositories/*
        ↓ SQLAlchemy query / transaction
app/db/models.py
        ↓
PostgreSQL

외부 연동:
app/services/ai_service.py ──HTTP──> AI Server (/soh/health, /soh/predict/multi)
```

| 레이어 | 대표 파일 | 주요 책임 | 발표에서 강조할 점 |
|---|---|---|---|
| API Router | `app/api/v1/session.py`, `upload.py`, `result.py`, `community.py` | HTTP 경로, request/response schema, status 변환 | 화면 기능이 아니라 진단 생명주기 기준으로 API가 나뉨 |
| Service | `session_service.py`, `raw_ingest_service.py`, `ai_service.py`, `community_service.py` | 세션 상태 검증, raw 저장 흐름, AI 전처리, 커뮤니티 카드 구성 | 실제 비즈니스 판단이 모여 있는 핵심 계층 |
| Repository | `postgres_repo.py` | CRUD, 통계 query, 트랜잭션 commit/rollback | DB 예외를 프로젝트 예외로 변환해 상위 계층을 단순화 |
| ORM Model | `db/models.py` | users/devices/sessions/telemetry/AI result/share schema | 세션 중심으로 raw와 AI 결과를 연결 |
| Migration | `alembic/versions/*` | 스키마 변경 추적 | 기능 확장 이력을 재현 가능하게 관리 |

---

## 주요 API 그룹

발표에서는 엔드포인트를 모두 나열하기보다 기능 그룹만 보여주는 것이 좋습니다.

| 그룹 | 대표 API | 역할 |
|---|---|---|
| 인증/사용자 | `/api/v1/auth/*`, `/api/v1/users/*` | 회원가입, 로그인, 사용자 정보 관리 |
| 디바이스 | `/api/v1/devices/*` | 사용자별 배터리/보조배터리 기기 등록, 조회, 삭제 |
| 세션/Raw | `/api/v1/sessions/start`, `/raw`, `/finish` | 진단 세션 시작, raw telemetry 업로드, 세션 종료 |
| AI 결과 | `/predict-soh`, `/result`, `/latest-result` | SOH 예측 요청, 세션별/기기별 결과 조회 |
| 커뮤니티 | `/api/v1/community/*` | 공유 보고서, 공개 피드, SOH 이력, 필터 옵션 제공 |
| 헬스 체크 | `/api/v1/health`, `/api/v1/ai/health` | 백엔드 및 AI 서버 상태 확인 |

---

## 핵심 데이터 흐름 요약

### 진단 데이터 흐름

```text
1. 사용자가 앱에서 기기 등록
2. 앱이 세션 시작 API 호출
3. 앱이 배터리 raw telemetry를 일정 단위로 업로드
4. 백엔드가 세션 상태와 elapsed_ms 순서를 검증한 뒤 DB 저장
5. 앱이 세션 종료 API 호출
6. 백엔드가 시작/종료 SOC, capacity_ah, 종료 시각을 저장
7. predict-soh 호출 시 최근 완료 세션 최대 20개를 조회
8. raw point 정렬 → 10분 집계 → 세션 유효성 검사 → delivered_wh 계산
9. 유효 세션 1개 이상이면 AI 서버에 multi-session payload 전달
10. 유효 세션 0개면 백엔드 fallback 결과 저장
11. 결과는 battery_ai_results와 soh_analysis에 함께 저장
12. 앱/커뮤니티 API가 최신 결과와 이력을 조회
```

### 발표에서 쓰기 좋은 핵심 문장

- “이 백엔드는 세션을 기준으로 raw 데이터, AI 결과, 커뮤니티 공유 데이터를 연결한다.”
- “AI 예측 자체는 외부 서버가 수행하지만, 예측 가능한 입력으로 데이터를 정리하는 핵심 책임은 백엔드에 있다.”
- “최근 개선의 방향은 기능 추가보다 raw 데이터 신뢰도, 예외 방어, 사용자 흐름 유지에 맞춰져 있다.”

---

## 데이터 처리 로직 (상세)

### 1. Raw 포인트 입력 처리
**입력 정의** (`app/schemas/raw.py`):
```python
class RawUploadRequest:
    data_points: list[BatteryPoint]  # BatteryPoint = timestamp, battery_level, voltage_mv, current_ma, elapsed_ms, ...
```

**처리 흐름** (`app/services/raw_ingest_service.py`):
1. 세션 존재 여부 검증 → 없으면 `SessionNotFoundException`
2. 세션 상태 검증 → `in_progress`만 허용 (이미 `finished`인 세션에는 추가 업로드 불가)
3. data_points 배열이 비어 있지 않은지 확인
4. Pydantic validator에서 `elapsed_ms`가 단조 증가하는지 확인
5. `save_raw_points(db, session_id, device_id, data_points)` 호출:
   - 각 포인트를 `BatteryTelemetry` 모델로 변환
   - `voltage_mv` → Volt로 정규화 (1000으로 나눔)
   - DB 일괄 저장 (`db.add_all`)
   - 저장 건수 반환

**저장 데이터** (`BatteryTelemetry` 컬럼):
- device_id, session_id: 외래키
- timestamp: UTC 시간
- soc: State of Charge (%), battery_level에서 매핑
- voltage, current_ma, temperature_c, elapsed_ms: raw 입력값
- battery_status: 추가 필드(현재 대부분 null)

---

### 2. 10분 집계 (Aggregation by Window)
**목적**: raw 포인트(고해상도, 100ms 단위)를 10분 윈도우로 평균화하여 노이즈 감소 및 AI 입력 크기 정규화

**알고리즘** (`ai_service.aggregate_by_10min(raw_points)`):
```
1. 정렬된 포인트 필터링: elapsed_ms가 None이 아닌 포인트만 추출
2. elapsed_ms를 기준으로 윈도우 ID 계산: window_id = int(elapsed_ms // 600000)
   - 600000ms = 10분
3. groupby(window_id)로 같은 윈도우 내 포인트들 그룹화
4. 각 윈도우별:
   - voltage 평균값 계산 (None 제외)
   - current 평균값 계산 (None 제외)
   - temperature 평균값 계산 (None 제외)
   - 마지막 포인트의 elapsed_ms 사용
5. 결과: cycle_records = [{voltage_mv, current_ma, temperature_c, elapsed_ms}, ...]
```

**예시**:
- elapsed_ms = 0 ~ 599999: window 0
- elapsed_ms = 600000 ~ 1199999: window 1
- 각 윈도우에서 voltage의 평균, current의 평균 산출

---

### 3. 전달 에너지 계산 (Delivered Wh)
**목적**: 세션 동안 배터리에서 실제로 전달된 에너지(Wh)를 계산 → AI 입력값으로 사용

**알고리즘** (`ai_service._calculate_delivered_wh(raw_points)`):
```
1. 정렬된 포인트 중 elapsed_ms, voltage, current_ma가 모두 있는 포인트만 선택
2. Trapezoidal 적분 방식:
   for (previous, current) in zip(sorted_points[:-1], sorted_points[1:]):
       delta_s = (current.elapsed_ms - previous.elapsed_ms) / 1000.0  # 초 단위
       if delta_s <= 0: continue  # 시간 역행 스킵
       
       previous_power_w = previous.voltage * abs(previous.current_ma) / 1000.0
       current_power_w = current.voltage * abs(current.current_ma) / 1000.0
       
       avg_power_w = (previous_power_w + current_power_w) / 2.0
       total_ws += avg_power_w * delta_s  # 에너지(Ws) 누적
3. total_ws / 3600.0 = Wh 변환
```

**물리 의미**:
- voltage(V) × current(A) = power(W)
- power(W) × time(s) = energy(Ws)
- Ws / 3600 = Wh
- Trapezoidal 적분으로 구간별 변화를 부드럽게 반영

**예시**:
- 10분(600s) 동안 평균 5V × 1A = 5W → 5W × 600s = 3000Ws = 0.833Wh

---

### 4. 세션 유효성 필터링
**목적**: 신뢰할 수 없는(짧거나, 불완전한) 세션을 AI 입력에서 제외

**검증 로직** (`ai_service._get_session_reject_reason(raw_points, cycle_records)`):

| 항목 | 조건 | 이유 |
|------|------|------|
| raw_points 수 | >= 2 | 최소 2개 포인트 필요 |
| 시작 SOC | not None | 초기 상태 필수 |
| duration | >= 5분 (5 × 60 × 1000 ms) | 최소 수집 기간 |
| 개인화 duration | 일반: >= 15분 / 빠른충전: >= 8분 | 충전 패턴 학습 충분시간 |
| 시작 SOC 범위 | <= 85% | 너무 높은 상태에서 시작하면 변화폭 작음 |
| fillable gap | >= 15% | 충전 가능 여유(100% - start_soc) |
| cycle_records | >= 2 | 집계된 윈도우 최소 2개 |

**fast-charge 판정** (`ai_service._is_fast_charging(raw_points)`):
- current_ma의 절대값들 중 중앙값(median) >= 2000 mA → fast-charge로 판정
- fast-charge일 경우 개인화 duration 기준을 8분으로 완화

**반환값**: 
- None: 유효한 세션
- "too_few_raw_points", "missing_start_battery_level", "duration_too_short" 등: 거부 사유

---

### 5. AI 입력 페이로드 구성
**다중 세션 전략** (`ai_service._build_multi_session_payload(db, session)`):

```python
# 1. 동일 device의 최근 완료 세션 최대 20개 수집
recent_sessions = get_recent_finished_sessions_for_device(db, session.device_id, limit=20)

# 2. 역순(가장 오래된 것부터)으로 처리하여 최신 세션을 마지막에 배치
valid_sessions = []
for recent_session in reversed(recent_sessions):
    raw_points = get_session_raw_points(db, recent_session.id)
    
    # 3. 각 세션 전처리
    cycle_records = aggregate_by_10min(raw_points)
    delivered_wh = _calculate_delivered_wh(raw_points)
    start_battery_level = _get_start_battery_level_pct(raw_points)
    
    # 4. 유효성 검사
    reject_reason = _get_session_reject_reason(raw_points, cycle_records)
    if reject_reason is None:
        valid_sessions.append({
            "cycle_records": cycle_records,
            "start_battery_level_pct": start_battery_level,
            "delivered_wh": delivered_wh,  # 고해상도 적분값
        })

# 5. 페이로드 구성
payload = {
    "sessions": valid_sessions,
    "powerbank_capacity_mah": device.powerbank_capacity_mah or 10000,  # 기본값 10000mAh
    "phone_capacity_mah": 4000,  # 고정값
}
```

**key point**: 
- 여러 세션 데이터를 AI에 함께 전달해 개인화 추정 지원
- cycle_records(10분 집계)와 delivered_wh(raw 기반 고해상도)를 함께 전달 — 집계 데이터로 AI 입력, delivered_wh로 기울기 정확도 향상

---

### 6. AI 서버 호출 및 Fallback
**정상 케이스** (`ai_service.predict_soh_for_session`):
```python
payload, sessions_total = _build_multi_session_payload(db, session)

if not payload["sessions"]:
    # Fallback: 유효 세션 0개
    result = {
        "soh_percentage": 100.0,
        "condition": "우수",
        "estimated_full_charges": capacity_mah * 0.85 / 4000,  # 효율 85%
        "powerbank_usable_mah": capacity_mah * 0.85,
        "confidence": "fallback",
        "sessions_used": 0,
        "sessions_total": sessions_total,
    }
    save_ai_result(db, session_id, device_id, result)
    return result

# 정상: AI 서버 호출
result = _request_ai_multi_prediction(payload)  # POST /soh/predict/multi
result["sessions_total"] = sessions_total
result["sessions_used"] = len(payload["sessions"])
save_ai_result(db, session_id, device_id, result)
return result
```

**Fallback 목적**:
- 유효 세션이 0개인 데이터 부족 상황에서 AI 서버 스펙 오류를 사전에 방지
- 사용자 화면이 데이터 부족 에러로 끊기지 않도록 안전한 기본값(soh=100%) 반환
- `confidence: fallback` 태그로 분석 시 필터링 가능

**주의**:
- AI 서버 네트워크 오류, timeout, 4xx/5xx 응답은 현재 fallback으로 삼키지 않고 `AIServiceException`으로 변환됩니다.
- 따라서 발표에서는 fallback을 “외부 서버 장애 대응”이 아니라 “유효 입력 0개 대응”으로 설명하는 것이 정확합니다.

---

### 7. 결과 저장 및 매핑
**저장 테이블** (`app/repositories/postgres_repo.py::save_ai_result`):

1. **battery_ai_results**:
   ```python
   BatteryAiResult(
       session_id=session_id,
       soh_percentage=result["soh_percentage"],
       condition=result["condition"],
       estimated_full_charges=result["estimated_full_charges"],
       powerbank_usable_mah=result["powerbank_usable_mah"],
       mean_temperature_c=result["mean_temperature_c"],
       standard_soh_percentage=result.get("standard_soh_percentage"),
       degradation_rate_ratio=result.get("degradation_rate_ratio"),
       sessions_used=result["sessions_used"],
       sessions_total=result["sessions_total"],
       confidence=result.get("confidence"),
   )
   ```

2. **soh_analysis**:
   ```python
   SohAnalysis(
       device_id=device_id,
       session_id=session_id,
       current_soh=result["soh_percentage"],
       grade=result["condition"],
       recommendation=json.dumps(result, ensure_ascii=False),  # 원본 JSON
   )
   ```

**필드 매핑**:
- soh_percentage → current_soh (percent)
- condition → grade (문자열: 우수/양호/보통/주의/위험)
- delivered_wh → 저장 안 함 (계산 과정에만 사용)
- 원본 JSON → recommendation 컬럼 (향후 상세 분석용)

---

### 8. 커뮤니티 카드 데이터 조회
**세션 통계 계산** (`app/repositories/postgres_repo.py::get_session_stats_for_device`):
```python
# device_id 기준 완료된 세션들 조회
sessions = query(BatterySession).filter(
    device_id == device_id,
    status == "finished"
).all()

# 통계 계산
total_usage_seconds = sum(
    (session.session_end_ts - session.session_start_ts).total_seconds()
    for session in sessions
    if session.session_start_ts and session.session_end_ts
)
total_capacity_ah = sum(
    session.capacity_ah
    for session in sessions
    if session.capacity_ah
)

return {
    "total_usage_hours": round(total_usage_seconds / 3600, 2),
    "total_capacity_ah": round(total_capacity_ah, 4),
    "finished_session_count": len(sessions),
}
```

**커뮤니티 카드 구성** (`app/services/community_service.py`):
```python
card = CommunityCardResponse(
    shared_report_id=shared_report.id,
    user=UserInfo(username=user.username, phone_model=user.phone_model),
    device=DeviceInfo(
        manufacturer=device.manufacturer,
        model_name=device.model_name,
        powerbank_capacity_mah=device.powerbank_capacity_mah,
    ),
    session_stats=SessionStats(**session_stats),  # 위에서 계산한 통계
    efficiency_stats=EfficiencyStats(
        soh_percentage=ai_result.soh_percentage if ai_result else None,
        efficiency_pct=None,  # curve_fit 전환으로 폐기됨
        mean_temperature_c=ai_result.mean_temperature_c if ai_result else None,
    ),
    created_at=shared_report.created_at,
)
```

## 재현성 / 실행 가이드
개발 환경 실행(요약):

```bash
cd backend
python -m venv .venv
# Windows PowerShell:
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
# 환경변수 설정: POSTGRES_*, AI_SERVER_BASE_URL 등
alembic upgrade head
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

핵심 API 시퀀스: 디바이스 생성 → 세션 시작 → raw 업로드 → 세션 종료 → predict-soh

---

## 운영/분석 메모 (권장)
- 유효 세션이 없을 경우 백엔드의 fallback 결과(soh=100%)가 저장되어 통계에 영향을 줄 수 있음 — 분석 시 필터링 권장.
- AI 응답 JSON이 `soh_analysis.recommendation`으로 저장되므로, 정기적으로 ETL하여 분석 DB로 적재하면 쿼리/집계 편의성 향상.
- `session`/`device` 용어의 혼선 주의. API 호출자 문서(또는 API 리팩토링) 권장.

## PPT 주요내용

전체 발표에는 백엔드, 프론트엔드, AI 팀 내용이 모두 들어가야 하므로 백엔드 파트는 길게 펼치기보다 **시스템 연결 역할, 데이터 흐름, AI 연동 안정성**만 선명하게 보여주는 구성이 적절합니다. 백엔드 단독으로 15장 이상을 쓰기보다는 아래 6~7장 정도로 압축하는 것을 권장합니다.

### 백엔드 파트 권장 분량
- **전체 발표 10분 내외**: 백엔드 2장
- **전체 발표 15분 내외**: 백엔드 3장
- **전체 발표 20분 이상**: 백엔드 4장까지 가능
- 백엔드 단독 상세 설명은 발표 자료 본문보다 부록 또는 질의응답 대비 자료로 빼는 것이 좋습니다.

---

### 1. 백엔드 역할: 앱과 AI를 연결하는 데이터 허브

**PPT에 꼭 넣을 내용**
- Android 앱에서 수집한 배터리 데이터를 서버가 검증하고 저장
- 저장된 세션 데이터를 AI 서버가 사용할 수 있는 형태로 전처리
- AI 예측 결과를 다시 앱과 커뮤니티 기능에 제공

**슬라이드 문장 예시**
- “백엔드는 앱에서 수집된 raw 배터리 데이터를 신뢰 가능한 진단 데이터로 정리하고, AI 예측 결과를 서비스 기능으로 연결하는 중간 허브입니다.”

**추천 시각 자료**

```text
Android App
   ↓ raw telemetry / session API
FastAPI Backend
   ↓ 저장
PostgreSQL
   ↓ 전처리 후 요청
AI Server
   ↓ SOH 결과
App Result / Community
```

**발표 포인트**
- 프론트엔드는 사용자 경험과 데이터 수집을 담당
- AI 팀은 SOH 예측 모델을 담당
- 백엔드는 두 팀 사이에서 데이터 형식, 저장, 검증, 결과 제공을 책임짐

---

### 2. 핵심 API 흐름: 진단 생명주기

**PPT에 꼭 넣을 내용**
- 백엔드는 화면 단위가 아니라 “진단 세션 흐름”을 기준으로 API가 구성됨
- 핵심 흐름은 다음 5단계로 압축 가능

```text
1. 디바이스 등록
2. 세션 시작
3. raw telemetry 업로드
4. 세션 종료
5. SOH 예측 및 결과 조회
```

**대표 API**
- `POST /api/v1/devices`
- `POST /api/v1/sessions/start`
- `POST /api/v1/sessions/{session_id}/raw`
- `POST /api/v1/sessions/{session_id}/finish`
- `POST /api/v1/sessions/{session_id}/predict-soh`
- `GET /api/v1/devices/{device_id}/latest-result`

**발표 포인트**
- API를 많이 나열하지 말고, “수집 → 저장 → 분석 → 결과” 흐름만 보여주는 것이 좋음
- 상세 엔드포인트 표는 발표 본문이 아니라 부록에 배치 권장

---

### 3. 데이터 저장 구조: 세션 중심 설계

**PPT에 꼭 넣을 내용**
- `User`: 사용자
- `Device`: 사용자의 배터리/보조배터리 기기
- `BatterySession`: 한 번의 진단/충전 세션
- `BatteryTelemetry`: 세션 중 수집된 raw 데이터
- `BatteryAiResult`, `SohAnalysis`: AI 진단 결과
- `SharedReport`: 커뮤니티 공유 보고서

**추천 시각 자료**

```text
User
  └─ Device
       └─ BatterySession
            ├─ BatteryTelemetry
            └─ BatteryAiResult / SohAnalysis
       └─ SharedReport
```

**발표 포인트**
- “세션”을 중심으로 raw 데이터와 AI 결과를 연결했기 때문에 기기별 최신 결과, 세션별 결과, 커뮤니티 이력 조회가 가능함
- ERD를 너무 자세히 보여주기보다 위 관계만 간단히 보여주는 것이 발표 피로도를 줄임

---

### 4. AI 연동 전처리: raw 데이터를 예측 가능한 입력으로 변환

**PPT에 꼭 넣을 내용**
- raw 포인트를 시간순으로 정렬
- 10분 단위로 전압/전류/온도 평균 집계
- 너무 짧거나 시작 SOC가 높은 세션 등은 AI 입력에서 제외
- 여러 완료 세션을 묶어 AI 서버의 `/soh/predict/multi`로 전달

**핵심 기술 포인트**
- `aggregate_by_10min`: raw 데이터를 10분 단위로 집계
- `_get_session_reject_reason`: 데이터 품질이 낮은 세션 제외
- `_build_multi_session_payload`: AI 서버 입력 payload 구성

**발표 포인트**
- AI 모델 성능은 모델 자체뿐 아니라 입력 데이터 품질에도 영향을 받음
- 백엔드는 “AI가 예측하기 좋은 데이터”로 정리하는 역할을 수행함

---

### 5. 주요 개선점: raw point 적분 기반 전달 에너지 계산

**PPT에 꼭 넣을 내용**
- 기존에는 집계값/기울기 기반 계산이 노이즈에 민감할 수 있음
- 개선 후 raw 포인트 전체를 사용해 구간별 전력량을 적분 방식으로 계산
- 계산된 `delivered_wh`를 AI 입력에 포함해 SOH 예측 입력의 안정성을 높임

**근거 커밋**
- `c08cbf9`: SOH 개인화 전달 에너지를 raw 포인트 적분값으로 산출해 기울기 노이즈 감소

**추천 시각 자료**

```text
전압(V) × 전류(A) = 전력(W)
전력(W) × 시간(s) = 에너지(Ws)
Ws / 3600 = Wh
```

**발표 포인트**
- 이 슬라이드는 백엔드 파트에서 가장 기술적으로 강조할 만한 내용
- “단순히 API만 만든 것이 아니라, AI 입력 데이터의 품질을 개선했다”는 메시지를 줄 수 있음

---

### 6. 결과 제공과 커뮤니티 확장

**PPT에 꼭 넣을 내용**
- AI 결과는 `battery_ai_results`와 `soh_analysis`에 저장
- 앱에서는 세션 결과와 기기별 최신 진단 결과를 조회
- 커뮤니티에서는 공유 보고서, SOH 이력, 필터링된 피드를 제공

**대표 기능**
- 기기별 최신 결과 조회
- 커뮤니티 공유 생성
- 공개 피드 조회
- SOH 이력 조회
- 제조사/용량/폰 모델 기반 필터 옵션 제공

**발표 포인트**
- 백엔드 결과는 개인 진단 화면에서 끝나지 않고 커뮤니티 비교 기능으로 확장됨
- 단, 발표에서는 커뮤니티 세부 API보다 “AI 결과가 서비스 기능으로 확장된다”는 흐름만 보여주는 것이 좋음

---

### 7. 안정성 및 예외 처리

**PPT에 꼭 넣을 내용**
- raw 업로드는 `in_progress` 세션에만 허용
- `elapsed_ms` 단조 증가 검증으로 raw 데이터 순서 품질 확인
- 유효 세션이 0개면 AI 서버 호출 전 fallback 결과 생성
- 커뮤니티 공유 생성 시 path/body `device_id` 불일치 방어
- DB commit 실패 시 rollback 후 프로젝트 예외로 변환

**발표 포인트**
- 실제 사용 환경에서는 네트워크, 중복 요청, 데이터 부족 같은 문제가 발생함
- 백엔드는 정상 흐름뿐 아니라 실패 가능성이 있는 지점을 방어함

---

## 최종 추천 PPT 구성

전체 팀 발표에서 백엔드 파트는 아래 3장 구성이 가장 균형이 좋습니다.

### 백엔드 3장 압축안

| 장 | 제목 | 핵심 메시지 |
|---|---|---|
| 1 | 백엔드 역할과 전체 흐름 | 앱, DB, AI 서버, 커뮤니티를 연결하는 데이터 허브 |
| 2 | 세션 기반 데이터 저장과 AI 전처리 | raw 데이터를 세션 단위로 저장하고 AI 입력으로 변환 |
| 3 | 주요 개선점과 결과 확장 | raw point 적분 기반 에너지 계산, fallback, 커뮤니티 공유 |

### 백엔드 4장 확장안

| 장 | 제목 | 핵심 메시지 |
|---|---|---|
| 1 | 백엔드 아키텍처 | FastAPI, Service, Repository, PostgreSQL 구조 |
| 2 | 진단 API 흐름 | 디바이스 등록부터 SOH 예측까지의 세션 생명주기 |
| 3 | AI 입력 전처리 | 10분 집계, 세션 필터링, delivered_wh 계산 |
| 4 | 결과 제공 및 안정성 | 최신 결과 조회, 커뮤니티 공유, fallback/검증 로직 |

---

## 발표에서 빼도 되는 내용

다음 내용은 문서에는 남겨두되 PPT 본문에서는 빼거나 부록으로 보내는 것을 권장합니다.

- 전체 API 엔드포인트 표
- 모든 ORM 컬럼 상세 설명
- Alembic migration 파일별 상세 변경
- 함수명 전체 나열
- 커밋 로그 전체
- 세션 reject reason 전체 목록
- 운영/분석 메모의 세부 항목

---

## 발표용 핵심 문장

- “백엔드는 앱에서 수집한 raw 배터리 데이터를 세션 단위로 저장하고, AI가 예측할 수 있는 입력 데이터로 전처리합니다.”
- “AI 모델은 예측을 담당하지만, 예측 품질을 좌우하는 데이터 정렬, 집계, 필터링, 전달 에너지 계산은 백엔드에서 수행합니다.”
- “진단 결과는 개인 결과 화면뿐 아니라 기기별 최신 결과와 커뮤니티 공유 기능까지 확장됩니다.”
- “백엔드 파트에서 가장 강조할 기술적 개선점은 raw point 적분 기반 `delivered_wh` 계산입니다.”

---

끝.
