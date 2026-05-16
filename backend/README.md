# BatteryInsight Backend

BatteryInsight 프로젝트용 FastAPI 백엔드입니다.

## 개요

현재 백엔드는 기존 PostgreSQL 스키마를 기준으로 동작합니다.

- `users`
- `devices`
- `battery_telemetry`
- `soh_analysis`
- `shared_reports`

주의할 점은 API 경로에는 아직 `session`이라는 이름이 남아 있지만, 현재 구현에서는 `start`에서 만든 `devices.device_id`를 `session_id`처럼 재사용한다는 점입니다. 즉 `raw`, `finish`, `predict-soh`, `result` 모두 내부적으로는 `device_id` 기준으로 동작합니다.

## 실행 조건

- `requirements.txt` 기준 Python 가상환경
- PostgreSQL
- 별도로 실행 중인 AI 서버 (`ai/soh_service`)

## 실행 방법

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## 환경 변수

주요 설정은 `app/core/config.py`에서 관리합니다.

- `POSTGRES_HOST`
- `POSTGRES_PORT`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `AI_SERVER_BASE_URL`
- `REQUEST_TIMEOUT_SECONDS`

기본 AI 서버 주소:

```text
http://43.203.28.250:8000
```

로컬 AI 서버를 사용할 때는 `.env`에서 `AI_SERVER_BASE_URL=http://127.0.0.1:8000`처럼 덮어씁니다.

## API 목록

### 헬스 체크

- `GET /api/v1/health`
- `GET /api/v1/ai/health`

`/api/v1/health`는 백엔드 상태를 확인합니다.  
`/api/v1/ai/health`는 AI 서버 상태를 확인합니다.

### 인증

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`

### 사용자

- `GET /api/v1/users`
- `GET /api/v1/users/{user_id}`
- `PATCH /api/v1/users/{user_id}`
- `DELETE /api/v1/users/{user_id}`

### 세션 형태 API

- `POST /api/v1/sessions/start`
- `POST /api/v1/sessions/{session_id}/raw`
- `POST /api/v1/sessions/{session_id}/finish`
- `POST /api/v1/sessions/{session_id}/predict-soh`
- `GET /api/v1/sessions/{session_id}/result`

현재 의미는 다음과 같습니다.

- `session_id`는 별도 세션 테이블의 키가 아니라 생성된 `device_id`입니다.
- `start`는 `devices` 테이블에 새 row를 만듭니다.
- `raw`는 `battery_telemetry`에 데이터를 저장합니다.
- `finish`는 현재 시점에서는 존재 확인 후 응답만 반환합니다.
- `predict-soh`는 해당 `device_id`의 telemetry를 읽어 AI 서버를 호출합니다.
- `result`는 해당 `device_id`의 최신 `soh_analysis` 결과를 반환합니다.

## 데이터 매핑

### `POST /api/v1/sessions/start`

`devices` 테이블에 저장됩니다.

- `session_id` -> 생성된 `device_id`
- `user_id` -> 숫자면 `users.id`, 문자열이면 `users.username`으로 매칭 시도
- `device_model` -> `devices.model_name`
- `phone_capacity_mah` -> `devices.capacity_mah`

현재 DB 스키마에는 아래 값들을 직접 저장할 컬럼이 없습니다.

- `powerbank_id`
- `cable_id`
- `android_api_level`
- 세션 시작/종료 시각

### `POST /api/v1/sessions/{session_id}/raw`

`battery_telemetry` 테이블에 저장됩니다.

- `timestamp` -> `battery_telemetry.timestamp`
- `battery_level` -> `battery_telemetry.soc`
- `voltage_mv` -> Volt로 변환 후 `battery_telemetry.voltage`
- `current_ma` -> `battery_telemetry.current_ma`
- `power_w` -> 전압/전류 기반 계산값

현재 DB 스키마에는 아래 값들을 직접 저장할 컬럼이 없습니다.

- `temperature_c`
- `elapsed_ms`
- `battery_status`
- `screen_state`

### `POST /api/v1/sessions/{session_id}/predict-soh`

백엔드는 `battery_telemetry`에서 AI 요청 데이터를 다시 조립합니다.

- `elapsed_ms`는 첫 timestamp 기준 차이값으로 계산합니다.
- `temperature_c`는 현재 `null`로 전달합니다.
- `phone_capacity_mah`는 `devices.capacity_mah`를 사용합니다.
- `powerbank_capacity_mah`는 현재 `10000`으로 고정되어 있습니다.

예측 결과는 `soh_analysis`에 다음처럼 저장됩니다.

- `soh_percentage` -> `current_soh`
- `condition` -> `grade`
- AI 원본 응답 JSON -> `recommendation`

즉 현재 DB 구조에서는 AI 응답의 모든 필드를 개별 컬럼으로 저장하지는 못합니다.

## 현재 한계

- 실제 세션 전용 테이블이 없습니다.
- API 이름은 `session`이지만 내부 데이터 모델은 `device` 중심입니다.
- `finish`는 세션 상태를 DB에 기록하지 않습니다.
- 현재 DB 구조 기준으로는 멀티세션 AI 예측을 자연스럽게 지원하지 못합니다.
- telemetry 온도 데이터는 DB에 저장되지 않습니다.
- 결과 상세 정보는 `soh_analysis` 구조에 맞춰 일부만 저장됩니다.

## Alembic 상태

Alembic은 `backend/alembic` 기준으로 초기화되어 있습니다. 다만 스키마 방향이 완전히 정리되기 전까지는 자동 생성 마이그레이션을 바로 적용하면 안 됩니다.
