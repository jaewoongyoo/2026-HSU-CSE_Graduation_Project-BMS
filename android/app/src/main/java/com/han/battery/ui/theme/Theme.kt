package com.han.battery.ui.theme
// Material Design 3 테마 설정 - 색상 스킴, Light/Dark 모드, 시스템 바 스타일 관리

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary          = Blue600,
    onPrimary        = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue100,
    background       = Slate50,
    surface          = androidx.compose.ui.graphics.Color.White,
    surfaceVariant   = Slate100,
    outline          = Slate300,
    onSurface        = Slate950,
    onBackground     = Slate950,
)

private val DarkColors = darkColorScheme(
    primary          = Blue400,
    onPrimary        = Blue900,
    primaryContainer = Blue900,
    background       = Slate950,
    surface          = Slate800,
    surfaceVariant   = Slate700,
    outline          = Slate500,
    onSurface        = Slate100,
    onBackground     = Slate100,
)

@Composable
fun BatteryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}