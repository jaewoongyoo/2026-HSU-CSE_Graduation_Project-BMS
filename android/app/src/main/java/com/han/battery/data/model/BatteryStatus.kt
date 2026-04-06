package com.han.battery.data.model
// 배터리의 실시간 상태 정보 데이터 모델 (SOC, SOH, 전류, 전압, 전력 등)

/**
 * 배터리의 실시간 상태 정보를 나타냅니다.
 */
data class BatteryStatus(
    val soc: Int = 0,             // 잔량 (%)
    val voltage: Float = 0f,      // 전압 (V)
    val current: Float = 0f,      // 전류 (mA)
    val temperature: Float = 0f,  // 온도 (°C)
    val isCharging: Boolean = false
)