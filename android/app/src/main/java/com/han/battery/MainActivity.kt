package com.han.battery
// 앱의 진입점 Activity - 스플래시 화면, 랜딩 화면, 대시보드 화면 전환 관리

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.han.battery.data.common.AppLogger
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.ui.components.animations.slideInFromLeftTransition
import com.han.battery.ui.components.animations.slideInFromRightTransition
import com.han.battery.ui.dashboard.DashboardScreen
import com.han.battery.ui.home.HomeScreen
import com.han.battery.ui.landing.LandingScreen
import com.han.battery.ui.splash.SplashScreen
import com.han.battery.ui.theme.BatteryTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var preferenceManager: PreferenceManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        preferenceManager = PreferenceManager(this)

        setContent {
            BatteryTheme {
                // 항상 스플래시부터 시작 (저장된 기기 여부는 스플래시 후 확인)
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
                var showExitDialog by remember { mutableStateOf(false) }
                // 기기 목록을 Compose 상태로 관리하여 삭제 후 즉시 업데이트되도록 함
                var deviceRefreshKey by remember { mutableStateOf(0) }
                
                // 백 버튼 처리
                BackHandler(enabled = currentScreen !is Screen.Splash) {
                    when (currentScreen) {
                        is Screen.Home -> showExitDialog = true
                        is Screen.Landing -> currentScreen = Screen.Home
                        is Screen.Dashboard -> currentScreen = Screen.Home
                        else -> {}
                    }
                }
                
                // 종료 확인 다이얼로그
                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("앱 종료") },
                        text = { Text("BatteryInsight를 종료하시겠습니까?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    finish()
                                }
                            ) {
                                Text("종료")
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = { showExitDialog = false }
                            ) {
                                Text("취소")
                            }
                        }
                    )
                }
                
                // 페이지 전환 애니메이션과 함께 화면 표시
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        // 이전 상태와 현재 상태에 따라 애니메이션 결정
                        when {
                            // Splash -> Home/Landing (페이드인)
                            initialState is Screen.Splash -> {
                                ContentTransform(
                                    androidx.compose.animation.fadeIn(animationSpec = tween(300)),
                                    androidx.compose.animation.fadeOut(animationSpec = tween(300))
                                )
                            }
                            // Home -> Landing (오른쪽에서 왼쪽으로)
                            initialState is Screen.Home && targetState is Screen.Landing -> {
                                slideInFromRightTransition()
                            }
                            // Landing -> Home (왼쪽에서 오른쪽으로, 뒤로가기)
                            initialState is Screen.Landing && targetState is Screen.Home -> {
                                slideInFromLeftTransition()
                            }
                            // Home -> Dashboard (오른쪽에서 왼쪽으로)
                            initialState is Screen.Home && targetState is Screen.Dashboard -> {
                                slideInFromRightTransition()
                            }
                            // Dashboard -> Home (왼쪽에서 오른쪽으로, 뒤로가기)
                            initialState is Screen.Dashboard && targetState is Screen.Home -> {
                                slideInFromLeftTransition()
                            }
                            else -> {
                                slideInFromRightTransition()
                            }
                        }
                    },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        is Screen.Splash -> {
                            SplashScreen(
                                onSplashFinished = {
                                    val nextScreen = if (preferenceManager.isDeviceRegistered()) {
                                        Screen.Home
                                    } else {
                                        Screen.Landing
                                    }
                                    currentScreen = nextScreen
                                }
                            )
                        }
                        is Screen.Home -> {
                            HomeScreen(
                                key = deviceRefreshKey,
                                devices = preferenceManager.getAllDevices(),
                                onDeviceSelected = { device ->
                                    currentScreen = Screen.Dashboard(device)
                                },
                                onAddNewDevice = {
                                    currentScreen = Screen.Landing
                                },
                                onDeleteDevice = { device ->
                                    preferenceManager.deleteDevice(device.nickname)
                                    deviceRefreshKey++
                                }
                            )
                        }
                        is Screen.Landing -> {
                            LandingScreen(
                                onStart = { deviceInfo ->
                                    preferenceManager.saveBatteryDevice(deviceInfo)
                                    currentScreen = Screen.Home
                                    deviceRefreshKey++
                                    // IME 숨기기
                                    window?.let { 
                                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.hideSoftInputFromWindow(it.decorView.windowToken, 0)
                                    }
                                }
                            )
                        }
                        is Screen.Dashboard -> {
                            val batteryDevice = screen.batteryDevice
                            
                            DashboardScreen(
                                device = batteryDevice,
                                onBack = {
                                    currentScreen = Screen.Home
                                },
                                onChangeDevice = {
                                    currentScreen = Screen.Home
                                },
                                onDeleteDevice = {
                                    preferenceManager.deleteDevice(batteryDevice.nickname)
                                    currentScreen = Screen.Home
                                    deviceRefreshKey++
                                }
                            )
                        }
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
        object Home : Screen()
        object Landing : Screen()
        data class Dashboard(val batteryDevice: BatteryDevice) : Screen()
    }
}

