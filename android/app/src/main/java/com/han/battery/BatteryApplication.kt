package com.han.battery

import android.app.Application
import androidx.room.Room
import com.han.battery.data.api.ApiService
import com.han.battery.data.repository.AWSIoTManager
import com.han.battery.data.repository.AuthRepository
import com.han.battery.data.repository.BatteryRepository
import com.han.battery.data.storage.BatteryDatabase
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager

class BatteryApplication : Application() {
    val userManager: UserManager by lazy {
        UserManager(applicationContext)
    }

    val apiService: ApiService by lazy {
        ApiService(baseUrl = DevConfig.API_BASE_URL)
    }

    val preferenceManager: PreferenceManager by lazy {
        PreferenceManager(applicationContext, userManager)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(apiService, userManager, applicationContext)
    }

    val awsIoTManager: AWSIoTManager by lazy {
        AWSIoTManager(applicationContext)
    }

    val batteryDatabase: BatteryDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            BatteryDatabase::class.java,
            "battery_db"
        ).build()
    }

    val batteryRepository: BatteryRepository by lazy {
        BatteryRepository(batteryDatabase.batteryDao(), awsIoTManager)
    }

    override fun onCreate() {
        super.onCreate()
        java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }
}
