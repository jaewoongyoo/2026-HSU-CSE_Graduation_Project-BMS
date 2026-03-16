## 📝 PR 목적 및 작업 내용 (Description)
* * ## 🔄 변경 유형 (Type of Change)
- [ ] 🚀 새로운 기능 추가 (Feat)
- [ ] 🐛 버그 수정 (Fix)
- [ ] ♻️ 코드 리팩토링 (Refactor)
- [ ] 📚 문서 업데이트 (Docs)
- [ ] ⚙️ 빌드/설정 변경 (Chore)

## ⚠️ 타 도메인 영향도 (Cross-Domain Impact) - **매우 중요**
- [ ] **MQTT 통신 규약 (JSON 스키마, 토픽명)이 변경됨** $\rightarrow$ *반드시 Front 및 HW 파트의 리뷰(Approve) 필요!*
- [ ] **AI 모델 입력 피처(Feature) 이름이나 단위가 변경됨** $\rightarrow$ *반드시 Data 파트의 리뷰 필요!*
- [ ] 타 파트에 영향을 주지 않는 독립적인 변경임.

## 📸 테스트 결과 (Test Results)
* 테스트 환경: (예: ESP32 실물 보드 / 로컬 WPF 에뮬레이터 / PyTorch 로컬 환경)
* 결과 스크린샷 또는 로그: 

## ✅ 셀프 체크리스트 (Self-Checklist)
- [ ] 브랜치명과 PR 제목 컨벤션을 준수했는가? (예: `Feat(HW): 센서 데이터 수집 추가`)
- [ ] 내가 작성한 코드가 로컬 환경에서 에러 없이 빌드/실행되는가?
- [ ] **[CRITICAL] AWS IoT 인증서, 비밀키, Wi-Fi 비밀번호 등 민감한 정보가 하드코딩되어 올라가지 않았는가?** (보안 사고 주의)
- [ ] 불필요한 주석이나 디버깅용 `print()`, `Serial.println()` 코드를 정리했는가?
