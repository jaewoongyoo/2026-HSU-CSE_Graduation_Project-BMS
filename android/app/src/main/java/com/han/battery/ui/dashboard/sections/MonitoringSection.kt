package com.han.battery.ui.dashboard.sections
// 대시보드의 모니터링 섹션 - SOC, 전압, 전류, 온도 등 실시간 배터리 상태 표시

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// ⭐ SectionHeader 대신 LiveStatusBadge를 import 합니다.
import com.han.battery.ui.components.common.LiveStatusBadge
import com.han.battery.ui.components.monitoring.SmallMetricCard
import com.han.battery.ui.components.monitoring.SocMonitorCard
import com.han.battery.ui.components.monitoring.SohMonitorCard
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Emerald500

@Composable
fun MonitoringSection(
    isMonitoring: Boolean, // ⭐ [수정됨] title 파라미터 대신 상태값을 직접 받습니다.
    soc: Int,
    soh: Int,
    power: Double,
    voltage: Double,
    current: Int,
    predictionText: String = ""
) {
    Column {
        // ⭐ [수정됨] 기존 SectionHeader를 지우고, 상단바 디자인이었던 배지를 포함한 새로운 헤더를 구성합니다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween // 텍스트는 왼쪽, 배지는 오른쪽에 배치
        ) {
            Text(
                text = "배터리 상태",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 상단바에 있던 배지가 이곳에서 상태값(isMonitoring)에 따라 바뀌게 됩니다.
            LiveStatusBadge(isLive = isMonitoring)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SocMonitorCard(
                modifier = Modifier.weight(1f),
                soc = soc,
                predictionText = predictionText
            )

            SohMonitorCard(
                modifier = Modifier.weight(1f),
                soh = soh
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                title = "충전 속도",
                value = power.toString(),
                unit = "W",
                accent = Blue600
            )
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                title = "전압",
                value = voltage.toString(),
                unit = "V",
                accent = Color(0xFFA78BFA)
            )
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                title = "전류",
                value = current.toString(),
                unit = "mA",
                accent = Emerald500
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}