package com.han.battery.ui.dashboard.sections
// 대시보드의 AI 분석 섹션 - 배터리 분석 카드들을 가로 스크롤 형식으로 표시

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
import com.han.battery.ui.components.ai.AiSummaryCard
import com.han.battery.ui.theme.Amber500
import com.han.battery.ui.theme.Blue100
import com.han.battery.ui.theme.Blue600

@Composable
fun AiAnalysisSection(predictedTimeText: String = "계산 중...") {

    // ⭐ 넘어온 상태 텍스트에 따라 카드 설명(desc)을 다르게 보여줍니다.
    val dynamicDesc = when {
        predictedTimeText.contains("분") -> "현재 충전 패턴을 기준으로 $predictedTimeText 뒤 완충이 예상됩니다."
        predictedTimeText.contains("방전") -> "충전기를 연결하시면 시스템이 완충까지 남은 시간을 분석해 드립니다."
        else -> "안정적인 전력 유입량을 측정하며 예측 시간을 분석하고 있습니다."
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
                value = "최상 (A+)", // 나중에 로직에 따라 동적으로 변경
                desc = "현재 전압 유지력과 출력 안정성이 매우 우수합니다. 배터리 셀 노후화가 거의 진행되지 않았습니다.",
                accent = Blue600,
                bg = Color(0xFFF5F8FF),
                icon = Icons.Default.AutoAwesome
            )

            AiSummaryCard(
                modifier = Modifier.width(208.dp),
                tag = "케이블 진단",
                title = "케이블 전력 손실",
                value = "15%",
                desc = "사용 중인 케이블에서 전력 손실이 15% 발생 중입니다.\n정품 케이블 교체 시 효율 개선 기대",
                accent = Amber500,
                bg = Color(0xFFFFFBF2),
                icon = Icons.Default.Bolt
            )
        }
    }
}