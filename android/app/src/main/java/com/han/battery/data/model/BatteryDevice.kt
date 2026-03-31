package com.han.battery.data.model
// 사용자가 등록한 배터리 기기 정보를 담는 데이터 모델 (제조사, 모델명, 용량, 제조년월 등)

/**
 * 사용자가 등록한 보조배터리의 정보를 담는 데이터 클래스입니다.
 * 
 * 이 클래스는 배터리 인사이트 앱에서 사용자가 관리하는 모든 보조배터리의
 * 기본 정보를 저장합니다.
 * 
 * @property brand 제조사 (예: Anker, Samsung, Xiaomi)
 * @property nickname 사용자가 지정한 별칭 (예: "내 맥세이프", "업무용 배터리")
 * @property capacity 정격 용량 (단위: mAh, 예: 10000)
 * @property manufactureDate 제조년월 (형식: YYYY-MM, 예: "2023-06")
 */
data class BatteryDevice(
    val brand: String,          // 제조사
    val nickname: String,       // 별칭
    val capacity: Int,          // 정격 용량 (mAh)
    val manufactureDate: String // 제조년월 (YYYY-MM)
)

/**
 * 향후 구현 예정 기능:
 * - ageInMonths: 제조일자로부터 현재까지의 개월 수 계산
 * - chargeCount: 충방전 횟수
 * - healthPercentage: 배터리 건강도 (%)
 * - estimatedLifespan: 예상 수명 (개월)
 */

