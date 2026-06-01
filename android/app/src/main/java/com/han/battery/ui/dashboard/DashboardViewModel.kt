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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    private var manuallyStoppedMonitoring = preferenceManager.wasMonitoringManuallyStopped()

    private val _lastAnalysisResult = MutableStateFlow<SessionResultResponse?>(null)
    val lastAnalysisResult: StateFlow<SessionResultResponse?> = _lastAnalysisResult.asStateFlow()

    private val _isAnyOtherDeviceMonitoring = MutableStateFlow(false)
    val isAnyOtherDeviceMonitoring: StateFlow<Boolean> = _isAnyOtherDeviceMonitoring.asStateFlow()

    private val _events = MutableSharedFlow<DashboardUiEvent>()
    val events: SharedFlow<DashboardUiEvent> = _events.asSharedFlow()

    val telemetryStats = BatteryMonitoringService.telemetryStats.asStateFlow()

    private val _isSessionShared = MutableStateFlow(false)
    val isSessionShared: StateFlow<Boolean> = _isSessionShared.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        monitorBattery()
        loadLastAnalysisResult()
        retryPendingPredictions()
        registerNetworkCallback()
    }

    fun setDevice(device: BatteryDevice) {
        _currentDevice.value = device
        
        val isCurrentDeviceMonitoring = preferenceManager.isMonitoringActive() &&
                (preferenceManager.getMonitoringDeviceId() == device.id)

        if (!preferenceManager.isMonitoringActive()) {
            preferenceManager.setActiveDevice(device)
        }
        
        _isMonitoring.value = isCurrentDeviceMonitoring
        _isAnyOtherDeviceMonitoring.value = preferenceManager.isMonitoringActive() &&
                (preferenceManager.getMonitoringDeviceId() != device.id)
        
        if (isCurrentDeviceMonitoring) {
            ensureMonitoringServiceIfCharging()
        }
        
        // 해당 기기의 최신 AI 진단 결과 로드
        loadLastAnalysisResult(device.id)
    }

    fun loadLastAnalysisResult(deviceId: Int = 0) {
        val targetDeviceId = if (deviceId > 0) deviceId else (_currentDevice.value?.id ?: 0)
        viewModelScope.launch {
            // 1. 로컬 캐시 로드
            val cachedResult = preferenceManager.getLastSessionResult(targetDeviceId)
            if (cachedResult != null) {
                _lastAnalysisResult.value = cachedResult
                _isSessionShared.value = preferenceManager.isSessionShared(cachedResult.id)
                Log.d("DashboardViewModel", "로컬 캐시된 AI 분석 결과 적재 (기기 $targetDeviceId): SOH=${cachedResult.soh_percentage}%")
            } else {
                _lastAnalysisResult.value = null
                _isSessionShared.value = false
            }

            // 2. 서버 최신화 (최근 세션 ID가 있는 경우)
            val lastSessionId = preferenceManager.getLastSessionId(targetDeviceId)
            if (lastSessionId > 0) {
                authRepository.getSessionResult(lastSessionId).onSuccess { response ->
                    if (response.status == "COMPLETED") {
                        _lastAnalysisResult.value = response
                        _isSessionShared.value = preferenceManager.isSessionShared(response.id)
                        preferenceManager.saveLastSessionResult(response, targetDeviceId)
                        Log.d("DashboardViewModel", "서버 실시간 갱신 완료 (기기 $targetDeviceId): SOH=${response.soh_percentage}%")
                    }
                }.onFailure { error ->
                    Log.e("DashboardViewModel", "서버 최신 결과 조회 실패: ${error.message}")
                }
            } else if (targetDeviceId > 0) {
                // 앱 삭제 후 재설치 등으로 로컬 세션 ID가 0인 경우, 기기별 최신 완료 세션 결과를 서버로부터 역조회합니다.
                authRepository.getLatestDeviceResult(targetDeviceId).onSuccess { response ->
                    _lastAnalysisResult.value = response
                    _isSessionShared.value = preferenceManager.isSessionShared(response.id)
                    // 로컬 캐시(세션 ID 및 결과) 복구
                    preferenceManager.saveLastSessionId(response.id, targetDeviceId)
                    preferenceManager.saveLastSessionResult(response, targetDeviceId)
                    Log.d("DashboardViewModel", "서버 최신 기기 결과 역조회 완료 및 로컬 캐시 복구 (기기 $targetDeviceId): session=${response.id}, SOH=${response.soh_percentage}%")
                }.onFailure { error ->
                    Log.d("DashboardViewModel", "서버 최신 기기 결과 없음 (신규 기기 또는 진단 이력 없음): ${error.message}")
                    _lastAnalysisResult.value = null
                    _isSessionShared.value = false
                }
            }
        }
    }

    fun startMonitoring() {
        val device = _currentDevice.value
        if (device == null) {
            Log.w("DashboardViewModel", "등록된 기기가 없어 시작할 수 없습니다.")
            return
        }

        preferenceManager.setActiveDevice(device)
        preferenceManager.saveMonitoringDeviceId(device.id)

        manuallyStoppedMonitoring = false
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
        preferenceManager.setMonitoringManuallyStopped(false)
        preferenceManager.setMonitoringActive(true)
        
        _isAnyOtherDeviceMonitoring.value = false

        MonitoringRecoveryWorker.enqueue(context)
        MonitoringServiceStarter.start(context)
        _isMonitoring.value = true
        Log.d("DashboardViewModel", "기기 ${device.model_name}(ID:${device.id}) 진단 시작")
    }

    fun stopMonitoring() {
        stopMonitoring(manualStop = true)
    }

    private fun stopMonitoring(manualStop: Boolean) {
        manuallyStoppedMonitoring = manualStop
        preferenceManager.setMonitoringManuallyStopped(manualStop)
        preferenceManager.setMonitoringActive(false)
        preferenceManager.saveMonitoringDeviceId(0)
        MonitoringServiceStarter.stop(context)
        _isMonitoring.value = false
        val currentId = _currentDevice.value?.id ?: 0
        Log.d("DashboardViewModel", if (manualStop) "사용자 요청으로 모니터링 서비스를 종료합니다." else "충전 종료로 모니터링 서비스를 종료합니다.")

        // 2초 뒤 분석 결과 로드 (백그라운드 서비스의 세션 종료 및 AI 예측 비동기 완료 대기)
        viewModelScope.launch {
            delay(2000)
            loadLastAnalysisResult(currentId)
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

                val activeDeviceId = preferenceManager.getMonitoringDeviceId()
                val currentId = _currentDevice.value?.id ?: 0

                _isAnyOtherDeviceMonitoring.value = preferenceManager.isMonitoringActive() &&
                        (activeDeviceId != currentId)

                // ✅ 핵심: 진단 중인 기기의 화면일 때만 충전 해제 감지 시 중단 및 UI 업데이트 실행
                if (activeDeviceId == currentId && !newStatus.isCharging && _isMonitoring.value) {
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

        val analysisResult = _lastAnalysisResult.value
        if (analysisResult == null) {
            viewModelScope.launch {
                _events.emit(
                    DashboardUiEvent.ShowMessage(
                        "아직 배터리 진단 결과가 존재하지 않습니다. 먼저 기기를 충전 전원에 20초 이상 연결하여 첫 AI 진단을 완료해 주세요.",
                        isError = true
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            communityRepository.shareDevice(device.id)
                .onSuccess {
                    preferenceManager.setSessionShared(analysisResult.id, true)
                    _isSessionShared.value = true
                    _events.emit(DashboardUiEvent.ShowMessage("커뮤니티에 진단 결과를 성공적으로 공유했습니다."))
                }
                .onFailure { error ->
                    _events.emit(DashboardUiEvent.ShowMessage(error.message ?: "커뮤니티 공유에 실패했습니다.", isError = true))
                }
        }
    }

    fun retryPendingPredictions() {
        val pending = preferenceManager.getPendingPredictions()
        if (pending.isEmpty()) return

        viewModelScope.launch {
            Log.d("DashboardViewModel", "미완료 세션 AI 분석 재시도 시작 (대시보드 진입). 대수: ${pending.size}개")
            pending.forEach { (sessId, devId) ->
                authRepository.predictSoh(sessId).onSuccess { predictResult ->
                    val resultResponse = SessionResultResponse(
                        id = sessId,
                        status = "COMPLETED",
                        soh_percentage = predictResult.soh_percentage,
                        condition = predictResult.condition,
                        estimated_full_charges = predictResult.estimated_full_charges,
                        powerbank_usable_mah = predictResult.powerbank_usable_mah,
                        smartphone_received_mah = predictResult.smartphone_received_mah,
                        mean_temperature_c = predictResult.mean_temperature_c,
                        confidence = predictResult.confidence
                    )
                    preferenceManager.saveLastSessionResult(resultResponse, devId)
                    preferenceManager.removePendingPrediction(sessId)
                    Log.d("DashboardViewModel", "미완료 세션 분석 및 캐시 복구 완료: sessionId=$sessId")

                    if (_currentDevice.value?.id == devId) {
                        _lastAnalysisResult.value = resultResponse
                        _isSessionShared.value = preferenceManager.isSessionShared(sessId)
                    }
                }.onFailure { error ->
                    Log.e("DashboardViewModel", "미완료 세션 $sessId 분석 재시도 실패: ${error.message}")
                    val rawMsg = error.message ?: ""
                    if (rawMsg.contains("at least 3 valid charging sessions") || 
                        rawMsg.contains("charging sessions") || 
                        rawMsg.contains("404") || 
                        rawMsg.contains("400")
                    ) {
                        preferenceManager.removePendingPrediction(sessId)
                        Log.d("DashboardViewModel", "영구 실패 에러 감지로 미완료 세션 $sessId 대기열에서 제거")
                    }
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) {
            Log.d("DashboardViewModel", "NetworkCallback이 이미 등록되어 있어 중복 등록을 스킵합니다.")
            return
        }
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("DashboardViewModel", "네트워크 복구 감지 (대시보드) -> AI 분석 재시도")
                    retryPendingPredictions()
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "NetworkCallback 등록 실패", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            networkCallback?.let {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "NetworkCallback 해제 실패", e)
        }
    }
}