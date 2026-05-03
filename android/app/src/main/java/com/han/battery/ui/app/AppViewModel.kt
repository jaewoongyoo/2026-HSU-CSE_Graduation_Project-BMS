package com.han.battery.ui.app

import androidx.lifecycle.ViewModel
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.repository.AuthRepository
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager

class AppViewModel(
    private val userManager: UserManager,
    private val authRepository: AuthRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    fun isLoggedIn(): Boolean = userManager.isLoggedIn()

    suspend fun syncDevicesFromServer(): Result<List<BatteryDevice>> {
        return authRepository.getCurrentUserBatteries().mapCatching { batteries ->
            val syncedDevices = batteries.mapNotNull { battery ->
                runCatching {
                    BatteryDevice(
                        manufacturer = battery.manufacturer.orEmpty(),
                        model_name = battery.model_name,
                        powerbank_capacity_mah = battery.powerbank_capacity_mah ?: 0,
                        manufacture_date = battery.manufacture_date.orEmpty(),
                        id = battery.id
                    )
                }.getOrNull()
            }

            preferenceManager.mergeDevicesFromServer(syncedDevices)
            preferenceManager.getAllDevices()
        }
    }
}
