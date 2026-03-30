package com.han.battery.data.model
// 배터리의 실시간 상태 정보 데이터 모델 (SOC, SOH, 전류, 전압, 전력 등)

/**
 * 배터리의 실시간 상태 정보를 나타냅니다.
 */
data class BatteryStatus(
    val soc: Int = 0,           // 충전량 (%)
    val soh: Int = 100,         // 건강도 (%)
    val current: Int = 0,       // 전류 (mA)
    val voltage: Double = 0.0,  // 전압 (V)
    val power: Double = 0.0     // 전력 (W)
)