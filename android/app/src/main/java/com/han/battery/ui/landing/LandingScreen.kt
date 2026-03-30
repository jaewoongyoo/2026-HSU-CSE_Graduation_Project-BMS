package com.han.battery.ui.landing
// 사용자의 배터리 정보 입력을 위한 초기 랜딩 스크린 (기기 제조사, 모델명, 제조년월일 등록)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.DeviceInfo
import com.han.battery.ui.landing.sections.FeatureSection
import com.han.battery.ui.landing.sections.FormState
import com.han.battery.ui.landing.sections.HeroSection
import com.han.battery.ui.landing.sections.InputSection
import com.han.battery.ui.landing.sections.LandingHeadline
import com.han.battery.ui.components.common.AppLogo
import com.han.battery.ui.components.common.LogoSize
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate50

@Composable
fun LandingScreen(
    onStart: (DeviceInfo) -> Unit
) {
    var formState by remember { mutableStateOf(FormState()) }

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
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        BackgroundGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            AppLogo(size = LogoSize.Medium)

            Spacer(modifier = Modifier.height(28.dp))

            HeroSection()

            Spacer(modifier = Modifier.height(30.dp))

            LandingHeadline()

            Spacer(modifier = Modifier.height(26.dp))

            InputSection(
                formState = formState,
                onFormChange = { formState = it },
                onStart = onStart
            )

            Spacer(modifier = Modifier.height(22.dp))

            FeatureSection()

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BackgroundGlow() {
    Box(
        modifier = Modifier
            .fillMaxSize(0.5f),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Blue600.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

