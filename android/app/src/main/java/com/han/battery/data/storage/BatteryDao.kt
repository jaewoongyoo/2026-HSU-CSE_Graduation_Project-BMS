package com.han.battery.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.han.battery.data.model.BatteryLog

@Dao
interface BatteryDao {
    @Insert
    suspend fun insertLog(log: BatteryLog): Long

    @Query("SELECT * FROM battery_logs WHERE isSent = 0 ORDER BY timestamp ASC")
    suspend fun getUnsentLogs(): List<BatteryLog>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :timestamp ORDER BY timestamp ASC")
    suspend fun getLogsSince(timestamp: Long): List<BatteryLog>

    @Query("UPDATE battery_logs SET isSent = 1 WHERE id IN (:ids)")
    suspend fun markAsSent(ids: List<Int>): Int
}
