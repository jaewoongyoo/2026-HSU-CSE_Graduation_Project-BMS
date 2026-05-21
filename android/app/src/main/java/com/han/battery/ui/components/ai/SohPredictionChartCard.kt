package com.han.battery.ui.components.ai

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
import com.han.battery.data.model.SessionResultResponse
import com.han.battery.ui.theme.Amber500
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate300
import com.han.battery.ui.theme.Slate500

@Composable
fun SohPredictionChartCard(
    modifier: Modifier = Modifier,
    analysisResult: SessionResultResponse? = null
) {
    val currentSoh = analysisResult?.soh_percentage ?: 92.0
    val condition = analysisResult?.condition ?: "최상 (A+)"
    
    // LSTM 예측 기반 6개월간 매월 노화 속도 비율에 맞춰 감소하는 추세 생성
    val degradation = (analysisResult?.degradationRateRatio ?: 1.0) * 0.6
    val predictionPoints = List(7) { month ->
        currentSoh - (month * degradation)
    }
    
    val predictedSohSixMonthsLater = predictionPoints.last()
    val formattedSixMonthsLater = String.format("%.1f", predictedSohSixMonthsLater)

    // 안내 메세지 빌드
    val tipText = when {
        predictedSohSixMonthsLater < 80.0 -> "⚠ 6개월 후 SOH ${formattedSixMonthsLater}% 예상 — 배터리 교체를 강하게 권장합니다."
        predictedSohSixMonthsLater < 90.0 -> "💡 6개월 후 SOH ${formattedSixMonthsLater}% 예상 — 보관 시 완전 방전을 방지하여 수명을 늘리세요."
        else -> "✅ 6개월 후 SOH ${formattedSixMonthsLater}% 예상 — 건강도가 매우 안정적으로 양호합니다."
    }

    val tipBgColor = when {
        predictedSohSixMonthsLater < 80.0 -> Color(0xFFFFF0F0)
        predictedSohSixMonthsLater < 90.0 -> Color(0xFFFFF8E8)
        else -> Color(0xFFE8FAF0)
    }

    val tipTextColor = when {
        predictedSohSixMonthsLater < 80.0 -> Color(0xFFD32F2F)
        predictedSohSixMonthsLater < 90.0 -> Color(0xFF9A6700)
        else -> Color(0xFF2E7D32)
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
                text = "6개월 SOH 변화 예측 (현재 ${String.format("%.1f", currentSoh)}%)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "AI 모델 기반 배터리 수명 저하 시뮬레이션 (상태: $condition)",
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

                    // Y축 기준선 (100% ~ 70%)
                    for (i in 0..4) {
                        val y = h * i / 4f
                        drawLine(
                            color = Slate300.copy(alpha = 0.55f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    // X축 기준선 (0개월 ~ 6개월)
                    for (i in 0..6) {
                        val x = w * i / 6f
                        drawLine(
                            color = Slate300.copy(alpha = 0.45f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // SOH 100% -> y = 0, 70% -> y = h로 매핑
                    // 즉, y = h * (100 - soh) / 30f
                    val points = predictionPoints.mapIndexed { index, soh ->
                        val x = w * index / 6f
                        val clampedSoh = soh.coerceIn(70.0, 100.0)
                        val y = h * (100.0 - clampedSoh).toFloat() / 30f
                        Offset(x, y)
                    }

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

                    // 80% 교체 경고선 그리기
                    val warningY = h * 20f / 30f
                    drawLine(
                        color = Amber500,
                        start = Offset(0f, warningY),
                        end = Offset(w, warningY),
                        strokeWidth = 2f
                    )
                }

                Text(
                    text = "100%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Text(
                    text = "70%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            if (analysisResult?.confidence != null || (analysisResult?.sessionsUsed != null && analysisResult?.sessionsTotal != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    analysisResult.confidence?.let { conf ->
                        Text(
                            text = "🎯 예측 신뢰도: ${String.format("%.1f", conf * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (analysisResult.sessionsUsed != null && analysisResult.sessionsTotal != null) {
                        Text(
                            text = "📊 분석 세션: ${analysisResult.sessionsUsed}/${analysisResult.sessionsTotal}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tipBgColor
            ) {
                Text(
                    text = tipText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = tipTextColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
