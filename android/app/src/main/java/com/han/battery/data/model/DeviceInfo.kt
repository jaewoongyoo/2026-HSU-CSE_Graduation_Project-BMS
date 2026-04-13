package com.han.battery
// 기기 정보 데이터 모델 및 폼 입력값을 BatteryDevice로 변환하는 유틸리티 함수

import com.han.battery.data.model.BatteryDevice

/**
 * DeviceInfo는 BatteryDevice의 별칭입니다.
 * 타입 안전성을 위해 BatteryDevice를 직접 사용하는 것을 권장합니다.
 * @deprecated BatteryDevice 사용을 권장합니다.
 */
typealias DeviceInfo = BatteryDevice

/**
 * FormState의 데이터를 BatteryDevice로 변환합니다.
 * @throws IllegalArgumentException 유효하지 않은 입력값일 경우
 */
fun convertFormStateToBatteryDevice(
    brand: String,
    nickname: String,
    capacity: String,
    manufactureDate: String
): BatteryDevice {
    val trimmedNickname = nickname.trim()
    val capacityInt = capacity.trim().toIntOrNull()

    // 유효성 검사
    if (trimmedNickname.isBlank()) {
        throw IllegalArgumentException("모델명은 필수입니다")
    }
    if (trimmedNickname.length > 100) {
        throw IllegalArgumentException("모델명은 100자 이하여야 합니다")
    }
    if (capacityInt == null) {
        throw IllegalArgumentException("용량은 유효한 숫자여야 합니다")
    }
    if (capacityInt <= 0) {
        throw IllegalArgumentException("용량은 0보다 커야 합니다")
    }
    if (capacityInt > 100000) {
        throw IllegalArgumentException("용량은 100000 mAh 이하여야 합니다")
    }

    return BatteryDevice(
        brand = brand.trim().ifBlank { "미지정" },
        nickname = trimmedNickname,
        capacity = capacityInt,
        manufactureDate = manufactureDate.trim().ifBlank { "미지정" }
    )
}
