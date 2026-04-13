package com.han.battery.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 실제로는 com.han.battery.data.model 패키지에서 import 해야 합니다.
data class BatteryPerformancePost(
    val id: String,
    val userName: String,
    val smartphoneModel: String,
    val powerBankModel: String,
    val totalUsageHours: Int,
    val efficiency: Int, // 전력 변환 효율 (%)
    val estimatedSoh: Int // 예상 수명 (State of Health)
)

@Composable
fun BoardScreen(
    posts: List<BatteryPerformancePost>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "모두의 배터리 기록",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(posts) { post ->
                    PerformancePostCard(post = post)
                }
            }
        }
    }
}

@Composable
fun PerformancePostCard(post: BatteryPerformancePost) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 사용자 및 기종 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "사용자: ${post.userName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(text = "데이터 신뢰도 인증", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "📱 폰: ${post.smartphoneModel}", fontSize = 14.sp)
            Text(text = "🔋 배터리: ${post.powerBankModel}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // 성능 지표 영역 (AI 및 수집 데이터 기반)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem(label = "누적 사용", value = "${post.totalUsageHours}h")
                MetricItem(label = "실제 효율", value = "${post.efficiency}%")
                MetricItem(label = "예상 SOH", value = "${post.estimatedSoh}%")
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}