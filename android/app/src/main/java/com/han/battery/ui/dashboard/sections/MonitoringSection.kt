package com.han.battery.ui.dashboard.sections
// 대시보드의 모니터링 섹션 - SOC, 전압, 전류, 온도 등 실시간 배터리 상태 표시

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ⭐ SectionHeader 대신 LiveStatusBadge를 import 합니다.
import com.han.battery.ui.components.common.LiveStatusBadge
import com.han.battery.ui.components.monitoring.SmallMetricCard
import com.han.battery.ui.components.monitoring.SocMonitorCard
import com.han.battery.ui.components.monitoring.SohMonitorCard
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Emerald500

@Composable
fun MonitoringSection(
    isMonitoring: Boolean,
    soc: Int,
    soh: Int,
    power: String,
    powerUnit: String,
    voltage: Double,
    current: String,
    predictionText: String = ""
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "배터리 상태",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

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

        // 실시간 계측값을 기반으로 한 게이지 프로그레스 계산 (토스 스타일 동적 바)
        val powerVal = power.toDoubleOrNull() ?: 0.0
        val powerProgress = if (powerUnit == "W") {
            (powerVal / 25.0).toFloat().coerceIn(0f, 1f) // 최대 25W 기준
        } else {
            (powerVal / 25000.0).toFloat().coerceIn(0f, 1f) // 최대 25000mW 기준
        }
        
        val voltageProgress = ((voltage - 3.0) / (4.5 - 3.0)).toFloat().coerceIn(0f, 1f) // 최소 3.0V ~ 최대 4.5V 기준
        
        val currentVal = current.toDoubleOrNull() ?: 0.0
        val currentProgress = if (currentVal > 5.0) {
            (currentVal / 3000.0).toFloat().coerceIn(0f, 1f) // 최대 3000mA 기준
        } else {
            (currentVal / 3.0).toFloat().coerceIn(0f, 1f) // 최대 3.0A 기준
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                title = "충전 속도",
                value = power,
                unit = powerUnit,
                accent = Blue600,
                progress = powerProgress
            )
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                title = "전압",
                value = voltage.toString(),
                unit = "V",
                accent = Color(0xFFA78BFA),
                progress = voltageProgress
            )
            SmallMetricCard(
                modifier = Modifier.weight(1f),
                title = "전류",
                value = current,
                unit = "mA",
                accent = Emerald500,
                progress = currentProgress
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}
