package com.han.battery.data.common

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.han.battery.data.model.BatteryLog

object BatteryUtils {
    fun getCurrentBatteryState(context: Context): BatteryLog {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        return BatteryLog(
            timestamp = System.currentTimeMillis(),
            voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1,
            current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000.0, // mA 단위
            level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
            temperature = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        )
    }
}
