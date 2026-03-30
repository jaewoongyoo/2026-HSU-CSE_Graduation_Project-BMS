package com.han.battery.ui.components.ai
// 배터리 건강도(SOH) 예측 및 화학적 변화를 선 차트로 표시하는 AI 분석 컴포넌트

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.han.battery.ui.theme.Amber500
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate300
import com.han.battery.ui.theme.Slate500

@Composable
fun SohPredictionChartCard(modifier: Modifier = Modifier) {
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
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = Blue600,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LSTM 수명 시뮬레이션",
                    style = MaterialTheme.typography.labelSmall,
                    color = Blue600,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "6개월 SOH 변화 예측",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "AI 모델 기반 배터리 수명 저하 시뮬레이션",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    for (i in 0..4) {
                        val y = h * i / 4f
                        drawLine(
                            color = Slate300.copy(alpha = 0.55f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    for (i in 0..6) {
                        val x = w * i / 6f
                        drawLine(
                            color = Slate300.copy(alpha = 0.45f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    val points = listOf(
                        Offset(0f, h * 0.18f),
                        Offset(w * 0.16f, h * 0.22f),
                        Offset(w * 0.33f, h * 0.28f),
                        Offset(w * 0.50f, h * 0.37f),
                        Offset(w * 0.67f, h * 0.43f),
                        Offset(w * 0.84f, h * 0.52f),
                        Offset(w, h * 0.59f)
                    )

                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { point ->
                            lineTo(point.x, point.y)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = Blue600,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )

                    points.forEach {
                        drawCircle(
                            color = Blue600,
                            radius = 6f,
                            center = it
                        )
                    }

                    val warningY = h * 0.53f
                    drawLine(
                        color = Amber500,
                        start = Offset(0f, warningY),
                        end = Offset(w, warningY),
                        strokeWidth = 2f
                    )
                }

                Text(
                    text = "100",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    text = "70",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFF8E8)
            ) {
                Text(
                    text = "⚠ 6개월 후 SOH 77% 예상 — 교체 검토를 권장합니다.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A6700),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
