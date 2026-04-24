package com.han.battery.ui.landing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.DeviceInfo
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.repository.AuthRepository
import com.han.battery.data.storage.PreferenceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface LandingUiEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : LandingUiEvent
    data class NavigateToDashboard(val modelName: String) : LandingUiEvent
}

class LandingViewModel(
    private val authRepository: AuthRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    private val _events = MutableSharedFlow<LandingUiEvent>()
    val events: SharedFlow<LandingUiEvent> = _events.asSharedFlow()

    fun registerDevice(deviceInfo: DeviceInfo) {
        if (deviceInfo.model_name.isBlank()) {
            viewModelScope.launch {
                _events.emit(LandingUiEvent.ShowMessage("Model name is required.", isError = true))
            }
            return
        }
        if (deviceInfo.powerbank_capacity_mah <= 0) {
            viewModelScope.launch {
                _events.emit(LandingUiEvent.ShowMessage("Capacity must be greater than 0.", isError = true))
            }
            return
        }

        viewModelScope.launch {
            val result = authRepository.registerBattery(
                modelName = deviceInfo.model_name,
                powerbankCapacityMah = deviceInfo.powerbank_capacity_mah,
                manufactureDate = deviceInfo.manufacture_date.ifBlank { null }
            )

            result.onSuccess { batteryResponse ->
                val deviceWithId: BatteryDevice = deviceInfo.copy(id = batteryResponse.id)
                preferenceManager.saveBatteryDevice(deviceWithId)
                _events.emit(LandingUiEvent.ShowMessage("Device registered."))
                _events.emit(LandingUiEvent.NavigateToDashboard(deviceWithId.model_name))
            }.onFailure {
                _events.emit(
                    LandingUiEvent.ShowMessage(
                        it.message ?: "Device registration failed.",
                        isError = true
                    )
                )
            }
        }
    }
}
