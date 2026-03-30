package com.han.battery.ui.components.common
// 각 섹션의 제목을 표시하는 헤더 컴포넌트 - 번개 아이콘과 함께 제목 텍스트 표시

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    title: String
) {
    Text(
        text = "⚡ $title",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

