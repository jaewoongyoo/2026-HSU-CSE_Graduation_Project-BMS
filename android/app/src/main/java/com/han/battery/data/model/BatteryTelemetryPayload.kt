package com.han.battery.data.model

data class BatteryTelemetryPayload(
    val device_id: Int,
    val session_id: String,
    val timestamp: Long,
    val level: Int,
    val voltage: Int,
    val current: Int,
    val temperature: Int,
    val elapsed_ms: Long,
    val isCharging: Boolean,
    val screen_state: Boolean
)
