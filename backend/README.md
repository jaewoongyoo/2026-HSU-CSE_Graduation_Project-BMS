# BatteryInsight Backend

FastAPI + DynamoDB 기반 백엔드 스캐폴드입니다.

## 핵심 API
- `POST /api/v1/sessions/start`
- `POST /api/v1/sessions/{session_id}/raw`
- `POST /api/v1/sessions/{session_id}/finish`
- `POST /api/v1/sessions/{session_id}/predict-soh`
- `GET /api/v1/sessions/{session_id}/result`
- `GET /api/v1/ai/health`

## 실행
```bash
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## DynamoDB 테이블 예시
- `battery_sessions`
- `battery_session_raw`

## 비고
- AI 서버는 별도로 떠 있어야 합니다.
- `.env.example`을 복사해서 `.env`로 사용하세요.
