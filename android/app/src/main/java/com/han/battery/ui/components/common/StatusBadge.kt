package com.han.battery.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LiveStatusBadge(
    isLive: Boolean, // ⭐ [추가] 상태를 전달받을 파라미터
    modifier: Modifier = Modifier
) {
    // ⭐ [추가] 상태에 따라 색상과 텍스트 결정
    val badgeColor = if (isLive) Color(0xFF10b981) else Color(0xFF64748B) // Green vs Slate Gray
    val badgeText = if (isLive) "실시간 모니터링" else "모니터링 대기중"

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = badgeColor.copy(alpha = 0.12f), // 동적 색상 적용
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(badgeColor, CircleShape) // 동적 색상 적용
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = badgeText, // 동적 텍스트 적용
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor // 동적 색상 적용
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}