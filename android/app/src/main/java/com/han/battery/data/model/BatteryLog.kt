package com.han.battery.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_logs")
data class BatteryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val voltage: Int,     // 전압 (mV)
    val current: Double,  // 전류 (mA)
    val level: Int,       // 배터리 잔량 (%)
    val temperature: Int, // 온도 (Celsius)
    val isCharging: Boolean,
    val isSent: Boolean = false // 서버 전송 여부 (중요!)
)
