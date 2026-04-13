package com.han.battery.data.model

import com.han.battery.data.model.BatteryRegistrationRequest
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
    val brand: String = "미지정",
    val nickname: String,
    val capacity: Int = 0,  // 기본값을 0으로 설정 (실제 용량값이 설정되지 않은 상태를 명시)
    val manufactureDate: String = "미지정"
) {
    init {
        // 유효성 검증
        require(nickname.isNotBlank()) { "배터리 모델명은 필수입니다" }
        require(nickname.length <= 100) { "모델명은 100자 이하여야 합니다" }
        // capacity가 0인 경우는 대시보드에서 임시 사용하는 경우이므로 검증에서 제외
        if (capacity > 0) {
            require(capacity in 100..200000) { "용량은 100 ~ 200000 mAh 범위여야 합니다" }
        }
    }

    /**
     * 유효한 배터리 정보인지 확인합니다.
     * capacity가 0이면 미설정 상태이므로 false 반환
     */
    fun isValid(): Boolean = nickname.isNotBlank() && capacity in 100..200000

    /**
     * 표시용 제조사명을 반환합니다.
     */
    fun getDisplayBrand(): String = brand.takeIf { it.isNotBlank() && it != "미지정" } ?: "미지정"

    /**
     * 표시용 제조일을 반환합니다.
     */
    fun getDisplayManufactureDate(): String = manufactureDate.takeIf { it.isNotBlank() && it != "미지정" } ?: "미지정"

    /**
     * 서버 등록용 요청 데이터를 생성합니다.
     */
    fun toRegistrationRequest(): BatteryRegistrationRequest {
        return BatteryRegistrationRequest(
            manufacturer = brand.takeIf { it.isNotBlank() && it != "미지정" },
            model_name = nickname,
            capacity_mah = capacity,
            manufacture_date = manufactureDate.takeIf { it.isNotBlank() && it != "미지정" },
            powerbank_capacity_mah = capacity
        )
    }
}

/**
 * 향후 구현 예정 기능:
 * - ageInMonths: 제조일자로부터 현재까지의 개월 수 계산
 * - chargeCount: 충방전 횟수
 * - healthPercentage: 배터리 건강도 (%)
 * - estimatedLifespan: 예상 수명 (개월)
 */

