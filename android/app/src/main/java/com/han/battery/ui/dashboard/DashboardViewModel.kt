package com.han.battery.ui.dashboard

import androidx.lifecycle.ViewModel
import com.han.battery.data.model.BatteryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel : ViewModel() {
    // 실시간 상태를 담는 StateFlow (UI가 관찰함)
    private val _batteryStatus = MutableStateFlow(BatteryStatus(soc = 78, current = 2400, voltage = 5.1))
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus

    // 나중에 Member B가 여기서 실시간 수집 로직을 연결할 예정입니다.
}