# Private Memory

## 현재 상태
- 안드로이드 메인 API 주소는 메인 백엔드(3.37.77.102:8000)로 복구하고, network-security-config에 AI 신서버 IP(43.203.28.250)를 추가해 트래픽을 허용하도록 복구함. 백엔드 capacities 충돌은 안드로이드 칩 UI 구조에 따라 capacities(IN 필터) 방식을 채택하도록 정리함.

## 다음 목표
- 이중 서버 환경(메인 BMS 백엔드 및 전용 AI 예측 서버) 통합 연동 시나리오 최종 모니터링 검증.
