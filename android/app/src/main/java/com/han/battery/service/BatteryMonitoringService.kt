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
import kotlin.math.abs

class BatteryMonitoringService : Service() {
    companion object {
        const val ACTION_START_MONITORING = "com.han.battery.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.han.battery.action.STOP_MONITORING"
    }

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
    private var sessionStartInstant: Instant? = null
    private var sessionStartTimestamp: Long = System.currentTimeMillis()
    @Volatile
    private var shouldFinishSessionOnDestroy = false
    @Volatile
    private var isFinishingSession = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()

        if (intent?.action == ACTION_STOP_MONITORING) {
            shouldFinishSessionOnDestroy = true
            preferenceManager.setMonitoringManuallyStopped(true)
            preferenceManager.setMonitoringActive(false)
            Log.d("BatteryService", "명시적 종료 요청 수신 → 세션 종료 후 서비스 중지")
            stopSelf()
            return START_NOT_STICKY
        }

        if (collectingJob?.isActive == true || sessionId != null) {
            Log.d("BatteryService", "이미 모니터링 중이므로 중복 시작을 건너뜁니다.")
            return START_STICKY
        }

        val activeDevice = preferenceManager.getActiveDevice()
            ?: preferenceManager.getAllDevices().firstOrNull { it.id > 0 }

        activeDeviceId = activeDevice?.id ?: 0
        activeDeviceModelName = activeDevice?.model_name
        if (activeDevice != null) {
            preferenceManager.setActiveDevice(activeDevice)
        }

        if (activeDeviceId <= 0) {
            Log.w("BatteryService", "활성 배터리 ID가 없어 AWS 연결을 건너뜁니다.")
            preferenceManager.setMonitoringActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        preferenceManager.setMonitoringActive(true)
        preferenceManager.setMonitoringManuallyStopped(false)
        serviceScope.launch {
            startSessionAndMonitoring()
        }
        return START_STICKY
    }

    private suspend fun startSessionAndMonitoring() {
        val sessionStartedAt = Instant.now()

        val startSessionResult = authRepository.startBatterySession(
            deviceId = activeDeviceId,
            powerbankId = activeDeviceModelName,
            sessionStartTs = sessionStartedAt
        )

        startSessionResult.onSuccess { response ->
            sessionId = response.id
            sessionStartInstant = sessionStartedAt
            sessionStartTimestamp = sessionStartedAt.toEpochMilli()

            val clientId = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d("AWSIoTManager", "연결 시도 ClientID: $clientId")

            // AWS IoT 연결 시도는 백그라운드에서 진행
            awsIoTManager.initAndConnect(clientId, activeDeviceId) {
                Log.d("BatteryService", "AWS IoT 연결 성공 콜백 실행")
            }

            // AWS 연결 여부와 상관없이 수집 및 Flush 루프 기동
            startCollecting()
            startFlushingLoop()

            // 즉시 1회 Flush 시도
            serviceScope.launch {
                flushPendingLogs()
            }

        }.onFailure { error ->
            Log.e("BatteryService", "세션 시작 실패로 모니터링을 중단합니다: ${error.message}", error)
            preferenceManager.setMonitoringActive(false)
            stopSelf()
        }
    }

    private val RESULT_CHANNEL_ID = "battery_diagnosis_result_channel"

