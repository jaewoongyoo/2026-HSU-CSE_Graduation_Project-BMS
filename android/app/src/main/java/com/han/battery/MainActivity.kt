package com.han.battery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.han.battery.ui.app.BatteryApp
import com.han.battery.ui.theme.BatteryTheme
import dagger.hilt.android.AndroidEntryPoint

// ✅ 수정 1: 괄호 제거 (@AndroidEntryPoint만 남기기)
@AndroidEntryPoint
// ✅ 수정 2: Hilt_MainActivity 대신 순정 ComponentActivity 상속받기
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BatteryTheme {
                BatteryApp()
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}