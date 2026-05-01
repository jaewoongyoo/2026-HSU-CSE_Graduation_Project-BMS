package com.han.battery.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.han.battery.BatteryApplication
import com.han.battery.data.common.BatteryUtils
// ⭐ [확인] 새로 만든 옵션 B 데이터 클래스 두 개를 임포트합니다.
import com.han.battery.data.model.BatteryTelemetryBatchPayload
import com.han.battery.data.model.BatteryTelemetryLog
import kotlinx.coroutines.*
import java.time.Instant

class BatteryMonitoringService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "battery_monitoring_channel"
    private val batteryApplication by lazy { application as BatteryApplication }
    private val repository by lazy { batteryApplication.batteryRepository }
    private val awsIoTManager by lazy { batteryApplication.awsIoTManager }
    private val authRepository by lazy { batteryApplication.authRepository }
    private val preferenceManager by lazy { batteryApplication.preferenceManager }

    private var collectingJob: Job? = null
    private var flushLoopJob: Job? = null

    private var activeDeviceId: Int = 0
    private var activeDeviceModelName: String? = null
    private var sessionId: Int? = null
    private var sessionStartTimestamp: Long = System.currentTimeMillis()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        val activeDevice = preferenceManager.getActiveDevice()
            ?: preferenceManager.getAllDevices().firstOrNull { it.id > 0 }

        activeDeviceId = activeDevice?.id ?: 0
        activeDeviceModelName = activeDevice?.model_name
        if (activeDevice != null) {
            preferenceManager.setActiveDevice(activeDevice)
        }

        if (activeDeviceId <= 0) {
            Log.w("BatteryService", "활성 배터리 ID가 없어 AWS 연결을 건너뜁니다.")
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            startSessionAndMonitoring(activeDevice?.powerbank_capacity_mah?.toDouble())
        }
        return START_STICKY
    }

    private suspend fun startSessionAndMonitoring(powerbankCapacityStartMah: Double?) {
        val sessionStartedAt = Instant.now()

        val startSessionResult = authRepository.startBatterySession(
            deviceId = activeDeviceId,
            powerbankId = activeDeviceModelName,
            powerbankCapacityStartMah = powerbankCapacityStartMah,
            sessionStartTs = sessionStartedAt
        )

        startSessionResult.onSuccess { response ->
            sessionId = response.id
            sessionStartTimestamp = sessionStartedAt.toEpochMilli()

            val clientId = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d("AWSIoTManager", "연결 시도 ClientID: $clientId")

            awsIoTManager.initAndConnect(clientId, activeDeviceId) {
                serviceScope.launch {
                    flushPendingLogs()
                    startFlushingLoop()
                }
            }
            startCollecting()

        }.onFailure { error ->
            Log.e("BatteryService", "세션 시작 실패로 모니터링을 중단합니다: ${error.message}", error)
            stopSelf()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "배터리 모니터링",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("배터리 수집 시스템 작동 중")
            .setContentText("AWS로 데이터를 실시간 전송하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun startCollecting() {
        if (collectingJob?.isActive == true) return

        collectingJob = serviceScope.launch {
            while (isActive) {
                val log = BatteryUtils.getCurrentBatteryState(applicationContext)
                repository.insertLog(log)
                delay(2000)
            }
        }
    }

    private fun startFlushingLoop() {
        if (flushLoopJob?.isActive == true) return

        flushLoopJob = serviceScope.launch {
            while (isActive) {
                delay(60000)
                flushPendingLogs()
            }
        }
    }

    // ⭐ [완전 수정됨] 옵션 B (배치 전송) 방식에 맞게 데이터 맵핑 구조 변경
    private suspend fun flushPendingLogs() {
        val pendingLogs = repository.getUnsentLogs() // 1. DB에서 미전송 로그(BatteryLog) 가져오기
        if (pendingLogs.isEmpty()) return

        val currentSessionId = sessionId ?: return
        val screenState = getCurrentScreenState()

        // 2. [Mapping] BatteryLog(DB용) -> BatteryTelemetryLog(서버용) 변환
        val mappedLogs = pendingLogs.map { dbLog ->
            BatteryTelemetryLog(
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

        // 3. [Batch] 택배 박스(BatteryTelemetryBatchPayload)에 담기
        val batchPayload = BatteryTelemetryBatchPayload(
            deviceId = activeDeviceId,
            sessionId = currentSessionId,
            logs = mappedLogs
        )

        // 4. 전송
        repository.sendToAWS(
            payload = batchPayload, // 📦 이제 리스트가 아닌 '객체 하나'를 보냅니다.
            onSuccess = {
                serviceScope.launch {
                    repository.markAsSent(pendingLogs.map { it.id })
                    Log.d("BatteryService", "AWS 배치 전송 완료: ${pendingLogs.size}건")
                }
            }
        )
    }

    private fun getCurrentScreenState(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        collectingJob?.cancel()
        flushLoopJob?.cancel()

        awsIoTManager.disconnect()

        serviceScope.cancel()
    }
}