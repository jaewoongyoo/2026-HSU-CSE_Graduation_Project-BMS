package com.han.battery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.han.battery.ui.dashboard.DashboardScreen
import com.han.battery.ui.landing.LandingScreen
import com.han.battery.ui.theme.BatteryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BatteryTheme {
                var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }

                if (deviceInfo == null) {
                    LandingScreen(
                        onStart = { info ->
                            deviceInfo = info
                        }
                    )
                } else {
                    DashboardScreen(
                        device = deviceInfo!!,
                        onBack = {
                            deviceInfo = null
                        }
                    )
                }
            }
        }
    }
}