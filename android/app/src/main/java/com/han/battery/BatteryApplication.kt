package com.han.battery

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp(Application::class)
class BatteryApplication : Hilt_BatteryApplication() {
    override fun onCreate() {
        super.onCreate()
        java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }
}
