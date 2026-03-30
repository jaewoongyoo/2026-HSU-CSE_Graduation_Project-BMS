package com.han.battery.ui.dashboard.sections
// 대시보드의 모니터링 섹션 - SOC, 전압, 전류, 온도 등 실시간 배터리 상태 표시

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.han.battery.ui.components.common.SectionHeader
import com.han.battery.ui.components.monitoring.SmallMetricCard
import com.han.battery.ui.components.monitoring.SocMonitorCard
import com.han.battery.ui.components.monitoring.SohMonitorCard
import com.han.battery.ui.theme.Amber500
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Emerald500

@Composable
fun MonitoringSection(
    soc: Int,
    soh: Int,
    power: Double,
    voltage: Double,
    current: Int
) {
    Column {
        SectionHeader(title = "실시간 모니터링")

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SocMonitorCard(
                modifier = Modifier.weight(1f),
                soc = soc
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

