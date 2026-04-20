package com.han.battery.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName

@Entity(tableName = "battery_logs")
data class BatteryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerialName("device_id") val deviceId: Int,   // ✅ 백엔드 키값 매핑
    @SerialName("session_id") val sessionId: Int,
    val timestamp: Long,
    val voltage: Int,     // 전압 (mV)
    @SerialName("current_ma") val current: Int,   // ✅ 백엔드 단위 명시
    @SerialName("soc") val level: Int,            // ✅ 백엔드 명칭(soc) 매핑
    val temperature: Int,
    @SerialName("is_charging") val isCharging: Boolean,
    @Transient val isSent: Boolean = false
)