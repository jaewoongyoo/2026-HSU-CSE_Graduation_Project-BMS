package com.han.battery

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.han.battery.service.ChargingReceiver

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var userManager: UserManager
    private lateinit var chargingReceiver: ChargingReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)
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

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                var deviceRefreshKey by remember { mutableStateOf(0) }
                var showExitDialog by remember { mutableStateOf(false) }

                val showBottomBar = currentRoute in listOf("home", "board") || currentRoute?.startsWith("dashboard") == true

                BackHandler {
                    if (!navController.popBackStack()) {
                        showExitDialog = true
                    }
                }

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
                                Text("종료", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text("취소")
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
                                    icon = { Icon(Icons.Default.Home, contentDescription = "홈", modifier = Modifier.size(20.dp)) },
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
                                    icon = { Icon(Icons.Default.List, contentDescription = "게시판", modifier = Modifier.size(20.dp)) },
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

                        composable("home") {
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
                                    preferenceManager.saveBatteryDevice(deviceInfo)
                                    deviceRefreshKey++

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

                        composable(
                            route = "dashboard/{deviceNickname}",
                            arguments = listOf(navArgument("deviceNickname") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val nickname = backStackEntry.arguments?.getString("deviceNickname") ?: ""
                            val device = preferenceManager.getBatteryDevice(nickname)

                            val dashboardViewModel: DashboardViewModel = viewModel()

                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                device = device ?: BatteryDevice(nickname = nickname),
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

                        composable("board") {
                            val dummyPosts = listOf(
                                com.han.battery.ui.board.BatteryPerformancePost("1", "배터리마스터", "Galaxy S24 Ultra", "Anker 10000mAh", 120, 85, 92),
                                com.han.battery.ui.board.BatteryPerformancePost("2", "보배콜렉터", "iPhone 15 Pro", "Baseus 20000mAh", 340, 78, 88),
                                com.han.battery.ui.board.BatteryPerformancePost("3", "충전중독자", "Pixel 8", "삼성 10000mAh 배터리팩", 50, 90, 99),
                                com.han.battery.ui.board.BatteryPerformancePost("4", "충전중독자", "Pixel 8", "삼성 10000mAh 배터리팩", 50, 90, 99)
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