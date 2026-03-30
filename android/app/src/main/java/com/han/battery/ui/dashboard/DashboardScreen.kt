package com.han.battery.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.han.battery.ui.components.*

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val status by viewModel.batteryStatus.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("실시간 모니터링", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(20.dp))

        // 게이지 컴포넌트 사용
        BatteryGauge(value = status.soc)

        Spacer(modifier = Modifier.height(20.dp))

        // AI 분석 카드 예시
        AIAnalysisCard(
            tag = "회귀 모델",
            title = "완충 예상 시간",
            value = "42분",
            desc = "현재 패턴 기반 분석 결과입니다."
        )
    }
}