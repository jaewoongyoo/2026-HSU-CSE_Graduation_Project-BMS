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
import com.han.battery.data.model.BatteryTelemetryPayload
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
    private var flushJob: Job? = null
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
        if (collectingJob?.isActive == true) {
            return
        }

        collectingJob = serviceScope.launch {
            while (isActive) {
                val log = BatteryUtils.getCurrentBatteryState(applicationContext)
                Log.d("BatteryService", "수집 완료: ${log.level}%")
                repository.insertLog(log)
                flushPendingLogs()
                delay(60000)
            }
        }
    }

    private suspend fun flushPendingLogs() {
        if (flushJob?.isActive == true) {
            return
        }

        flushJob = serviceScope.launch {
            val pendingLogs = repository.getUnsentLogs()
            if (pendingLogs.isEmpty()) {
                return@launch
            }

            val currentSessionId = sessionId
            if (currentSessionId == null || currentSessionId <= 0) {
                Log.w("BatteryService", "유효한 session_id가 없어 AWS 전송을 건너뜁니다.")
                return@launch
            }

            val screenState = getCurrentScreenState()
            val telemetryPayloads = pendingLogs.map { log ->
                BatteryTelemetryPayload(
                    device_id = activeDeviceId,
                    session_id = currentSessionId,
                    timestamp = log.timestamp,
                    level = log.level,
                    voltage = log.voltage,
                    current = log.current,
                    temperature = log.temperature,
                    elapsed_ms = (log.timestamp - sessionStartTimestamp).coerceAtLeast(0L),
                    isCharging = log.isCharging,
                    screen_state = screenState
                )
            }

            repository.sendToAWS(
                logs = telemetryPayloads,
                onSuccess = {
                    serviceScope.launch {
                        repository.markAsSent(pendingLogs.map { it.id })
                        Log.d("BatteryService", "AWS 전송 성공 및 sent 처리 완료: ${pendingLogs.size}건")
                    }
                },
                onFailure = { error ->
                    Log.w("BatteryService", "AWS 전송 보류: ${error?.message ?: "연결 안 됨"}")
                }
            )
        }
        flushJob?.join()
    }

    private fun getCurrentScreenState(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        collectingJob?.cancel()
        flushJob?.cancel()
        serviceScope.cancel()
    }
}
