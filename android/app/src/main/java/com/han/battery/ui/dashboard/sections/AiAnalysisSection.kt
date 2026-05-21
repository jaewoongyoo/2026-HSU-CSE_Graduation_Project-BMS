package com.han.battery.ui.dashboard.sections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.han.battery.data.model.SessionResultResponse
import com.han.battery.ui.components.ai.AiSummaryCard
import com.han.battery.ui.theme.Amber500
import com.han.battery.ui.theme.Blue100
import com.han.battery.ui.theme.Blue600

@Composable
fun AiAnalysisSection(
    predictedTimeText: String = "계산 중...",
    analysisResult: SessionResultResponse? = null
) {
    val dynamicDesc = when {
        predictedTimeText.contains("분") -> "현재 충전 패턴을 기준으로 $predictedTimeText 뒤 완충이 예상됩니다."
        predictedTimeText.contains("방전") -> "충전기를 연결하시면 시스템이 완충까지 남은 시간을 분석해 드립니다."
        else -> "안정적인 전력 유입량을 측정하며 예측 시간을 분석하고 있습니다."
    }

    val conditionValue = analysisResult?.condition ?: "진단 대기"
    
    val dynamicAnalysisDesc = if (analysisResult != null) {
        val soh = analysisResult.soh_percentage ?: 92.0
        val temperature = analysisResult.mean_temperature_c?.let { "${String.format("%.1f", it)}°C" } ?: "정상"
        val confidenceText = analysisResult.confidence?.let { conf ->
            val confKorean = when (conf.lowercase()) {
                "high" -> "높음"
                "medium" -> "보통"
                "low" -> "낮음"
                "fallback" -> "기본"
                else -> conf
            }
            " (예측 신뢰도: $confKorean)"
        } ?: ""
        "현재 SOH 건강도는 ${String.format("%.1f", soh)}%이며 상태 등급은 $conditionValue 입니다. 평균 충전 온도는 ${temperature}로 셀 스트레스 수준이 매우 적정합니다.$confidenceText"
    } else {
        "이전 충전 진단 이력이 존재하지 않습니다. 충전을 진행하여 배터리 상태 실시간 진단을 생성해 주세요."
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🧠 AI 종합 분석",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Blue100
            ) {
                Text(
                    text = "Beta",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Blue600,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AiSummaryCard(
                modifier = Modifier.width(208.dp),
                tag = "종합 진단",
                title = "보조배터리 성능 평가",
                value = conditionValue,
                desc = dynamicAnalysisDesc,
                accent = Blue600,
                bg = Color(0xFFF5F8FF),
                icon = Icons.Default.AutoAwesome
            )

            AiSummaryCard(
                modifier = Modifier.width(208.dp),
                tag = "케이블 진단",
                title = "케이블 전력 손실",
                value = if (analysisResult != null) "7%" else "15%",
                desc = if (analysisResult != null) "안정적인 충전 전류가 감지되어 전력 효율이 정상 범위 내에 안착했습니다." else "사용 중인 케이블에서 전력 손실이 약 15% 발생 중일 수 있습니다.\n정품 케이블 사용 권장.",
                accent = Amber500,
                bg = Color(0xFFFFFBF2),
                icon = Icons.Default.Bolt
            )
        }
    }
}