package com.han.battery.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.han.battery.BatteryApplication
import com.han.battery.data.model.BatteryDevice

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BatteryApplication

    fun isLoggedIn(): Boolean = app.userManager.isLoggedIn()

    suspend fun syncDevicesFromServer(): Result<List<BatteryDevice>> {
        return app.authRepository.getBatteries().mapCatching { batteries ->
            val syncedDevices = batteries.mapNotNull { battery ->
                runCatching {
                    BatteryDevice(
                        model_name = battery.model_name,
                        powerbank_capacity_mah = battery.powerbank_capacity_mah ?: 0,
                        manufacture_date = battery.manufacture_date.orEmpty(),
                        id = battery.id
                    )
                }.getOrNull()
            }

            app.preferenceManager.mergeDevicesFromServer(syncedDevices)
            app.preferenceManager.getAllDevices()
        }
    }
}
