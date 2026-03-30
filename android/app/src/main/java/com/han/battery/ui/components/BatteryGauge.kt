package com.han.battery.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BatteryGauge(value: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        // 배경 회색 원
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 12.dp
        )
        // 실제 충전량 표시 푸른 원
        CircularProgressIndicator(
            progress = value / 100f,
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 12.dp
        )
        // 중앙 퍼센트 텍스트
        Text(
            text = "$value%",
            style = MaterialTheme.typography.headlineLarge
        )
    }
}