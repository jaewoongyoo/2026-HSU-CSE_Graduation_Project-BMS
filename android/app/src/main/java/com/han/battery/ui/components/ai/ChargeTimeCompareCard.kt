package com.han.battery.ui.components.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Emerald500
import com.han.battery.ui.theme.Slate500

@Composable
fun ChargeTimeCompareCard(
    modifier: Modifier = Modifier,
    currentSoc: Int = 78,
    isCharging: Boolean = false,
    estimatedFullCharges: Double? = null
) {
    val targetSoc = (100 - currentSoc).coerceAtLeast(0)

    val currentText: String
    val optimalText: String
    val lossText: String

    val currentProgress: Float
    val optimalProgress: Float
    val lossProgress: Float

    if (!isCharging) {
        currentText = "진단 대기"
        optimalText = "진단 대기"
        lossText = "진단 대기"
        currentProgress = 0f
        optimalProgress = 0f
        lossProgress = 0f
    } else if (targetSoc <= 0) {
        currentText = "완충됨"
        optimalText = "완충됨"
        lossText = "완충됨"
        currentProgress = 1f
        optimalProgress = 1f
        lossProgress = 1f
    } else {
        // 배터리 잔량에 따른 대략적인 소요 시간 계산
        val currentMin = (targetSoc * 0.55).toInt().coerceAtLeast(1)
        val optimalMin = (targetSoc * 0.45).toInt().coerceAtLeast(1)
        val lossMin = (targetSoc * 0.70).toInt().coerceAtLeast(1)

        currentText = "약 ${currentMin}분"
        optimalText = "약 ${optimalMin}분"
        lossText = "약 ${lossMin}분"

        // 차트에 채워질 progress (속도가 빠를수록 progress바가 더 길게 참)
        currentProgress = 0.82f
        optimalProgress = 0.91f
        lossProgress = 0.64f
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = Blue600,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "완충 시간 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val subtitle = if (isCharging) {
                "현재 잔량 (${currentSoc}%) → 100%"
            } else {
                "현재 잔량 (${currentSoc}%) — 진단 시 완충 예상 시간 분석 가능"
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )

            Spacer(modifier = Modifier.height(14.dp))

            TimeCompareBar(
                label = "현재 충전 환경",
                value = currentText,
                progress = currentProgress,
                color = Blue600
            )

            Spacer(modifier = Modifier.height(10.dp))

            TimeCompareBar(
                label = "최적 충전 환경",
                value = optimalText,
                progress = optimalProgress,
                color = Emerald500
            )

        }
    }
}

@Composable
private fun TimeCompareBar(
    label: String,
    value: String,
    progress: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.14f)
        )
    }
}
