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
import com.han.battery.ui.components.common.SectionHeader
import com.han.battery.ui.theme.Amber500
import com.han.battery.ui.theme.Blue100
import com.han.battery.ui.theme.Blue600

@Composable
fun AiAnalysisSection() {
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
                tag = "회귀 모델",
                title = "AI 완충 시간 예측",
                value = "42분",
                desc = "현재 충전 패턴으로 42분 뒤 완충 예상됩니다.\n평균 충전 속도 18.5W 기준",
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


