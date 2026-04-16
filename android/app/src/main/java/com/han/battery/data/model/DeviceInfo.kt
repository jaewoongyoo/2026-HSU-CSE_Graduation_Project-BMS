package com.han.battery

import com.han.battery.data.model.BatteryDevice

typealias DeviceInfo = BatteryDevice

fun convertFormStateToBatteryDevice(
    manufacturer: String,
    model_name: String,
    powerbank_capacity_mah: String,
    manufacture_date: String
): BatteryDevice {
    val trimmedModelName = model_name.trim()
    val capacityInt = powerbank_capacity_mah.trim().toIntOrNull()

    require(trimmedModelName.isNotBlank()) { "model_name is required" }
    require(trimmedModelName.length <= 100) { "model_name must be 100 characters or fewer" }
    require(capacityInt != null) { "powerbank_capacity_mah must be numeric" }
    require(capacityInt in 100..100000) { "powerbank_capacity_mah must be between 100 and 100000 mAh" }

    return BatteryDevice(
        model_name = trimmedModelName,
        powerbank_capacity_mah = capacityInt,
        manufacture_date = manufacture_date.trim()
    )
}
