package com.han.battery.service

import android.content.Context
import android.content.Intent
import android.os.Build

object MonitoringServiceStarter {
    fun start(context: Context) {
        val intent = Intent(context, BatteryMonitoringService::class.java).apply {
            action = BatteryMonitoringService.ACTION_START_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context, BatteryMonitoringService::class.java).apply {
            action = BatteryMonitoringService.ACTION_STOP_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
