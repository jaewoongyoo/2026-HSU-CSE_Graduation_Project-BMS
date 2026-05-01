package com.han.battery.data.model

import com.google.gson.annotations.SerializedName

data class BatteryTelemetryBatchPayload(
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("session_id") val sessionId: Int,
    val logs: List<BatteryTelemetryLog> // 아래 개별 로그들의 리스트
)

// 📝 택배 박스 안에 들어갈 개별 데이터 포인트
data class BatteryTelemetryLog(
    val timestamp: Long,
    val level: Int,
    val voltage: Int,
    val current: Int,
    val temperature: Int,
    @SerializedName("elapsed_ms") val elapsedMs: Long,
    @SerializedName("is_charging") val isCharging: Boolean, // ⭐ 백엔드와 맞춘 스네이크 케이스
    @SerializedName("screen_state") val screenState: Boolean
)