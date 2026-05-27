package com.han.battery.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.data.common.BatteryOptimizationHelper
import com.han.battery.data.model.BatteryStatus
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.model.SessionResultResponse
import com.han.battery.data.repository.AuthRepository
import com.han.battery.data.repository.CommunityRepository
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.service.BatteryMonitoringService
import com.han.battery.service.MonitoringRecoveryWorker
import com.han.battery.service.MonitoringServiceStarter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DashboardUiEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : DashboardUiEvent
}

class DashboardViewModel(
    private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val authRepository: AuthRepository,
    private val communityRepository: CommunityRepository
) : ViewModel() {

    private val _currentDevice = MutableStateFlow<BatteryDevice?>(null)
    val currentDevice: StateFlow<BatteryDevice?> = _currentDevice.asStateFlow()

    private val _batteryStatus = MutableStateFlow(BatteryStatus())
    val batteryStatus: StateFlow<BatteryStatus> = _batteryStatus.asStateFlow()

    private val _isMonitoring = MutableStateFlow(preferenceManager.isMonitoringActive())
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    private var manuallyStoppedMonitoring = preferenceManager.wasMonitoringManuallyStopped()

    private val _lastAnalysisResult = MutableStateFlow<SessionResultResponse?>(null)
    val lastAnalysisResult: StateFlow<SessionResultResponse?> = _lastAnalysisResult.asStateFlow()

    private val _events = MutableSharedFlow<DashboardUiEvent>()
    val events: SharedFlow<DashboardUiEvent> = _events.asSharedFlow()

    val telemetryStats = BatteryMonitoringService.telemetryStats.asStateFlow()

    init {
        monitorBattery()
        loadLastAnalysisResult()
    }

    fun setDevice(device: BatteryDevice) {
        _currentDevice.value = device
        preferenceManager.setActiveDevice(device)
        if (!manuallyStoppedMonitoring) {
            ensureMonitoringServiceIfCharging()
        }
    }

    fun loadLastAnalysisResult() {
        viewModelScope.launch {
            // 1. 로컬 캐시 로드
            val cachedResult = preferenceManager.getLastSessionResult()
            if (cachedResult != null) {
                _lastAnalysisResult.value = cachedResult
                Log.d("DashboardViewModel", "로컬 캐시된 AI 분석 결과 적재: SOH=${cachedResult.soh_percentage}%")
            }

            // 2. 서버 최신화 (최근 세션 ID가 있는 경우)
            val lastSessionId = preferenceManager.getLastSessionId()
            if (lastSessionId > 0) {
                authRepository.getSessionResult(lastSessionId).onSuccess { response ->
                    if (response.status == "COMPLETED") {
                        _lastAnalysisResult.value = response
                        preferenceManager.saveLastSessionResult(response)
                        Log.d("DashboardViewModel", "서버 실시간 갱신 완료: SOH=${response.soh_percentage}%")
                    }
                }.onFailure { error ->
                    Log.e("DashboardViewModel", "서버 최신 결과 조회 실패: ${error.message}")
                }
            }
        }
    }

    fun startMonitoring() {
        val activeDevice = preferenceManager.getActiveDevice()
        if (activeDevice == null) {
            Log.w("DashboardViewModel", "등록된 기기가 없어 시작할 수 없습니다.")
            return
        }

        manuallyStoppedMonitoring = false
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
        preferenceManager.setMonitoringManuallyStopped(false)
        preferenceManager.setMonitoringActive(true)
        MonitoringRecoveryWorker.enqueue(context)
        MonitoringServiceStarter.start(context)
        _isMonitoring.value = true
        Log.d("DashboardViewModel", "진단 시작")
    }

    fun stopMonitoring() {
        stopMonitoring(manualStop = true)
    }

    private fun stopMonitoring(manualStop: Boolean) {
        manuallyStoppedMonitoring = manualStop
        preferenceManager.setMonitoringManuallyStopped(manualStop)
        preferenceManager.setMonitoringActive(false)
        MonitoringServiceStarter.stop(context)
        _isMonitoring.value = false
        Log.d("DashboardViewModel", if (manualStop) "사용자 요청으로 모니터링 서비스를 종료합니다." else "충전 종료로 모니터링 서비스를 종료합니다.")

        // 2초 뒤 분석 결과 로드 (백그라운드 서비스의 세션 종료 및 AI 예측 비동기 완료 대기)
        viewModelScope.launch {
            delay(2000)
            loadLastAnalysisResult()
        }
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

        MonitoringRecoveryWorker.enqueue(context)
        MonitoringServiceStarter.start(context)

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
                    stopMonitoring(manualStop = false)
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

    fun shareActiveDeviceToCommunity() {
        val device = _currentDevice.value
        if (device == null) {
            viewModelScope.launch {
                _events.emit(DashboardUiEvent.ShowMessage("공유할 기기 정보가 없습니다.", isError = true))
            }
            return
        }
        if (device.id <= 0) {
            viewModelScope.launch {
                _events.emit(DashboardUiEvent.ShowMessage("서버에 등록되지 않은 기기는 공유할 수 없습니다.", isError = true))
            }
            return
        }

        viewModelScope.launch {
            communityRepository.shareDevice(device.id)
                .onSuccess {
                    _events.emit(DashboardUiEvent.ShowMessage("커뮤니티에 진단 결과를 성공적으로 공유했습니다."))
                }
                .onFailure { error ->
                    _events.emit(DashboardUiEvent.ShowMessage(error.message ?: "커뮤니티 공유에 실패했습니다.", isError = true))
                }
        }
    }
}