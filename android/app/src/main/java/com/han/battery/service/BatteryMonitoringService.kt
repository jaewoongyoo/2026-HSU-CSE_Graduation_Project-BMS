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

data class TelemetryStats(
    val totalCollected: Int = 0,
    val mqttSuccess: Int = 0,
    val httpSuccess: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0
)

class BatteryMonitoringService : Service() {
    companion object {
        const val ACTION_START_MONITORING = "com.han.battery.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.han.battery.action.STOP_MONITORING"
        
        val telemetryStats = kotlinx.coroutines.flow.MutableStateFlow(TelemetryStats())
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "battery_monitoring_channel"
    private var wakeLock: PowerManager.WakeLock? = null
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
    private var startPluggedType: Int = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()

        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BatteryInsight::MonitoringWakeLock"
            ).apply {
                acquire()
            }
            Log.d("BatteryService", "WakeLock 획득 완료 (Deep Sleep 방지)")
        }

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
        preferenceManager.saveMonitoringDeviceId(activeDeviceId)

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
            .setStyle(Notification.BigTextStyle().bigText(contentText))
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
            .setStyle(Notification.BigTextStyle().bigText(message))
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
            telemetryStats.value = TelemetryStats() // 수집 시작 시 통계 초기화
            var lastUpdateTs = 0L
            startPluggedType = getPluggedType(applicationContext)
            Log.d("BatteryService", "수집 시작 시 충전 공급원 plugged type: $startPluggedType")

            while (isActive) {
                try {
                    val log = BatteryUtils.getCurrentBatteryState(applicationContext)
                    repository.insertLog(log)
                    collectedCount++
                    
                    // 실시간 수집 통계 갱신 (pending 건수 계산)
                    val currentStats = telemetryStats.value
                    val newPending = collectedCount - currentStats.mqttSuccess - currentStats.httpSuccess
                    telemetryStats.value = currentStats.copy(
                        totalCollected = collectedCount,
                        pending = newPending.coerceAtLeast(0)
                    )
                    
                    val currentPlugged = getPluggedType(applicationContext)
                    
                    if (!log.isCharging || currentPlugged == 0) {
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
                        val targetCount = 900 // 권장 수집 목표 (30분, 2초 주기 = 900건)
                        val minTargetCount = 600 // 최소 수집 목표 (20분, 2초 주기 = 600건)
                        val progressPercent = ((collectedCount.toFloat() / targetCount.toFloat()) * 100).toInt().coerceAtMost(100)
                        
                        val progressText = when {
                            collectedCount >= targetCount -> "권장 진단 완료! (종료하셔도 좋습니다)"
                            collectedCount >= minTargetCount -> "최소 진단 가능 ($progressPercent%) | 신뢰도를 높이려면 더 충전하세요."
                            else -> "진단 분석 중: $progressPercent% (최소 20분 충전 필요)"
                        }
                        
                        val statusText = "$progressText\n배터리: ${log.level}%, 전류: ${log.current.toInt()}mA (수집: ${collectedCount}건)"
                        updateForegroundNotification(statusText)
                        lastUpdateTs = now
                    }
                } catch (e: Exception) {
                    Log.e("BatteryService", "Telemetry 수집 주기 중 비정상적인 예외 발생! ⚠️ 수집은 유지합니다.", e)
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
            onSuccess = { isMqtt ->
                serviceScope.launch {
                    repository.markAsSent(pendingLogs.map { it.id })
                    Log.d("BatteryService", "텔레메트리 배치 전송 완료 (DB 업데이트): ${pendingLogs.size}건")
                    
                    // 전송 통계 실시간 갱신
                    val currentStats = telemetryStats.value
                    val addedCount = pendingLogs.size
                    val newMqttSuccess = if (isMqtt) currentStats.mqttSuccess + addedCount else currentStats.mqttSuccess
                    val newHttpSuccess = if (!isMqtt) currentStats.httpSuccess + addedCount else currentStats.httpSuccess
                    val newPending = (currentStats.totalCollected - newMqttSuccess - newHttpSuccess).coerceAtLeast(0)
                    
                    telemetryStats.value = currentStats.copy(
                        mqttSuccess = newMqttSuccess,
                        httpSuccess = newHttpSuccess,
                        pending = newPending
                    )
                }
            },
            onFailure = { error ->
                Log.e("BatteryService", "텔레메트리 배치 전송 최종 실패 (AWS & HTTP 모두 실패): ${error?.message}")
                
                // 실패 통계 실시간 갱신
                val currentStats = telemetryStats.value
                val addedCount = pendingLogs.size
                val newFailed = currentStats.failed + addedCount
                val newPending = (currentStats.totalCollected - currentStats.mqttSuccess - currentStats.httpSuccess).coerceAtLeast(0)
                
                telemetryStats.value = currentStats.copy(
                    failed = newFailed,
                    pending = newPending
                )
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
        preferenceManager.saveMonitoringDeviceId(0)

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("BatteryService", "WakeLock 해제 완료")
            }
        }
        wakeLock = null

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

                // 🛡️ [방어 로직] 수집된 로그가 너무 적은 경우 (단일 세션 SOH 최소 조건 10개 미만, 약 20초 충전 시)
                if (sessionLogs.size < 10) {
                    Log.w("BatteryService", "수집 데이터 부족으로 SOH 예측을 생략하고 세션만 종료합니다. (수집 건수: ${sessionLogs.size}건)")
                    showDiagnosisResultNotification(
                        title = "AI 배터리 진단 중단 ⚠️",
                        message = "충전 시간이 너무 짧아 데이터가 부족합니다. 최소 20초 이상 충전을 유지해 주세요."
                    )
                    return@runCatching
                }

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
                    val rawMsg = error.message ?: ""
                    val friendlyMessage = when {
                        rawMsg.contains("at least 3 valid charging sessions") || rawMsg.contains("charging sessions") -> {
                            "AI 분석을 위한 충전 데이터가 아직 충분하지 않습니다. 신뢰도 높은 개인화 진단을 위해 보조배터리를 20분 이상 충전하는 과정을 반복해 주세요."
                        }
                        else -> "진단 데이터를 분석하는 도중 오류가 발생했습니다. (사유: ${error.message})"
                    }
                    showDiagnosisResultNotification(
                        title = "AI 배터리 진단 실패 ⚠️",
                        message = friendlyMessage
                    )
                }
            }.onFailure { error ->
                Log.e("BatteryService", "세션 종료 처리 실패: ${error.message}", error)
                showDiagnosisResultNotification(
                    title = "AI 배터리 진단 실패 ⚠️",
                    message = "충전 세션을 종료하는 도중 서버 통신에 실패했습니다. (사유: ${error.message})"
                )
            }
        }
    }

    private fun calculateCapacityAh(logs: List<com.han.battery.data.model.BatteryLog>): Double {
        if (logs.size < 2) return 0.0

        var sum = 0.0
        for (i in logs.indices) {
            sum += abs(logs[i].current)
        }
        val averageAbsCurrent = sum / logs.size
        val currentValuesAreAmps = averageAbsCurrent < 20.0

        var capacitySum = 0.0
        for (i in 0 until logs.size - 1) {
            val previous = logs[i]
            val next = logs[i + 1]
            val deltaHours = (next.timestamp - previous.timestamp).coerceAtLeast(0L) / 3_600_000.0
            val currentA = if (currentValuesAreAmps) {
                abs(previous.current)
            } else {
                abs(previous.current) / 1000.0
            }
            capacitySum += currentA * deltaHours
        }
        return capacitySum
    }

    private fun getPluggedType(context: Context): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    }
}
