package com.han.battery

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
import androidx.compose.foundation.layout.padding // 추가됨
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons // 추가됨
import androidx.compose.material.icons.filled.Home // 추가됨
import androidx.compose.material.icons.filled.List // 추가됨
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon // 추가됨
import androidx.compose.material3.NavigationBar // 추가됨
import androidx.compose.material3.NavigationBarItem // 추가됨
import androidx.compose.material3.Scaffold // 추가됨
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme // 추가됨
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier // 추가됨
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

                // 현재 화면의 라우트(경로)를 추적하는 상태 변수
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                var deviceRefreshKey by remember { mutableStateOf(0) }
                var showExitDialog by remember { mutableStateOf(false) }

                // 하단 바를 보여줄 화면 조건 설정 (스플래시, 로그인, 회원가입 등에서는 숨김)
                val showBottomBar = currentRoute in listOf("home", "board") || currentRoute?.startsWith("dashboard") == true

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

                // 👉 Scaffold로 전체 앱을 감싸 하단 바 영역을 확보합니다.
                Scaffold(
                    topBar = {
                        if (showBottomBar) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp) // 👉 상단 바 높이 축소 (기본 64dp -> 48dp)
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
                                // 👉 수정 포인트 1: 시스템 영역만큼 패딩을 먼저 주고, 그 위에 높이를 설정합니다.
                                modifier = Modifier
                                    .navigationBarsPadding() // 스마트폰 하단 제스처/버튼 영역 확보
                                    .height(60.dp),          // 확보된 공간 위로 60dp 높이 설정

                                // 👉 수정 포인트 2: NavigationBar 자체의 기본 중복 패딩을 제거합니다.
                                windowInsets = WindowInsets(0, 0, 0, 0),

                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                // 1. 홈 탭
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

                                // 2. 게시판 탭
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
                    // 👉 NavHost에 modifier = Modifier.padding(innerPadding) 적용
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
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
                                    },
                                    onNavigateToBoard = {
                                        navController.navigate("board")
                                    }
                                )
                            }
                        }

                        // 5. 랜딩 화면
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

                        // 6. 대시보드 화면
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

                        // 7. 실사용 성능 공유 게시판 화면
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
    }
}