    private fun startForegroundServiceWithNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "배터리 모니터링",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)

            val resultChannel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "배터리 진단 결과",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI 배터리 진단이 완료되었을 때 결과를 알려줍니다."
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(resultChannel)
        }

        val intent = Intent(this, com.han.battery.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("배터리 수집 시스템 작동 중")
            .setContentText("AWS로 데이터를 실시간 전송하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun updateForegroundNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, com.han.battery.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("배터리 수집 시스템 작동 중")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        manager.notify(1001, notification)
    }

    private fun showDiagnosisResultNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelExists = manager.getNotificationChannel(RESULT_CHANNEL_ID) != null
            if (!channelExists) {
                val resultChannel = NotificationChannel(
                    RESULT_CHANNEL_ID,
                    "배터리 진단 결과",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "AI 배터리 진단이 완료되었을 때 결과를 알려줍니다."
                    enableLights(true)
                    enableVibration(true)
                }
                manager.createNotificationChannel(resultChannel)
            }
        }

        val intent = Intent(this, com.han.battery.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, RESULT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(2002, notification)
    }

    private fun startCollecting() {
        if (collectingJob?.isActive == true) return

        collectingJob = serviceScope.launch {
            var collectedCount = 0
            var lastUpdateTs = 0L
            while (isActive) {
                val log = BatteryUtils.getCurrentBatteryState(applicationContext)
                repository.insertLog(log)
                collectedCount++
                
                if (!log.isCharging) {
                    Log.d("BatteryService", "충전 종료 상태 감지 → 모니터링 서비스를 종료합니다.")
                    shouldFinishSessionOnDestroy = true
                    preferenceManager.setMonitoringManuallyStopped(false)
                    preferenceManager.setMonitoringActive(false)
                    
                    showDiagnosisResultNotification(
                        title = "충전이 중단되었습니다",
                        message = "케이블 분리가 감지되어 배터리 진단을 조기 종료하고 분석을 진행합니다."
                    )
                    
                    stopSelf()
                    break
                }

                val now = System.currentTimeMillis()
                if (now - lastUpdateTs >= 10000) {
                    val statusText = "배터리: ${log.level}%, 전류: ${log.current.toInt()}mA (수집: ${collectedCount}건)"
                    updateForegroundNotification(statusText)
                    lastUpdateTs = now
                }

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

        // repository의 sendTelemetryWithFallback 호출하여 MQTT/HTTP 대체 전송 수행
        repository.sendTelemetryWithFallback(
            sessionId = currentSessionId,
            deviceId = activeDeviceId,
            logs = pendingLogs,
            sessionStartTimestamp = sessionStartTimestamp,
            screenState = screenState,
            onSuccess = {
                serviceScope.launch {
                    repository.markAsSent(pendingLogs.map { it.id })
                    Log.d("BatteryService", "텔레메트리 배치 전송 완료 (DB 업데이트): ${pendingLogs.size}건")
                }
            },
            onFailure = { error ->
                Log.e("BatteryService", "텔레메트리 배치 전송 최종 실패 (AWS & HTTP 모두 실패): ${error?.message}")
            }
        )
    }

    private fun getCurrentScreenState(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (preferenceManager.isMonitoringActive() && !preferenceManager.wasMonitoringManuallyStopped()) {
            Log.w("BatteryService", "최근 앱에서 제거됨 → 모니터링 서비스 복구 시도")
            MonitoringServiceStarter.start(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        collectingJob?.cancel()
        flushLoopJob?.cancel()

        if (shouldFinishSessionOnDestroy) {
            finishSessionBlocking()
            preferenceManager.setMonitoringActive(false)
        } else {
            Log.w("BatteryService", "서비스가 명시적 종료 없이 파괴되었습니다. 세션 종료 API는 호출하지 않습니다.")
        }

        awsIoTManager.disconnect()

        serviceScope.cancel()
    }

    private fun finishSessionBlocking() {
        val currentSessionId = sessionId ?: return
        if (isFinishingSession) return

        isFinishingSession = true
        runBlocking(Dispatchers.IO) {
            runCatching {
                flushPendingLogs()
                val sessionLogs = repository.getLogsSince(sessionStartTimestamp)
                val capacityAh = calculateCapacityAh(sessionLogs)
                authRepository.finishBatterySession(
                    sessionId = currentSessionId,
                    powerbankCapacityStartMah = null,
                    sessionStartTs = sessionStartInstant,
                    sessionEndTs = Instant.now(),
                    capacityAh = capacityAh,
                    powerbankCapacityEndMah = null
                ).getOrThrow()
                Log.d("BatteryService", "세션 종료 처리 완료: session_id=$currentSessionId, capacity_ah=$capacityAh")

                // 마지막으로 종료 완료된 세션 ID 저장
                preferenceManager.saveLastSessionId(currentSessionId)

                // AI SOH 예측 실행 및 캐싱
                runCatching {
                    Log.d("BatteryService", "AI SOH 분석 예측 트리거 호출: session_id=$currentSessionId")
                    val predictResult = authRepository.predictSoh(currentSessionId).getOrThrow()
                    val resultResponse = com.han.battery.data.model.SessionResultResponse(
                        id = currentSessionId,
                        status = "COMPLETED",
                        soh_percentage = predictResult.soh_percentage,
                        condition = predictResult.condition,
                        estimated_full_charges = predictResult.estimated_full_charges,
                        powerbank_usable_mah = predictResult.powerbank_usable_mah,
                        smartphone_received_mah = predictResult.smartphone_received_mah,
                        mean_temperature_c = predictResult.mean_temperature_c
                    )
                    preferenceManager.saveLastSessionResult(resultResponse)
                    Log.d("BatteryService", "AI SOH 분석 예측 결과 수신 및 캐시 완료: $resultResponse")

                    showDiagnosisResultNotification(
                        title = "AI 배터리 진단 완료 🔋",
                        message = "건강도(SOH): ${String.format("%.1f", predictResult.soh_percentage)}% | 상태: ${predictResult.condition} (사용 가능 용량: ${predictResult.powerbank_usable_mah}mAh)"
                    )
                }.onFailure { error ->
                    Log.e("BatteryService", "AI SOH 분석 예측 수행 오류: ${error.message}", error)
                    showDiagnosisResultNotification(
                        title = "AI 배터리 진단 실패 ⚠️",
                        message = "진단 데이터를 분석하는 도중 오류가 발생했습니다."
                    )
                }
            }.onFailure { error ->
                Log.e("BatteryService", "세션 종료 처리 실패: ${error.message}", error)
                showDiagnosisResultNotification(
                    title = "AI 배터리 진단 실패 ⚠️",
                    message = "충전 세션을 종료하는 도중 서버 통신에 실패했습니다."
                )
            }
        }
    }

    private fun calculateCapacityAh(logs: List<com.han.battery.data.model.BatteryLog>): Double {
        if (logs.size < 2) return 0.0

        val averageAbsCurrent = logs.map { abs(it.current) }.average()
        val currentValuesAreAmps = averageAbsCurrent < 20.0

        return logs.zipWithNext().sumOf { (previous, next) ->
            val deltaHours = (next.timestamp - previous.timestamp).coerceAtLeast(0L) / 3_600_000.0
            val currentA = if (currentValuesAreAmps) {
                abs(previous.current)
            } else {
                abs(previous.current) / 1000.0
            }
            currentA * deltaHours
        }
    }
}
