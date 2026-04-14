package com.han.battery

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.han.battery.data.api.ApiService
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.repository.AuthRepository
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var userManager: UserManager
    private lateinit var apiService: ApiService
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)
        apiService = ApiService(baseUrl = DevConfig.API_BASE_URL)
        authRepository = AuthRepository(apiService, userManager)
        preferenceManager = PreferenceManager(this, userManager)

        setContent {
            BatteryTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                var deviceRefreshKey by remember { mutableStateOf(0) }
                var showExitDialog by remember { mutableStateOf(false) }

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

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        SplashScreen(
                            userManager = userManager,
                            context = this@MainActivity,
                            onSplashFinished = {
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
                                    preferenceManager.clearAllDevices()
                                    deviceRefreshKey++
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    composable("landing") {
                        LandingScreen(
                            onStartClick = { deviceInfo ->
                                if (deviceInfo.nickname.isBlank()) {
                                    Toast.makeText(this@MainActivity, "Model name is required.", Toast.LENGTH_SHORT).show()
                                    return@LandingScreen
                                }
                                if (deviceInfo.capacity <= 0) {
                                    Toast.makeText(this@MainActivity, "Capacity must be greater than 0.", Toast.LENGTH_SHORT).show()
                                    return@LandingScreen
                                }

                                coroutineScope.launch {
                                    val result = authRepository.registerBattery(
                                        modelName = deviceInfo.nickname,
                                        capacityMah = deviceInfo.capacity,
                                        manufacturer = deviceInfo.brand.ifBlank { null },
                                        manufactureDate = deviceInfo.manufactureDate.ifBlank { null },
                                        powerbankCapacityMah = deviceInfo.capacity
                                    )

                                    if (result.isSuccess) {
                                        preferenceManager.saveBatteryDevice(deviceInfo)
                                        deviceRefreshKey++

                                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                        currentFocus?.windowToken?.let { token ->
                                            imm.hideSoftInputFromWindow(token, 0)
                                        }

                                        Toast.makeText(this@MainActivity, "Device registered.", Toast.LENGTH_SHORT).show()
                                        navController.navigate("dashboard/${deviceInfo.nickname}") {
                                            popUpTo("landing") { inclusive = true }
                                        }
                                    } else {
                                        val message = result.exceptionOrNull()?.message ?: "Device registration failed."
                                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
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
                        arguments = listOf(navArgument("deviceNickname") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val nickname = backStackEntry.arguments?.getString("deviceNickname") ?: ""
                        val device = preferenceManager.getBatteryDevice(nickname)
                        val dashboardViewModel: DashboardViewModel = viewModel()

                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            device = device ?: BatteryDevice(nickname = nickname, capacity = 100),
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
