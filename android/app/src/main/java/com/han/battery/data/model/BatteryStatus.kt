package com.han.battery.data.model

data class BatteryStatus(
    val soc: Int = 0,           // 충전량 (%)
    val soh: Int = 100,         // 건강도 (%)
    val current: Int = 0,       // 전류 (mA)
    val voltage: Double = 0.0,  // 전압 (V)
    val power: Double = 0.0     // 전력 (W)
)