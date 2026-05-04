package com.han.battery.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.data.model.BatteryStatus
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.service.BatteryMonitoringService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _currentDevice = MutableStateFlow<BatteryDevice?>(null)
    val currentDevice: StateFlow<BatteryDevice?> = _currentDevice.asStateFlow()

    private val _batteryStatus = MutableStateFlow(BatteryStatus())
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()

    // 초기값은 항상 false (진입 시 "AI 진단 시작" 보장)
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    private var manuallyStoppedMonitoring = false

    init {
        monitorBattery()
    }

    fun setDevice(device: BatteryDevice) {
        _currentDevice.value = device
        preferenceManager.setActiveDevice(device)
        if (!manuallyStoppedMonitoring) {
            ensureMonitoringServiceIfCharging()
        }
    }

    fun startMonitoring() {
        val activeDevice = preferenceManager.getActiveDevice()
        if (activeDevice == null) {
            Log.w("DashboardViewModel", "등록된 기기가 없어 시작할 수 없습니다.")
            return
        }

        manuallyStoppedMonitoring = false
        val serviceIntent = Intent(context, BatteryMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        _isMonitoring.value = true
        Log.d("DashboardViewModel", "진단 시작")
    }

    fun stopMonitoring() {
        manuallyStoppedMonitoring = true
        val serviceIntent = Intent(context, BatteryMonitoringService::class.java)
        context.stopService(serviceIntent)
        _isMonitoring.value = false
        Log.d("DashboardViewModel", "사용자 요청으로 모니터링 서비스를 종료합니다.")
    }

    private fun ensureMonitoringServiceIfCharging() {
        val batteryStatusIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return

        val status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (!isCharging) {
            manuallyStoppedMonitoring = false
            Log.d("DashboardViewModel", "현재 충전 중이 아니어서 모니터링 서비스를 시작하지 않습니다.")
            return
        }

        val serviceIntent = Intent(context, BatteryMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        _isMonitoring.value = true // 상태 업데이트 추가
        Log.d("DashboardViewModel", "현재 충전 중이므로 모니터링 서비스를 시작합니다.")
    }

    private fun monitorBattery() {
        viewModelScope.launch {
            while (true) {
                val newStatus = getRealBatteryInfo()
                _batteryStatus.value = newStatus

                // ✅ 핵심: 진단 중인데 충전선이 뽑히면 즉시 중단 및 UI 업데이트
                if (!newStatus.isCharging && _isMonitoring.value) {
                    stopMonitoring()
                }
                delay(2000)
            }
        }
    }

    private fun getRealBatteryInfo(): BatteryStatus {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatusIntent = context.registerReceiver(null, intentFilter)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        return batteryStatusIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val soc = if (level != -1 && scale != -1) (level / scale.toFloat() * 100).toInt() else 0
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val rawCurrent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val currentNow = Math.abs(rawCurrent) / 1000f
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            var remainingMinutes = -1L
            if (isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val ms = batteryManager.computeChargeTimeRemaining()
                if (ms > 0) remainingMinutes = ms / 1000 / 60
            }

            BatteryStatus(
                soc = soc, voltage = voltage, current = currentNow,
                temperature = temperature, isCharging = isCharging,
                remainingTime = remainingMinutes
            )
        } ?: BatteryStatus()
    }
}