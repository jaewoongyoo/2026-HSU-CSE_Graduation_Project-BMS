package com.han.battery.data.repository

import com.han.battery.data.model.BatteryLog
import com.han.battery.data.storage.BatteryDao

class BatteryRepository(
    private val batteryDao: BatteryDao,
    private val awsManager: AWSIoTManager
) {
    suspend fun insertLog(log: BatteryLog) = batteryDao.insertLog(log)
    suspend fun getUnsentLogs() = batteryDao.getUnsentLogs()
    suspend fun getLogsSince(timestamp: Long) = batteryDao.getLogsSince(timestamp)
    suspend fun markAsSent(ids: List<Int>) = batteryDao.markAsSent(ids)

    // AWS 전송 로직을 여기서 호출하도록 설계합니다.
    fun sendToAWS(
        payload: Any,
        onSuccess: () -> Unit,
        onFailure: (Throwable?) -> Unit = {}
    ) {
        awsManager.publishLogs(payload, onSuccess, onFailure)
    }
}
