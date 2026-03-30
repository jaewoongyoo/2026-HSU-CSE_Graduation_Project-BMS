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
 */
fun convertFormStateToBatteryDevice(
    brand: String,
    nickname: String,
    capacity: String,
    manufactureDate: String
): BatteryDevice {
    return BatteryDevice(
        brand = brand.ifBlank { "미지정" },
        nickname = nickname,
        capacity = capacity.toIntOrNull() ?: 0,
        manufactureDate = manufactureDate.ifBlank { "미지정" }
    )
}
