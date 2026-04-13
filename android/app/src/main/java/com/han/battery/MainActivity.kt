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
import com.han.battery.data.api.ApiService
import com.han.battery.data.repository.AuthRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var userManager: UserManager
    private lateinit var apiService: ApiService
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)
        apiService = ApiService(baseUrl = com.han.battery.DevConfig.API_BASE_URL)
        authRepository = AuthRepository(apiService, userManager)
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
                            context = this@MainActivity,
                            onSplashFinished = {
                                // 세션 기반 인증: 사용자 정보로 로그인 상태 판단
                                if (userManager.getCurrentUser() != null) {
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
                            authRepository = authRepository,
                            onNavigateToSignup = {
                                navController.navigate("signup")
                            },
                            onLoginSuccess = {
                                // 로그인 성공 후 로컬 데이터 정리 (다른 사용자의 데이터 제거)
                                preferenceManager.clearAllDevices()
                                deviceRefreshKey++
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    // 3. 회원가입 화면
                    composable("signup") {
                        SignupScreen(
                            authRepository = authRepository,
                            onNavigateToLogin = {
                                navController.popBackStack()
                            },
                            onSignupSuccess = {
                                // 회원가입 성공 후 로컬 데이터 정리
                                preferenceManager.clearAllDevices()
                                deviceRefreshKey++
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
                                    authRepository.logout()
                                    preferenceManager.clearAllDevices()  // 로그아웃 시 로컬 데이터 정리
                                    deviceRefreshKey++
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                     // 5. 랜딩 화면
                    composable("landing") {
                        val coroutineScope = rememberCoroutineScope()
                        var isRegistering by remember { mutableStateOf(false) }
                        var registrationError by remember { mutableStateOf<String?>(null) }

                        if (registrationError != null) {
                            AlertDialog(
                                onDismissRequest = { registrationError = null },
                                title = { Text("배터리 등록 알림") },
                                text = {
                                    Text(
                                        registrationError ?: "알 수 없는 오류가 발생했습니다.",
                                        color = androidx.compose.ui.graphics.Color.Red
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = { registrationError = null }) {
                                        Text("확인")
                                    }
                                }
                            )
                        }

                        LandingScreen(
                            onStartClick = { deviceInfo ->
                                // 입력 데이터 유효성 검증
                                if (deviceInfo.nickname.isBlank()) {
                                    registrationError = "모델명을 입력해주세요"
                                    return@LandingScreen
                                }
                                if (deviceInfo.capacity <= 0) {
                                    registrationError = "용량은 0보다 커야 합니다"
                                    return@LandingScreen
                                }

                                isRegistering = true

                                // 로컬에 저장
                                try {
                                    preferenceManager.saveBatteryDevice(deviceInfo)
                                    deviceRefreshKey++
                                    android.util.Log.d("BatteryRegistration", "배터리 로컬 저장 성공: ${deviceInfo.nickname}")
                                } catch (e: Exception) {
                                    android.util.Log.e("BatteryRegistration", "배터리 로컬 저장 실패", e)
                                    registrationError = "로컬 저장에 실패했습니다: ${e.message}"
                                    isRegistering = false
                                    return@LandingScreen
                                }

                                // 서버에도 저장 (비동기)
                                coroutineScope.launch {
                                    try {
                                        android.util.Log.d("BatteryRegistration",
                                            "서버 등록 시작 - model: ${deviceInfo.nickname}, capacity: ${deviceInfo.capacity}")

                                        val result = authRepository.registerBattery(
                                            modelName = deviceInfo.nickname,
                                            capacityMah = deviceInfo.capacity,
                                            manufacturer = deviceInfo.brand.ifBlank { null },
                                            manufactureDate = deviceInfo.manufactureDate.ifBlank { null },
                                            powerbankCapacityMah = deviceInfo.capacity
                                        )

                                        if (result.isSuccess) {
                                            android.util.Log.d("BatteryRegistration",
                                                "배터리 서버 저장 성공: ${result.getOrNull()?.id}")
                                        } else {
                                            val errorMsg = result.exceptionOrNull()?.message ?: "서버 등록 실패"
                                            android.util.Log.e("BatteryRegistration",
                                                "배터리 서버 저장 실패: $errorMsg")
                                            // 서버 저장 실패해도 계속 진행 (로컬 데이터는 있음)
                                            registrationError = "서버 저장에 실패했습니다. 앱을 재시작하면 다시 시도됩니다.\n오류: $errorMsg"
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("BatteryRegistration",
                                            "배터리 서버 저장 중 예외 발생", e)
                                        registrationError = "서버 저장 중 오류가 발생했습니다: ${e.message}"
                                    } finally {
                                        isRegistering = false
                                    }
                                }

                                // 키보드 숨기기
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                currentFocus?.windowToken?.let { token ->
                                    imm.hideSoftInputFromWindow(token, 0)
                                }

                                // 대시보드로 이동
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
                            device = device ?: BatteryDevice(nickname = nickname, capacity = 0), // 임시 기기 정보 (실제 용량 미지정)
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