package com.han.battery.data.repository

import android.util.Log
import com.han.battery.data.api.ApiService
import com.han.battery.data.model.BatteryLog
import com.han.battery.data.storage.BatteryDao
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class BatteryRepository(
    private val batteryDao: BatteryDao,
    private val awsManager: AWSIoTManager,
    private val apiService: ApiService
) {
    suspend fun insertLog(log: BatteryLog) = batteryDao.insertLog(log)
    suspend fun getUnsentLogs() = batteryDao.getUnsentLogs()
    suspend fun getLogsSince(timestamp: Long) = batteryDao.getLogsSince(timestamp)
    suspend fun markAsSent(ids: List<Int>) = batteryDao.markAsSent(ids)

    /**
     * AWS MQTT 전송을 우선 시도하고, 실패 시 HTTP API를 통해 Telemetry 데이터를 우회 전송하는 Fallback 메커니즘
     */
    suspend fun sendTelemetryWithFallback(
        sessionId: Int,
        deviceId: Int,
        logs: List<BatteryLog>,
        sessionStartTimestamp: Long,
        screenState: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Throwable?) -> Unit = {}
    ) {
        // 1. MQTT용 payload 매핑 (BatteryTelemetryBatchPayload)
        val mappedLogs = logs.map { dbLog ->
            com.han.battery.data.model.BatteryTelemetryLog(
                timestamp = dbLog.timestamp,
                level = dbLog.level,
                voltage = dbLog.voltage,
                current = dbLog.current,
                temperature = dbLog.temperature,
                elapsedMs = (dbLog.timestamp - sessionStartTimestamp).coerceAtLeast(0L),
                isCharging = dbLog.isCharging,
                screenState = screenState
            )
        }
        val batchPayload = com.han.battery.data.model.BatteryTelemetryBatchPayload(
            deviceId = deviceId,
            sessionId = sessionId,
            logs = mappedLogs
        )

        // 2. AWS MQTT 전송 시도
        val mqttSuccess = suspendCancellableCoroutine<Boolean> { continuation ->
            awsManager.publishLogs(
                payload = batchPayload,
                onSuccess = {
                    if (continuation.isActive) continuation.resume(true)
                },
                onFailure = {
                    if (continuation.isActive) continuation.resume(false)
                }
            )
        }

        if (mqttSuccess) {
            onSuccess()
            return
        }

        // 3. AWS MQTT 실패 시 HTTP API Fallback 전송 시도
        Log.w("BatteryRepository", "AWS MQTT 전송 실패 -> HTTP API 대체 전송 시도 (sessionId: $sessionId)")
        val rawDataPoints = logs.map { dbLog ->
            com.han.battery.data.model.RawDataPoint(
                timestamp = java.time.Instant.ofEpochMilli(dbLog.timestamp).toString(), // ISO 8601 string
                voltageMv = dbLog.voltage.toDouble(),
                currentMa = dbLog.current,
                temperatureC = dbLog.temperature.toDouble(),
                elapsedMs = (dbLog.timestamp - sessionStartTimestamp).coerceAtLeast(0L).toDouble(),
                batteryLevel = dbLog.level,
                batteryStatus = if (dbLog.isCharging) "Charging" else "Discharging"
            )
        }
        val httpPayload = com.han.battery.data.model.RawUploadRequest(dataPoints = rawDataPoints)

        try {
            apiService.uploadRaw(sessionId, httpPayload).getOrThrow()
            Log.d("BatteryRepository", "HTTP API 대체 전송 성공 ✅ (sessionId: $sessionId)")
            onSuccess()
        } catch (e: Exception) {
            Log.e("BatteryRepository", "HTTP API 대체 전송도 실패 ❌: ${e.message}", e)
            onFailure(e)
        }
    }
}
