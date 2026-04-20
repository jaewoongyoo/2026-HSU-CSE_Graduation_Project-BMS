package com.han.battery.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.BatteryApplication
import com.han.battery.data.model.BatteryDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface HomeUiEvent {
    data class ShowMessage(val message: String, val isError: Boolean = false) : HomeUiEvent
    data object NavigateToLogin : HomeUiEvent
    data object NavigateToHome : HomeUiEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BatteryApplication

    private val _devices = MutableStateFlow<List<BatteryDevice>>(emptyList())
    val devices: StateFlow<List<BatteryDevice>> = _devices.asStateFlow()
    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        _devices.value = app.preferenceManager.getAllDevices()
    }

    fun getDevice(modelName: String): BatteryDevice? {
        return _devices.value.find { it.model_name == modelName }
            ?: app.preferenceManager.getBatteryDevice(modelName)
    }

    fun deleteDevice(device: BatteryDevice, navigateHomeAfterDelete: Boolean = false) {
        viewModelScope.launch {
            val deleteResult = if (device.id > 0) {
                app.authRepository.deleteBattery(device.id)
            } else {
                Result.success("Device deleted locally.")
            }

            deleteResult.onSuccess {
                app.preferenceManager.deleteDevice(device.model_name)
                refreshDevices()
                _events.emit(HomeUiEvent.ShowMessage(it.ifBlank { "Device deleted successfully." }))
                if (navigateHomeAfterDelete) {
                    _events.emit(HomeUiEvent.NavigateToHome)
                }
            }.onFailure {
                _events.emit(
                    HomeUiEvent.ShowMessage(
                        it.message ?: "Failed to delete device from server.",
                        isError = true
                    )
                )
            }
        }
    }

    fun logout() {
        app.authRepository.logout()
        _devices.value = emptyList()
        viewModelScope.launch {
            _events.emit(HomeUiEvent.NavigateToLogin)
        }
    }
}
