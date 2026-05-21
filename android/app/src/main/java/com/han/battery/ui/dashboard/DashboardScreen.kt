package com.han.battery.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.han.battery.data.model.BatteryDevice
import com.han.battery.ui.theme.Slate50
import com.han.battery.ui.dashboard.sections.AiAnalysisSection
import com.han.battery.ui.dashboard.sections.MonitoringSection
import com.han.battery.ui.dashboard.sections.PredictionSection
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    device: com.han.battery.data.model.BatteryDevice,
    onBack: () -> Unit,
    onChangeDevice: () -> Unit,
    onDeleteDevice: () -> Unit
) {
    val context = LocalContext.current
    val menuExpanded = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardUiEvent.ShowMessage -> {
                    Toast.makeText(
                        context,
                        event.message,
                        if (event.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // 데이터 구독
    val status by viewModel.batteryStatus.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val lastAnalysisResult by viewModel.lastAnalysisResult.collectAsState()

    LaunchedEffect(device.id, device.model_name) {
        viewModel.setDevice(device)
    }

    // UI 로직 계산
    val predictionText = when {
        !status.isCharging -> "방전 중 (충전 필요)"
        status.remainingTime > 0 -> {
            val hours = status.remainingTime / 60
            val mins = status.remainingTime % 60
            if (hours > 0) "${hours}시간 ${mins}분" else "${mins}분"
        }
        else -> "계산 중..."
    }

    val monitoringTitle = if (isMonitoring) "실시간 모니터링" else "모니터링 대기중"
    val powerW = status.voltage * status.current / 1000f
    val powerDisplayValue = if (abs(powerW) < 1f) {
        String.format("%.2f", powerW * 1000f)
    } else {
        String.format("%.2f", powerW)
    }
    val powerDisplayUnit = if (abs(powerW) < 1f) "mW" else "W"
    val currentDisplayValue = String.format("%.2f", status.current)
    // 버튼 상태 결정
    val isButtonEnabled = isMonitoring || status.isCharging
    val buttonText = if (isMonitoring) "진단 종료" else "AI 진단 시작"

    // 삭제 다이얼로그 로직
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
                        Text(text = device.model_name.ifBlank { "보조배터리" }, fontWeight = FontWeight.ExtraBold)
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
                    IconButton(onClick = { menuExpanded.value = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "옵션 메뉴")
                    }
                    DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("커뮤니티에 공유", fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded.value = false
                                viewModel.shareActiveDeviceToCommunity()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("기기 변경", fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded.value = false
                                onChangeDevice()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("기기 삭제", fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded.value = false
                                showDeleteDialog.value = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFFF6F8FC))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Slate50, Color(0xFFF6F8FC), Slate50)))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            MonitoringSection(
                isMonitoring = isMonitoring,
                soc = status.soc,
                soh = lastAnalysisResult?.soh_percentage?.toInt() ?: 100,
                power = powerDisplayValue,
                powerUnit = powerDisplayUnit,
                voltage = String.format("%.2f", status.voltage).toDouble(),
                current = currentDisplayValue,
                predictionText = predictionText
            )

            // 핵심 버튼 영역
            Button(
                enabled = isButtonEnabled,
                onClick = {
                    if (isMonitoring) viewModel.stopMonitoring() else viewModel.startMonitoring()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFD1D5DB),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // 비활성화 안내 텍스트
            if (!status.isCharging && !isMonitoring) {
                Text(
                    text = "보조배터리가 충전 중일 때만 진단이 가능합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))

            AiAnalysisSection(
                predictedTimeText = predictionText,
                analysisResult = lastAnalysisResult
            )
            Spacer(modifier = Modifier.height(4.dp))
            PredictionSection(
                analysisResult = lastAnalysisResult,
                currentSoc = status.soc,
                isCharging = status.isCharging
            )

            // 온도 정보 표기
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}