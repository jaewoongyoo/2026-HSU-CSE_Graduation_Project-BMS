package com.han.battery.ui.landing.sections
// 랜딩 스크린의 기능 소개 섹션 - 앱의 주요 기능(실시간 모니터링, AI 예측 등)을 아이콘과 함께 표시

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Emerald500
import com.han.battery.ui.theme.Slate500

@Composable
fun FeatureSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeatureItem(
            icon = Icons.Default.BatteryChargingFull,
            color = Emerald500,
            label = "실시간 SOC"
        )
        FeatureItem(
            icon = Icons.Default.Shield,
            color = Blue600,
            label = "건강도 분석"
        )
        FeatureItem(
            icon = Icons.Default.TipsAndUpdates,
            color = Color(0xFF8B5CF6),
            label = "AI 예측"
        )
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    color: Color,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,  // ✅ 레이블을 설명으로 사용
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Slate500,
            fontWeight = FontWeight.Medium
        )
    }
}

