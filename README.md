# 2026-HSU-CSE Graduation Project BMS

한세대학교 컴퓨터공학과 졸업프로젝트용 배터리 상태 분석 시스템입니다.

## 구성

- `android/`
  Android 앱
- `backend/`
  FastAPI 백엔드
- `ai/`
  SOH 예측 모델 및 AI 서버

## 현재 구조 요약

- Android 앱은 배터리 정보를 수집하는 UI와 로컬 로직이 중심입니다.
- Backend는 PostgreSQL 기존 스키마 위에서 동작하도록 맞추는 중입니다.
- AI 서버는 `ai/soh_service`에서 별도로 실행되며 `/soh/predict`, `/soh/health` 엔드포인트를 제공합니다.

## 실행 위치

- Android: `android/`
- Backend: `backend/`
- AI: `ai/`

## 참고

- 백엔드 상세 설명은 [backend/README.md](backend/README.md) 참고
- AI 서버 계약과 예측 API는 [ai/soh_service/README.md](ai/soh_service/README.md) 참고
