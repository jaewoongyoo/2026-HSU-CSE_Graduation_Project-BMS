package com.han.battery.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.data.model.BatteryStatus
import com.han.battery.data.model.BatteryDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 배터리 실시간 데이터 수집(Logic)과 기기 정보 관리(UI State)를 모두 담당하는 통합 ViewModel
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // ── 1. 기기 정보 관리 (Member A의 코드 반영) ──
    private val _currentDevice = MutableStateFlow<BatteryDevice?>(null)
    val currentDevice: StateFlow<BatteryDevice?> = _currentDevice.asStateFlow()

    // ── 2. 실시간 배터리 상태 (Member B의 로직 반영) ──
    private val _batteryStatus = MutableStateFlow(BatteryStatus())
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()

    init {
        // 앱 시작 시 실제 배터리 모니터링 루프 가동
        monitorBattery()
    }

    /**
     * 배터리 기기 정보를 설정합니다.
     */
    fun setDevice(device: BatteryDevice) {
        _currentDevice.value = device
    }

    /**
     * 2초마다 안드로이드 시스템으로부터 실제 배터리 정보를 갱신합니다.
     */
    private fun monitorBattery() {
        viewModelScope.launch {
            while (true) {
                _batteryStatus.value = getRealBatteryInfo()
                delay(2000) // 갱신 주기
            }
        }
    }

    /**
     * BatteryManager를 통해 하드웨어 센서 데이터를 직접 수집합니다.
     */
    private fun getRealBatteryInfo(): BatteryStatus {
        val context = getApplication<Application>().applicationContext
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        // 현재 배터리 상태 스냅샷 가져오기
        val batteryStatusIntent = context.registerReceiver(null, intentFilter)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        return batteryStatusIntent?.let { intent ->
            // 1. 잔량(SOC) 계산
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val soc = if (level != -1 && scale != -1) (level / scale.toFloat() * 100).toInt() else 0

            // 2. 전압(V) 및 온도(°C)
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

            // 3. 전류(mA) - 기기에 따라 음수(방전)/양수(충전) 확인 필요
            val currentNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000f

            // 4. 충전 상태 확인
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // 5. 완충 예상 시간 계산 (자체 계산 없이 시스템 API만 사용)
            var remainingMinutes = -1L
            if (isCharging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val ms = batteryManager.computeChargeTimeRemaining()
                // 시스템이 계산 중이거나 알 수 없을 때는 ms가 -1로 옵니다.
                if (ms > 0) {
                    remainingMinutes = ms / 1000 / 60
                }
            }

            BatteryStatus(
                soc = soc,
                voltage = voltage,
                current = currentNow,
                temperature = temperature,
                isCharging = isCharging,
                remainingTime = remainingMinutes // 이 값이 -1이면 UI에서 "계산 중..." 표시
            )
        } ?: BatteryStatus()
    }
}