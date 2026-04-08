package com.han.battery

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.ui.dashboard.DashboardScreen
import com.han.battery.ui.dashboard.DashboardViewModel
import com.han.battery.ui.home.HomeScreen
import com.han.battery.ui.landing.LandingScreen
import com.han.battery.ui.splash.SplashScreen
import com.han.battery.ui.theme.BatteryTheme
import com.han.battery.data.model.BatteryDevice
import com.han.battery.ui.auth.LoginScreen
import com.han.battery.ui.auth.SignupScreen
import com.han.battery.data.storage.UserManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)
        preferenceManager = PreferenceManager(this, userManager)

        setContent {
            BatteryTheme {
                val navController = rememberNavController()
                var deviceRefreshKey by remember { mutableStateOf(0) }
                var showExitDialog by remember { mutableStateOf(false) }

                // BackHandler: 시스템 뒤로가기 버튼 처리
                BackHandler {
                    // 백스택이 비어있으면 (홈 스크린) 앱 종료 확인
                    if (!navController.popBackStack()) {
                        showExitDialog = true
                    }
                }

                // 앱 종료 확인 다이얼로그
                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("앱 종료") },
                        text = { Text("정말 앱을 종료하시겠습니까?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitDialog = false
                                    finish()
                                }
                            ) {
                                Text("종료", color = androidx.compose.ui.graphics.Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text("취소")
                            }
                        }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    // 1. 스플래시 화면
                    composable("splash") {
                        SplashScreen(
                            userManager = userManager,
                            onSplashFinished = {
                                // 사용자가 이미 로그인되어 있는지 확인
                                if (userManager.isLoggedIn()) {
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("login") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    // 2. 로그인 화면
                    composable("login") {
                        LoginScreen(
                            userManager = userManager,
                            onNavigateToSignup = {
                                navController.navigate("signup")
                            },
                            onLoginSuccess = {
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    // 3. 회원가입 화면
                    composable("signup") {
                        SignupScreen(
                            userManager = userManager,
                            onNavigateToLogin = {
                                navController.popBackStack()
                            },
                            onSignupSuccess = {
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    // 4. 홈 화면
                    composable("home") {
                        // key를 통해 삭제/수정 시 UI 강제 리프레시
                        key(deviceRefreshKey) {
                            HomeScreen(
                                devices = preferenceManager.getAllDevices(),
                                onDeviceSelected = { device ->
                                    navController.navigate("dashboard/${device.nickname}")
                                },
                                onAddNewDevice = {
                                    navController.navigate("landing")
                                },
                                onDeleteDevice = { device ->
                                    preferenceManager.deleteDevice(device.nickname)
                                    deviceRefreshKey++
                                },
                                userManager = userManager,
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    // 5. 랜딩 화면
                    composable("landing") {
                        LandingScreen(
                            onStartClick = { deviceInfo ->
                                // deviceInfo는 BatteryDevice의 별칭이므로 바로 저장 가능합니다.
                                preferenceManager.saveBatteryDevice(deviceInfo)
                                deviceRefreshKey++

                                // 키보드 숨기기 (Context 활용)
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                currentFocus?.windowToken?.let { token ->
                                    imm.hideSoftInputFromWindow(token, 0)
                                }

                                navController.navigate("dashboard/${deviceInfo.nickname}") {
                                    popUpTo("landing") { inclusive = true }
                                }
                            },
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // 6. 대시보드 화면
                    composable(
                        route = "dashboard/{deviceNickname}",
                        arguments = listOf(navArgument("deviceNickname") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val nickname = backStackEntry.arguments?.getString("deviceNickname") ?: ""
                        // 저장소에서 닉네임으로 기기 정보 조회
                        val device = preferenceManager.getBatteryDevice(nickname)

                        // Member B의 실시간 로직이 담긴 ViewModel
                        val dashboardViewModel: DashboardViewModel = viewModel()

                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            device = device ?: BatteryDevice(nickname = nickname), // 방어 코드
                            onBack = { navController.popBackStack() },
                            onChangeDevice = { navController.navigate("home") },
                            onDeleteDevice = {
                                preferenceManager.deleteDevice(nickname)
                                deviceRefreshKey++
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

}