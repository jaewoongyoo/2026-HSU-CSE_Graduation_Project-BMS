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

    // 기기 삭제 확인 다이얼로그 (기존 유지)
    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = false },
            title = { Text("배터리 삭제") },
            text = { Text("'${device.nickname.ifBlank { "배터리" }}'을(를) 삭제하시겠습니까?") },
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
                            text = device.nickname.ifBlank { "보조배터리" },
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${device.brand.ifBlank { "브랜드 미입력" }} · ${device.capacity} mAh",
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
                    LiveStatusBadge() // 실시간 연결 표시 (Member A의 UI)

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
        }
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // ── 1. 실시간 모니터링 섹션 (실제 데이터 주입) ──
            MonitoringSection(
                soc = status.soc,
                soh = 92,
                // 소수점 1자리까지 (예: 0.3W)
                power = String.format("%.1f", (status.voltage * status.current / 1000f)).toDouble(),
                // 소수점 2자리까지 (예: 4.20V)
                voltage = String.format("%.2f", status.voltage).toDouble(),
                current = status.current.toInt()
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            // ── 2. AI 분석 섹션 (AI 분석 로직 반영 가능) ──
            AiAnalysisSection()

            Spacer(modifier = Modifier.height(14.dp))

            // ── 3. 예측 차트 섹션 ──
            PredictionSection()

            // ── 4. 온도 정보 (Member B의 데이터 활용) ──
            Spacer(modifier = Modifier.height(20.dp))
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