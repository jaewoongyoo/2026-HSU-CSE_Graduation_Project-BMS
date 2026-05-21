package com.han.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val preferenceManager = PreferenceManager(context, UserManager(context))
        MonitoringRecoveryWorker.enqueue(context)

        if (preferenceManager.isMonitoringActive() &&
            !preferenceManager.wasMonitoringManuallyStopped() &&
            preferenceManager.isDeviceRegistered()
        ) {
            Log.d("BootReceiver", "부팅 후 이전 진단 상태 감지 → 모니터링 서비스 복구")
            MonitoringServiceStarter.start(context)
        }
    }
}
