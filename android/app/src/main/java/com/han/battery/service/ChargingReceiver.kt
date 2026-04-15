package com.han.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userManager = UserManager(context)
        val preferenceManager = PreferenceManager(context, userManager)

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d("ChargingReceiver", "충전 시작 감지!")
                if (preferenceManager.isDeviceRegistered()) {
                    Log.d("ChargingReceiver", "등록된 기기 있음 → 서비스 시작")
                    val serviceIntent = Intent(context, BatteryMonitoringService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    Log.d("ChargingReceiver", "등록된 기기 없음 → 서비스 시작 안 함")
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d("ChargingReceiver", "충전 종료 감지 → 서비스 중지")
                val serviceIntent = Intent(context, BatteryMonitoringService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}