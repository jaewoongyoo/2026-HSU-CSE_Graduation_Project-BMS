package com.han.battery.ui.dashboard
// 대시보드 화면의 배터리 데이터 상태 관리 (현재 상태, 예측 데이터 등)

import androidx.lifecycle.ViewModel
import com.han.battery.data.model.BatteryStatus
import com.han.battery.data.model.BatteryDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * 배터리 정보와 상태를 관리하는 ViewModel
 */
class DashboardViewModel : ViewModel() {
    
    // 현재 선택된 배터리 기기 정보
    private val _currentDevice = MutableStateFlow<BatteryDevice?>(null)
    val currentDevice: StateFlow<BatteryDevice?> = _currentDevice.asStateFlow()
    
    // 실시간 배터리 상태 정보
    private val _batteryStatus = MutableStateFlow(
        BatteryStatus(
            soc = 78,      // 충전량 78%
            soh = 92,      // 건강도 92%
            current = 2400, // 전류 2400mA
            voltage = 5.1,  // 전압 5.1V
            power = 12.2    // 전력 12.2W
        )
    )
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()
    
    private val random = Random
    
    /**
     * 배터리 기기 정보를 설정합니다.
     * @param device 설정할 BatteryDevice
     */
    fun setDevice(device: BatteryDevice) {
        _currentDevice.value = device
        // TODO: 기기별 배터리 상태 로드 로직 추가
        initializeBatteryStatus(device)
    }
    
    /**
     * 배터리 상태 정보를 초기화합니다.
     * 실제 구현에서는 센서에서 데이터를 수집할 예정입니다.
     */
    private fun initializeBatteryStatus(device: BatteryDevice) {
        // TODO: 실제 배터리 상태 데이터 수집 로직
        // 현재는 더미 데이터 사용
        _batteryStatus.value = BatteryStatus(
            soc = random.nextInt(60, 96),
            soh = random.nextInt(80, 101),
            current = random.nextInt(1000, 3001),
            voltage = 4.8 + (random.nextDouble() * 0.4),
            power = 10.0 + (random.nextDouble() * 10.0)
        )
    }
    
    /**
     * 배터리 상태를 실시간으로 업데이트합니다.
     * 별도 스레드/코루틴에서 주기적으로 호출될 예정입니다.
     */
    fun updateBatteryStatus(status: BatteryStatus) {
        _batteryStatus.value = status
    }
}