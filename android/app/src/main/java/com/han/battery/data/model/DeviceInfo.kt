package com.han.battery

import com.han.battery.data.model.BatteryDevice

typealias DeviceInfo = BatteryDevice

fun convertFormStateToBatteryDevice(
    brand: String,
    nickname: String,
    capacity: String,
    manufactureDate: String
): BatteryDevice {
    val trimmedNickname = nickname.trim()
    val capacityInt = capacity.trim().toIntOrNull()

    require(trimmedNickname.isNotBlank()) { "nickname is required" }
    require(trimmedNickname.length <= 100) { "nickname must be 100 characters or fewer" }
    require(capacityInt != null) { "capacity must be numeric" }
    require(capacityInt in 100..100000) { "capacity must be between 100 and 100000 mAh" }

    return BatteryDevice(
        brand = brand.trim(),
        nickname = trimmedNickname,
        capacity = capacityInt,
        manufactureDate = manufactureDate.trim()
    )
}
