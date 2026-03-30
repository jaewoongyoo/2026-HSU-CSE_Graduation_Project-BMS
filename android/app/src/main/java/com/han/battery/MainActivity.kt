package com.han.battery
// 앱의 진입점 Activity - 스플래시 화면, 랜딩 화면, 대시보드 화면 전환 관리

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.han.battery.data.model.BatteryDevice
import com.han.battery.ui.dashboard.DashboardScreen
import com.han.battery.ui.landing.LandingScreen
import com.han.battery.ui.splash.SplashScreen
import com.han.battery.ui.theme.BatteryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BatteryTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
                
                when (currentScreen) {
                    is Screen.Splash -> {
                        SplashScreen(
                            onSplashFinished = {
                                currentScreen = Screen.Landing
                            }
                        )
                    }
                    is Screen.Landing -> {
                        LandingScreen(
                            onStart = { deviceInfo ->
                                // DeviceInfo는 BatteryDevice의 별칭
                                currentScreen = Screen.Dashboard(deviceInfo)
                                // IME 숨기기 (화면 전환 후)
                                window?.let { 
                                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                    imm.hideSoftInputFromWindow(it.decorView.windowToken, 0)
                                }
                            }
                        )
                    }
                    is Screen.Dashboard -> {
                        val batteryDevice = (currentScreen as Screen.Dashboard).batteryDevice
                        
                        DashboardScreen(
                            device = batteryDevice,
                            onBack = {
                                currentScreen = Screen.Landing
                            }
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 앱 내의 가능한 화면들을 정의합니다.
     */
    sealed class Screen {
        object Splash : Screen()
        object Landing : Screen()
        data class Dashboard(val batteryDevice: BatteryDevice) : Screen()
    }
}

