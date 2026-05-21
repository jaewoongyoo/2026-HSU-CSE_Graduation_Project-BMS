package com.han.battery.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager
import java.util.concurrent.TimeUnit

class MonitoringRecoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferenceManager = PreferenceManager(applicationContext, UserManager(applicationContext))

        val shouldRecover = preferenceManager.isMonitoringActive() &&
            !preferenceManager.wasMonitoringManuallyStopped() &&
            preferenceManager.isDeviceRegistered() &&
            isCharging(applicationContext)

        if (shouldRecover) {
            Log.d(TAG, "모니터링 복구 조건 충족 → 서비스 시작 요청")
            MonitoringServiceStarter.start(applicationContext)
        }

        return Result.success()
    }

    private fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    companion object {
        private const val TAG = "MonitoringRecovery"
        private const val WORK_NAME = "battery_monitoring_recovery"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitoringRecoveryWorker>(
                15,
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
