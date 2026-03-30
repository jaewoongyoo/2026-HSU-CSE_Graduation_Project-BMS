package com.han.battery.ui.splash
// 앱 시작 시 표시되는 로고 애니메이션이 포함된 스플래시 스크린

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.ui.components.common.AppLogo
import com.han.battery.ui.components.common.LogoSize
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate50

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 로고 페이드인
        logoAlpha.animateTo(1f, animationSpec = tween(800))
        // 텍스트 페이드인
        textAlpha.animateTo(1f, animationSpec = tween(800))
        // 2초 대기
        kotlinx.coroutines.delay(2000)
        // 전체 페이드아웃
        logoAlpha.animateTo(0f, animationSpec = tween(600))
        // 화면 전환
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Slate50,
                        Color(0xFFF6F8FC),
                        Slate50
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(logoAlpha.value)
        ) {
            AppLogo(
                size = LogoSize.Large,
                showText = false
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    text = "BatteryAI",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Blue600
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "내 보조배터리의 진정한 상태를 알아보세요",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }
    }
}

