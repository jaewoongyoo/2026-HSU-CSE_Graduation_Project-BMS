package com.han.battery.ui.components.common
// 앱 로고 표시 컴포넌트 - Bolt 아이콘과 "Battery Insight" 텍스트를 표시

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.ui.theme.Blue600

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: LogoSize = LogoSize.Medium,
    showText: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size.boxSize)
                .clip(RoundedCornerShape(size.borderRadius))
                .background(Blue600.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "앱 로고 - 배터리",
                tint = Blue600,
                modifier = Modifier.size(size.iconSize)
            )
        }

        if (showText) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "BatteryAI",
                fontSize = size.textSize,
                fontWeight = FontWeight.ExtraBold,
                color = Blue600,
                letterSpacing = 0.9.sp
            )
        }
    }
}


enum class LogoSize(
    val boxSize: androidx.compose.ui.unit.Dp,
    val iconSize: androidx.compose.ui.unit.Dp,
    val borderRadius: androidx.compose.ui.unit.Dp,
    val textSize: androidx.compose.ui.unit.TextUnit
) {
    Small(28.dp, 14.dp, 6.dp, 10.sp),
    Medium(44.dp, 22.dp, 10.dp, 14.sp),
    Large(70.dp, 32.dp, 14.dp, 20.sp)
}

