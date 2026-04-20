package com.han.battery.ui.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.han.battery.ui.auth.AuthUiEvent
import com.han.battery.ui.auth.AuthViewModel
import com.han.battery.ui.auth.LoginScreen
import com.han.battery.ui.auth.SignupScreen
import com.han.battery.ui.board.BatteryPerformancePost
import com.han.battery.ui.board.BoardScreen
import com.han.battery.ui.dashboard.DashboardScreen
import com.han.battery.ui.dashboard.DashboardViewModel
import com.han.battery.ui.home.HomeScreen
import com.han.battery.ui.home.HomeUiEvent
import com.han.battery.ui.home.HomeViewModel
import com.han.battery.ui.landing.LandingScreen
import com.han.battery.ui.landing.LandingUiEvent
import com.han.battery.ui.landing.LandingViewModel
import com.han.battery.ui.splash.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun BatteryApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val appViewModel: AppViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val landingViewModel: LandingViewModel = hiltViewModel()

    val devices by homeViewModel.devices.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showExitDialog by remember { mutableStateOf(false) }

    val isCharging = rememberIsCharging()

    fun navigateToHome(popUpRoute: String) {
        navController.navigate("home") {
            popUpTo(popUpRoute) { inclusive = true }
        }
    }

    fun syncDevicesAndNavigateHome(popUpRoute: String) {
        coroutineScope.launch {
            val syncResult = appViewModel.syncDevicesFromServer()
            if (syncResult.isFailure) {
                android.util.Log.w(
                    "BatteryApp",
                    "서버 배터리 동기화 실패: ${syncResult.exceptionOrNull()?.message}"
                )
            }
            homeViewModel.refreshDevices()
            navigateToHome(popUpRoute)
        }
    }

    AuthEventHandler(
        authViewModel = authViewModel,
        currentRoute = currentRoute,
        onSignedIn = {
            syncDevicesAndNavigateHome("login")
            authViewModel.resetState()
        }
    )

    HomeEventHandler(
        context = context,
        homeViewModel = homeViewModel,
        navController = navController,
        onNavigateHome = { navigateToHome("home") }
    )

    LandingEventHandler(
        context = context,
        landingViewModel = landingViewModel,
        homeViewModel = homeViewModel,
        navController = navController
    )

    val showBottomBar = currentRoute in listOf("home", "board") ||
        currentRoute?.startsWith("dashboard") == true

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
                        activity?.finish()
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
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,

        // ✅ 2. 둥근 시작 버튼 추가
        floatingActionButton = {
            if (showBottomBar) { // 하단바가 보일 때만 버튼도 같이 보이게
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        if (isCharging) {
                            // TODO: 여기에 세션 시작 API 및 서비스 실행 로직 연결
                            Toast.makeText(context, "모니터링을 시작합니다!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "충전 케이블을 먼저 연결해 주세요.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    // 충전 중이면 기본 테마색, 아니면 회색으로 변경
                    containerColor = if (isCharging) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                        contentDescription = "모니터링 시작",
                        modifier = Modifier.size(32.dp)
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
        BatteryNavGraph(
            modifier = Modifier.padding(innerPadding),
        navController = navController,
        appViewModel = appViewModel,
        authViewModel = authViewModel,
        homeViewModel = homeViewModel,
        landingViewModel = landingViewModel,
        devices = devices,
        onSyncAndNavigateHome = { popUpRoute -> syncDevicesAndNavigateHome(popUpRoute) }
    )
}
}

@Composable
private fun AuthEventHandler(
    authViewModel: AuthViewModel,
    currentRoute: String?,
    onSignedIn: () -> Unit
) {
    LaunchedEffect(authViewModel, currentRoute) {
        authViewModel.events.collect { event ->
            when (event) {
                AuthUiEvent.SignedIn -> onSignedIn()
            }
        }
    }
}

@Composable
private fun HomeEventHandler(
    context: Context,
    homeViewModel: HomeViewModel,
    navController: NavHostController,
    onNavigateHome: () -> Unit
) {
    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.ShowMessage -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
                HomeUiEvent.NavigateToHome -> onNavigateHome()
                HomeUiEvent.NavigateToLogin -> {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandingEventHandler(
    context: Context,
    landingViewModel: LandingViewModel,
    homeViewModel: HomeViewModel,
    navController: NavHostController
) {
    LaunchedEffect(landingViewModel) {
        landingViewModel.events.collect { event ->
            when (event) {
                is LandingUiEvent.ShowMessage -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
                is LandingUiEvent.NavigateToDashboard -> {
                    homeViewModel.refreshDevices()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    (context as? Activity)?.currentFocus?.windowToken?.let { token ->
                        imm.hideSoftInputFromWindow(token, 0)
                    }
                    navController.navigate("dashboard/${event.modelName}") {
                        popUpTo("landing") { inclusive = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    landingViewModel: LandingViewModel,
    devices: List<com.han.battery.data.model.BatteryDevice>,
    onSyncAndNavigateHome: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "splash",
        modifier = modifier
    ) {
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    if (appViewModel.isLoggedIn()) {
                        onSyncAndNavigateHome("splash")
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
                viewModel = authViewModel,
                onNavigateToSignup = {
                    authViewModel.resetState()
                    navController.navigate("signup")
                }
            )
        }

        composable("signup") {
            SignupScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    authViewModel.resetState()
                    navController.popBackStack()
                }
            )
        }

        composable("home") {
            HomeScreen(
                devices = devices,
                onDeviceSelected = { device ->
                    navController.navigate("dashboard/${device.model_name}")
                },
                onAddNewDevice = {
                    navController.navigate("landing")
                },
                onDeleteDevice = { device ->
                    homeViewModel.deleteDevice(device)
                },
                onLogout = {
                    homeViewModel.logout()
                },
                onNavigateToBoard = {
                    navController.navigate("board")
                }
            )
        }

        composable("landing") {
            LandingScreen(
                onStartClick = { deviceInfo ->
                    landingViewModel.registerDevice(deviceInfo)
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
            val nickname = backStackEntry.arguments?.getString("deviceNickname") ?: ""
            val device = homeViewModel.getDevice(nickname)
            val dashboardViewModel: DashboardViewModel = hiltViewModel()

            if (device != null) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    device = device,
                    onBack = { navController.popBackStack() },
                    onChangeDevice = { navController.navigate("home") },
                    onDeleteDevice = {
                        homeViewModel.deleteDevice(device, navigateHomeAfterDelete = true)
                    }
                )
            } else {
                navController.popBackStack()
            }
        }

        composable("board") {
            val dummyPosts = listOf(
                BatteryPerformancePost("1", "배터리마스터", "Galaxy S24 Ultra", "Anker 10000mAh", 120, 85, 92),
                BatteryPerformancePost("2", "보배콜렉터", "iPhone 15 Pro", "Belkin 20000mAh", 340, 78, 88),
                BatteryPerformancePost("3", "충전중독자", "Pixel 8", "삼성 10000mAh 배터리팩", 50, 90, 99),
                BatteryPerformancePost("4", "충전중독자", "Pixel 8", "삼성 10000mAh 배터리팩", 50, 90, 99)
            )

            BoardScreen(posts = dummyPosts)
        }
    }
}

@Composable
fun rememberIsCharging(): Boolean {
    val context = LocalContext.current
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val initialIntent = context.registerReceiver(null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val initialStatus = initialIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging = initialStatus == BatteryManager.BATTERY_STATUS_CHARGING || initialStatus == BatteryManager.BATTERY_STATUS_FULL

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return isCharging
}
