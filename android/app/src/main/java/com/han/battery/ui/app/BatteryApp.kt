package com.han.battery.ui.app

import android.app.Activity
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.han.battery.BatteryApplication
import com.han.battery.ui.auth.AuthUiEvent
import com.han.battery.ui.auth.AuthViewModel
import com.han.battery.ui.auth.LoginScreen
import com.han.battery.ui.auth.SignupScreen
import com.han.battery.ui.board.BoardScreen
import com.han.battery.ui.board.BoardViewModel
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
    val batteryApplication = context.applicationContext as BatteryApplication
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val appViewModel: AppViewModel = viewModel(
        factory = remember(batteryApplication) {
            simpleViewModelFactory {
                AppViewModel(
                    batteryApplication.userManager,
                    batteryApplication.authRepository,
                    batteryApplication.preferenceManager
                )
            }
        }
    )
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(batteryApplication) {
            simpleViewModelFactory {
                AuthViewModel(batteryApplication.authRepository)
            }
        }
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = remember(batteryApplication) {
            simpleViewModelFactory {
                HomeViewModel(
                    batteryApplication.authRepository,
                    batteryApplication.preferenceManager
                )
            }
        }
    )
    val landingViewModel: LandingViewModel = viewModel(
        factory = remember(batteryApplication) {
            simpleViewModelFactory {
                LandingViewModel(
                    batteryApplication.authRepository,
                    batteryApplication.preferenceManager
                )
            }
        }
    )

    val devices by homeViewModel.devices.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showExitDialog by remember { mutableStateOf(false) }

    fun navigateToHome(popUpRoute: String) {
        navController.navigate("home") {
            popUpTo(popUpRoute) { inclusive = true }
        }
    }

    fun navigateHomeSingleTop() {
        navController.navigate("home") {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
        }
    }

    fun navigateBackOrHome() {
        if (!navController.popBackStack()) {
            navigateHomeSingleTop()
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
        onNavigateHome = { navigateHomeSingleTop() }
    )

    LandingEventHandler(
        context = context,
        landingViewModel = landingViewModel,
        homeViewModel = homeViewModel,
        navController = navController
    )

    val showBottomBar = currentRoute in listOf("home", "board") ||
        currentRoute?.startsWith("dashboard") == true

    val isRootRoute = currentRoute == "home" || currentRoute == "login" || currentRoute == "splash"

    BackHandler {
        if (isRootRoute) {
            showExitDialog = true
        } else {
            if (!navController.popBackStack()) {
                showExitDialog = true
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("앱 종료", fontWeight = FontWeight.Bold) },
            text = { Text("애플리케이션을 종료하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        activity?.finish()
                    }
                ) {
                    Text("종료", color = androidx.compose.ui.graphics.Color.Red, fontWeight = FontWeight.Bold)
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
            batteryApplication = batteryApplication,
            appViewModel = appViewModel,
            authViewModel = authViewModel,
            homeViewModel = homeViewModel,
            landingViewModel = landingViewModel,
            devices = devices,
            onSyncAndNavigateHome = { popUpRoute -> syncDevicesAndNavigateHome(popUpRoute) },
            onNavigateHome = { navigateHomeSingleTop() },
            onNavigateBackOrHome = { navigateBackOrHome() }
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
    batteryApplication: BatteryApplication,
    appViewModel: AppViewModel,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    landingViewModel: LandingViewModel,
    devices: List<com.han.battery.data.model.BatteryDevice>,
    onSyncAndNavigateHome: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateBackOrHome: () -> Unit
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
                    onNavigateBackOrHome()
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
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = remember(batteryApplication) {
                    simpleViewModelFactory {
                        DashboardViewModel(
                            batteryApplication.applicationContext,
                            preferenceManager = batteryApplication.preferenceManager,
                            authRepository = batteryApplication.authRepository,
                            communityRepository = batteryApplication.communityRepository
                        )
                    }
                }
            )

            if (device != null) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    device = device,
                    onBack = onNavigateBackOrHome,
                    onChangeDevice = onNavigateHome,
                    onDeleteDevice = {
                        homeViewModel.deleteDevice(device, navigateHomeAfterDelete = true)
                    }
                )
            } else {
                onNavigateBackOrHome()
            }
        }

        composable("board") {
            val boardViewModel: BoardViewModel = viewModel(
                factory = remember(batteryApplication) {
                    simpleViewModelFactory {
                        BoardViewModel(
                            communityRepository = batteryApplication.communityRepository,
                            userManager = batteryApplication.userManager
                        )
                    }
                }
            )
            val boardUiState by boardViewModel.uiState.collectAsState()

            BoardScreen(
                uiState = boardUiState,
                onCategorySelected = boardViewModel::selectCategory,
                onFilterSelected = boardViewModel::selectFilterValue,
                onSearchQueryChanged = boardViewModel::setSearchQuery,
                onPostSelected = boardViewModel::loadSohHistory,
                onDeletePost = boardViewModel::deletePost,
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onRetry = boardViewModel::refresh
            )
        }
    }
}

private inline fun <reified VM : ViewModel> simpleViewModelFactory(
    crossinline creator: () -> VM
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VM::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return creator() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
