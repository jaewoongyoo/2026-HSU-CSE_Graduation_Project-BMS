package com.han.battery.ui.dashboard.sections
// 대시보드의 예측 섹션 - SOH 예측 차트 및 충전 시간 비교 데이터 표시

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.han.battery.ui.components.ai.ChargeTimeCompareCard
import com.han.battery.ui.components.ai.SohPredictionChartCard
import com.han.battery.ui.theme.Slate500

@Composable
fun PredictionSection() {
    Column {
        SohPredictionChartCard()

        Spacer(modifier = Modifier.height(14.dp))

        ChargeTimeCompareCard()

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "AI 분석은 현재 사용 배터리 기준으로 예측됩니다.\n마지막 업데이트: 방금 전",
            style = MaterialTheme.typography.bodySmall,
            color = Slate500,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(18.dp))
    }
}

