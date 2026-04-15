package com.han.battery.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.han.battery.DeviceInfo
import com.han.battery.ui.theme.Slate50
import com.han.battery.ui.components.common.LiveStatusBadge
import com.han.battery.ui.dashboard.sections.AiAnalysisSection
import com.han.battery.ui.dashboard.sections.MonitoringSection
import com.han.battery.ui.dashboard.sections.PredictionSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel, // 실시간 데이터 주입
    device: DeviceInfo,            // 기기 정보 주입
    onBack: () -> Unit,
    onChangeDevice: () -> Unit,
    onDeleteDevice: () -> Unit
) {
    // ── 상태 관리 (UI) ──
    val menuExpanded = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }

    // ── 실시간 데이터 구독 (Logic) ──
    val status by viewModel.batteryStatus.collectAsState()

    // ⭐ 충전 완료 예상 시간 텍스트 로직 추가
    val predictionText = when {
        !status.isCharging -> "방전 중 (충전 필요)"
        status.remainingTime > 0 -> {
            val hours = status.remainingTime / 60
            val mins = status.remainingTime % 60

            if (hours > 0) {
                "${hours}시간 ${mins}분"
            } else {
                "${mins}분"
            }
        }
        else -> "계산 중..."
    }

    // 기기 삭제 확인 다이얼로그
    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = false },
            title = { Text("배터리 삭제") },
            text = { Text("'${device.model_name.ifBlank { "배터리" }}'을(를) 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog.value = false
                        onDeleteDevice()
                    }
                ) { Text("삭제", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog.value = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = device.model_name.ifBlank { "보조배터리" },
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${device.powerbank_capacity_mah} mAh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    LiveStatusBadge() // 실시간 연결 표시

                    IconButton(onClick = { menuExpanded.value = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "옵션 메뉴")
                    }

                    DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("기기 변경") },
                            onClick = {
                                menuExpanded.value = false
                                onChangeDevice()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("기기 삭제") },
                            onClick = {
                                menuExpanded.value = false
                                showDeleteDialog.value = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF6F8FC)
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Slate50, Color(0xFFF6F8FC), Slate50)
                    )
                )
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 0.dp)
        ) {
            // ── 1. 실시간 모니터링 섹션 ──
            MonitoringSection(
                soc = status.soc,
                soh = 92,
                power = String.format("%.1f", (status.voltage * status.current / 1000f)).toDouble(),
                voltage = String.format("%.2f", status.voltage).toDouble(),
                current = status.current.toInt(),
                predictionText = predictionText // ⭐ AiAnalysisSection에 있던 걸 여기로 옮깁니다!
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // ── 2. AI 분석 섹션 (예측된 텍스트 전달) ──
            // ⭐ AiAnalysisSection에 값을 넘겨주도록 변경했습니다.
            AiAnalysisSection(
                predictedTimeText = predictionText
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 3. 예측 차트 섹션 ──
            PredictionSection()

            // ── 4. 온도 정보 ──
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "현재 배터리 온도: ${status.temperature}°C",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}