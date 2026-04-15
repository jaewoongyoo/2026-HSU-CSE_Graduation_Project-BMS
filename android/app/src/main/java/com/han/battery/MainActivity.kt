package com.han.battery

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.han.battery.data.api.ApiService
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.repository.AuthRepository
import com.han.battery.data.repository.AdvancedBatteryRepository
import com.han.battery.data.repository.AdvancedUserRepository
import com.han.battery.data.storage.PreferenceManager
import com.han.battery.data.storage.UserManager
import com.han.battery.ui.auth.LoginScreen
import com.han.battery.ui.auth.SignupScreen
import com.han.battery.ui.dashboard.DashboardScreen
import com.han.battery.ui.dashboard.DashboardViewModel
import com.han.battery.ui.home.HomeScreen
import com.han.battery.ui.landing.LandingScreen
import com.han.battery.ui.splash.SplashScreen
import com.han.battery.ui.theme.BatteryTheme
import com.han.battery.service.ChargingReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var userManager: UserManager
    private lateinit var apiService: ApiService
    private lateinit var authRepository: AuthRepository
    private lateinit var advancedBatteryRepository: AdvancedBatteryRepository
    private lateinit var advancedUserRepository: AdvancedUserRepository
    private lateinit var chargingReceiver: ChargingReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)
        apiService = ApiService(baseUrl = DevConfig.API_BASE_URL)
        authRepository = AuthRepository(apiService, userManager, this)
        // ✅ AdvancedRepository 초기화
        advancedBatteryRepository = AdvancedBatteryRepository(apiService, userManager, this, authRepository)
        advancedUserRepository = AdvancedUserRepository(apiService, userManager, this, authRepository)
        preferenceManager = PreferenceManager(this, userManager)

        // 충전 감지 리시버 동적 등록
        chargingReceiver = ChargingReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(chargingReceiver, filter)

        setContent {
            BatteryTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                var deviceRefreshKey by remember { mutableIntStateOf(0) }
                var showExitDialog by remember { mutableStateOf(false) }
                var devicesList by remember { mutableStateOf(preferenceManager.getAllDevices()) }

                val showBottomBar = currentRoute in listOf(
                    "home",
                    "board"
                ) || currentRoute?.startsWith("dashboard") == true

                BackHandler {
                    if (!navController.popBackStack()) {
                        showExitDialog = true
                    }
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("Exit") },
                        text = { Text("Close the app?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitDialog = false
                                    finish()
                                }
                            ) {
                                Text("Exit", color = androidx.compose.ui.graphics.Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        if (showBottomBar) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "보조배터리 관리",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .height(60.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == "home" || currentRoute?.startsWith("dashboard") == true,
                                    onClick = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = "홈",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = { Text("홈", fontSize = 10.sp) },
                                    alwaysShowLabel = true
                                )

                                NavigationBarItem(
                                    selected = currentRoute == "board",
                                    onClick = {
                                        navController.navigate("board") {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.List,
                                            contentDescription = "게시판",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = { Text("게시판", fontSize = 10.sp) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(
                                userManager = userManager,
                                onSplashFinished = {
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

                        composable("login") {
                            LoginScreen(
                                authRepository = authRepository,
                                onNavigateToSignup = {
                                    navController.navigate("signup")
                                },
                                onLoginSuccess = {
                                    preferenceManager.clearAllDevices()
                                    deviceRefreshKey++
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("signup") {
                            SignupScreen(
                                authRepository = authRepository,
                                onNavigateToLogin = {
                                    navController.popBackStack()
                                },
                                onSignupSuccess = {
                                    preferenceManager.clearAllDevices()
                                    deviceRefreshKey++
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            key(deviceRefreshKey) {
                                // deviceRefreshKey가 변경될 때마다 devices 리스트 새로고침
                                devicesList = preferenceManager.getAllDevices()

                                HomeScreen(
                                    devices = devicesList,
                                    onDeviceSelected = { device ->
                                        navController.navigate("dashboard/${device.model_name}")
                                    },
                                    onAddNewDevice = {
                                        navController.navigate("landing")
                                    },
                                    onDeleteDevice = { device ->
                                        coroutineScope.launch {
                                            // 서버에서 배터리 삭제
                                            if (device.id > 0) {
                                                android.util.Log.d("MainActivity", "🗑️ 배터리 삭제 시작: ${device.model_name} (ID: ${device.id})")
                                                val result = authRepository.deleteBattery(device.id)
                                                if (result.isSuccess) {
                                                    android.util.Log.d("MainActivity", "✅ 서버 삭제 성공, 로컬에서도 삭제: ${device.model_name}")
                                                    // 로컬에서도 삭제
                                                    preferenceManager.deleteDevice(device.model_name)
                                                    // 리스트 즉시 업데이트
                                                    devicesList = preferenceManager.getAllDevices()
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Device deleted successfully.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    val message = result.exceptionOrNull()?.message
                                                        ?: "Failed to delete device from server."
                                                    android.util.Log.e("MainActivity", "❌ 서버 삭제 실패: $message")
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        message,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                // ID가 없으면 로컬에서만 삭제
                                                android.util.Log.d("MainActivity", "⚠️ 배터리 ID가 없음, 로컬에서만 삭제: ${device.model_name}")
                                                preferenceManager.deleteDevice(device.model_name)
                                                // 리스트 즉시 업데이트
                                                devicesList = preferenceManager.getAllDevices()
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Device deleted locally.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    userManager = userManager,
                                    onLogout = {
                                        authRepository.logout()
                                        preferenceManager.clearAllDevices()
                                        devicesList = emptyList()
                                        deviceRefreshKey++
                                        navController.navigate("login") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    },
                                    onNavigateToBoard = {
                                        navController.navigate("board")
                                    }
                                )
                            }
                        }

                        composable("landing") {
                            LandingScreen(
                                onStartClick = { deviceInfo ->
                                    if (deviceInfo.model_name.isBlank()) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Model name is required.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@LandingScreen
                                    }
                                    if (deviceInfo.powerbank_capacity_mah <= 0) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Capacity must be greater than 0.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@LandingScreen
                                    }

                                    coroutineScope.launch {
                                        val result = authRepository.registerBattery(
                                            modelName = deviceInfo.model_name,
                                            powerbankCapacityMah = deviceInfo.powerbank_capacity_mah,
                                            manufactureDate = deviceInfo.manufacture_date.ifBlank { null }
                                        )

                                        if (result.isSuccess) {
                                            // ✅ 서버에서 받은 ID를 포함하여 저장
                                            val batteryResponse = result.getOrNull()
                                            android.util.Log.d("MainActivity", "📱 배터리 등록 성공: ${batteryResponse?.model_name}, ID: ${batteryResponse?.id}")

                                            if (batteryResponse?.id != null && batteryResponse.id > 0) {
                                                android.util.Log.d("MainActivity", "✅ 서버 ID 수신 및 저장: ID=${batteryResponse.id}")
                                            } else {
                                                android.util.Log.w("MainActivity", "⚠️ 서버에서 유효한 ID 미수신: ID=${batteryResponse?.id}")
                                            }

                                            val deviceWithId = deviceInfo.copy(id = batteryResponse?.id ?: 0)
                                            preferenceManager.saveBatteryDevice(deviceWithId)
                                            deviceRefreshKey++

                                            val imm =
                                                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                            currentFocus?.windowToken?.let { token ->
                                                imm.hideSoftInputFromWindow(token, 0)
                                            }

                                            Toast.makeText(
                                                this@MainActivity,
                                                "Device registered.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            navController.navigate("dashboard/${deviceInfo.model_name}") {
                                                popUpTo("landing") { inclusive = true }
                                            }
                                        } else {
                                            val message = result.exceptionOrNull()?.message
                                                ?: "Device registration failed."
                                            android.util.Log.e("MainActivity", "❌ 배터리 등록 실패: $message")
                                            Toast.makeText(
                                                this@MainActivity,
                                                message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = "dashboard/{deviceNickname}",
                            arguments = listOf(navArgument("deviceNickname") {
                                type = NavType.StringType
                            })
                        ) { backStackEntry ->
                            val nickname =
                                backStackEntry.arguments?.getString("deviceNickname") ?: ""
                            val device = preferenceManager.getBatteryDevice(nickname)
                            val dashboardViewModel: DashboardViewModel = viewModel()

                            if (device != null) {
                                DashboardScreen(
                                    viewModel = dashboardViewModel,
                                    device = device,
                                    onBack = { navController.popBackStack() },
                                    onChangeDevice = { navController.navigate("home") },
                                    onDeleteDevice = {
                                        coroutineScope.launch {
                                            // ...existing code...
                                            if (device.id > 0) {
                                                android.util.Log.d("MainActivity", "🗑️ 배터리 삭제 시작: ${device.model_name} (ID: ${device.id})")
                                                val result = authRepository.deleteBattery(device.id)
                                                if (result.isSuccess) {
                                                    android.util.Log.d("MainActivity", "✅ 서버 삭제 성공, 로컬에서도 삭제: ${device.model_name}")
                                                    // 로컬에서도 삭제
                                                    preferenceManager.deleteDevice(nickname)
                                                    deviceRefreshKey++
                                                    navController.navigate("home") {
                                                        popUpTo("home") { inclusive = true }
                                                    }
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Device deleted successfully.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    val message = result.exceptionOrNull()?.message
                                                        ?: "Failed to delete device from server."
                                                    android.util.Log.e("MainActivity", "❌ 서버 삭제 실패: $message")
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        message,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                // ID가 없으면 로컬에서만 삭제
                                                android.util.Log.d("MainActivity", "⚠️ 배터리 ID가 없음, 로컬에서만 삭제: ${device.model_name}")
                                                preferenceManager.deleteDevice(nickname)
                                                deviceRefreshKey++
                                                navController.navigate("home") {
                                                    popUpTo("home") { inclusive = true }
                                                }
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Device deleted locally.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )
                            } else {
                                // Device를 찾을 수 없는 경우
                                navController.popBackStack()
                            }
                        }


                        composable("board") {
                            val dummyPosts = listOf(
                                com.han.battery.ui.board.BatteryPerformancePost(
                                    "1",
                                    "배터리마스터",
                                    "Galaxy S24 Ultra",
                                    "Anker 10000mAh",
                                    120,
                                    85,
                                    92
                                ),
                                com.han.battery.ui.board.BatteryPerformancePost(
                                    "2",
                                    "보배콜렉터",
                                    "iPhone 15 Pro",
                                    "Belkin 20000mAh",
                                    340,
                                    78,
                                    88
                                ),
                                com.han.battery.ui.board.BatteryPerformancePost(
                                    "3",
                                    "충전중독자",
                                    "Pixel 8",
                                    "삼성 10000mAh 배터리팩",
                                    50,
                                    90,
                                    99
                                ),
                                com.han.battery.ui.board.BatteryPerformancePost(
                                    "4",
                                    "충전중독자",
                                    "Pixel 8",
                                    "삼성 10000mAh 배터리팩",
                                    50,
                                    90,
                                    99
                                )
                            )

                            com.han.battery.ui.board.BoardScreen(
                                posts = dummyPosts
                            )
                        }
                    }
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(chargingReceiver)
    }
}

