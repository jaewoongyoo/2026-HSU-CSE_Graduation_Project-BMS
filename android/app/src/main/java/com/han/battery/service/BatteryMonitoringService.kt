package com.han.battery.service
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.han.battery.BatteryApplication
import com.han.battery.data.common.BatteryUtils
import com.han.battery.data.repository.AWSIoTManager
import com.han.battery.data.repository.BatteryRepository
import kotlinx.coroutines.*

class BatteryMonitoringService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "battery_monitoring_channel"
    private lateinit var repository: BatteryRepository
    private lateinit var awsIoTManager: AWSIoTManager
    private var collectingJob: Job? = null
    private var flushJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as BatteryApplication
        repository = app.repository
        awsIoTManager = app.awsIoTManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        // ✅ 수정: 기기 고유 ID 사용 (재시작해도 항상 동일)
        val clientId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
        Log.d("AWSIoTManager", "연결 시도 ClientID: $clientId")
        awsIoTManager.initAndConnect(clientId) {
            serviceScope.launch {
                flushPendingLogs()
            }
        }
        startCollecting()
        return START_STICKY
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

        repository.sendToAWS(
            logs = pendingLogs,
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        collectingJob?.cancel()
        flushJob?.cancel()
        serviceScope.cancel()
    }
}
