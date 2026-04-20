package com.han.battery.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.han.battery.data.common.BatteryUtils
import com.han.battery.data.repository.AWSIoTManager
import com.han.battery.data.repository.BatteryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

// ✅ 핵심 수정 1: @AndroidEntryPoint만 달고, 평범하게 Service()를 상속받습니다!
@AndroidEntryPoint
class BatteryMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // ✅ 핵심 수정 2: 네이밍 경고 해결 (대문자 CHANNEL_ID -> 소문자 카멜케이스 channelId)
    private val channelId = "battery_monitoring_channel"

    @Inject lateinit var repository: BatteryRepository
    @Inject lateinit var awsIoTManager: AWSIoTManager

    private var collectingJob: Job? = null
    private var flushJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()

        // ⚠️ Settings.Secure.ANDROID_ID 사용 경고(Warning)는 무시하셔도 됩니다.
        // (구글이 개인정보 보호차원에서 권장하지 않는다는 단순 경고이며, 우리 프로젝트에선 괜찮습니다!)
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

        // ✅ 핵심 수정 3: 불필요한 버전 체크 삭제 경고 해결 (minSdk가 이미 26 이상이므로 if문 생략 가능)
        val channel = NotificationChannel(
            channelId,
            "배터리 모니터링",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
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
        // 1. 2초마다 수집해서 DB에 넣는 일꾼
        serviceScope.launch {
            while (isActive) {
                val log = BatteryUtils.getCurrentBatteryState(applicationContext, "my_device")
                repository.insertLog(log) // DB에 차곡차곡 저장
                Log.d("BatteryService", "2초 주기 수집 완료")
                delay(2000) // 2초 대기
            }
        }

        // 2. 1분마다 DB를 뒤져서 AWS로 쏘는 일꾼
        serviceScope.launch {
            while (isActive) {
                delay(60000) // 1분 대기
                val pendingLogs = repository.getUnsentLogs() // DB에서 안 보낸 거 싹 가져오기
                if (pendingLogs.isNotEmpty()) {
                    flushPendingLogs() // AWS로 전송 실행
                    Log.d("BatteryService", "1분 주기 묶음 전송 완료: ${pendingLogs.size}건")
                }
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