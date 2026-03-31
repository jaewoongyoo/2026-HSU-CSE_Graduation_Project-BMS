package com.han.battery.ui.landing.sections
// 랜딩 스크린의 영웅 섹션 - 배터리 일러스트레이션과 부드러운 애니메이션 표시

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.han.battery.ui.theme.Blue600

/**
 * 배터리 일러스트레이션을 표시하는 영웅 섹션
 * - 부드러운 부유 애니메이션 (상하 이동)
 * - 발광 효과 (Glow) 애니메이션
 * - 배터리 충전 아이콘과 강조 점 표시
 */
@Composable
fun HeroSection() {
    val transition = rememberInfiniteTransition(label = "battery_hero")
    
    // 상하 부유 애니메이션 (-4dp ~ +4dp)
    val floatOffset = transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    ).value
    
    // 발광 효과 애니메이션 (60% ~ 100% 투명도)
    val glowAlpha = transition.animateFloat(
        initialValue = 0.60f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    ).value

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .offset(y = floatOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            // 배터리 주위 발광 효과
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .alpha(glowAlpha * 0.35f)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                Blue600.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            BatteryIllustration()
        }
    }
}

/**
 * 배터리 일러스트레이션 UI
 * - 배터리 외형 (흰색 박스)
 * - 배터리 충전 상태 (파란색 그래디언트)
 * - 충전 중 표시 (노란색 번개 아이콘)
 * - 강조 점 (알파 투명도 연출)
 */
@Composable
private fun BatteryIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.4f)
            .height(120.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // 배터리 단자 부분
        Box(
            modifier = Modifier
                .fillMaxWidth(0.67f)
                .height(10.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Color(0xFFD9E3FF))
        )

        // 배터리 본체
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.92f)
                .height(102.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(
                    BorderStroke(1.dp, Blue600.copy(alpha = 0.15f)),
                    RoundedCornerShape(18.dp)
                )
        ) {
            // 배터리 충전 상태 표시 (파란색 그래디언트)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF7BB0FF),
                                Blue600
                            )
                        )
                    )
            )

            // 충전 중 표시 아이콘 (번개)
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "배터리 충전 아이콘",
                tint = Color(0xFFFFD54F),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )

            // 강조 점 1 (왼쪽)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 12.dp, y = 20.dp)
                    .size(4.dp)
                    .background(Color(0xFFFFD54F).copy(alpha = 0.85f), CircleShape)
            )

            // 강조 점 2 (오른쪽)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-14).dp, y = 10.dp)
                    .size(5.dp)
                    .background(Color(0xFFFFD54F).copy(alpha = 0.75f), CircleShape)
            )
        }
    }
}



