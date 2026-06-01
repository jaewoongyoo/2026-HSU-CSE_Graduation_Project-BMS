# Private Memory

## 현재 상태
- 커뮤니티 게시판 용량 필터링 구현 검증 완료 및 수집 루프 예외 방어(try-catch), AWS IoT Manager의 중복 연결 방지(isConnecting, pendingCallbacks 도입) 및 가비지 프리(calculateCapacityAh) 메모리 최적화를 보완함.

## 다음 목표
- 실제 기기 연동을 통한 용량 필터링 테스트 진행 및 AWS IoT MQTT 연결 장시간 실시간 수집 안정성 최종 검증.
