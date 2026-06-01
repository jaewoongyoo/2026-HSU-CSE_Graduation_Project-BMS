package com.han.battery.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.unit.sp
import com.han.battery.data.model.BatteryDevice
import com.han.battery.ui.theme.Slate50
import com.han.battery.ui.dashboard.sections.AiAnalysisSection
import com.han.battery.ui.dashboard.sections.MonitoringSection
import com.han.battery.ui.dashboard.sections.PredictionSection
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager


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
    var showPowerBankConfirmDialog by remember { mutableStateOf(false) }

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
    val stats by viewModel.telemetryStats.collectAsState()

    val pluggedType = remember(status.isCharging) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    }

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
    // AC 또는 무선 충전 감지 여부 (다이얼로그 경고 문구용)
    val isAcOrWireless = pluggedType == BatteryManager.BATTERY_PLUGGED_AC || pluggedType == BatteryManager.BATTERY_PLUGGED_WIRELESS
    // 버튼 상태 결정 - 충전 중이기만 하면 진단 시작 허용
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

    // 보조배터리 확인 다이얼로그 로직
    if (showPowerBankConfirmDialog) {
        val warningText = if (isAcOrWireless) {
            "⚠️ 현재 기기가 일반 콘센트 전원(AC) 또는 무선 충전으로 감지되었습니다.\n\n벽면 충전기가 아닌 '고속 충전 보조배터리'를 스마트폰에 연결하신 것이 맞나요?\n\n(고성능 보조배터리의 경우 시스템에서 일반 AC 충전기로 오인해 분류할 수 있습니다. 보조배터리 연결이 확실하다면 아래 버튼을 눌러 시작해 주세요.)"
        } else {
            "노트북이나 벽면 콘센트 충전기가 아닌, 실제 보조배터리를 스마트폰에 연결하셨나요?\n\n노트북이나 다른 기기로 충전할 경우 전력 분석이 왜곡되어 정확한 건강도 측정이 불가능합니다."
        }

        AlertDialog(
            onDismissRequest = { showPowerBankConfirmDialog = false },
            title = { Text("보조배터리 연결 확인 🔌", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = warningText,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPowerBankConfirmDialog = false
                        viewModel.startMonitoring()
                    }
                ) {
                    Text("예, 연결했습니다", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPowerBankConfirmDialog = false }) {
                    Text("취소")
                }
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val isDark = isSystemInDarkTheme()
        val bgGradient = if (isDark) {
            listOf(
                MaterialTheme.colorScheme.background,
                Color(0xFF020617),
                Color(0xFF0B1329)
            )
        } else {
            listOf(
                Slate50,
                Color(0xFFF6F8FC),
                Slate50
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(bgGradient))
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

            if (isMonitoring) {
                DiagnosticEtaCard(
                    totalCollected = stats.totalCollected,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 핵심 버튼 영역
            Button(
                enabled = isButtonEnabled,
                onClick = {
                    if (isMonitoring) {
                        viewModel.stopMonitoring()
                    } else {
                        showPowerBankConfirmDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // 비활성화 및 제한 안내 알림 박스
            if (!status.isCharging && !isMonitoring) {
                Spacer(modifier = Modifier.height(14.dp))
                ErrorNotificationBox(
                    message = "보조배터리가 충전 중일 때만 AI 진단이 가능합니다. 기기를 충전 전원에 연결해주세요."
                )
            } else if (status.isCharging && isAcOrWireless && !isMonitoring) {
                Spacer(modifier = Modifier.height(14.dp))
                WarningNotificationBox(
                    message = "고속 충전 전원(AC) 연결 감지. 일반 콘센트가 아닌 보조배터리를 충전 중인지 확인해주세요."
                )
            }

            if (isMonitoring) {
                Spacer(modifier = Modifier.height(12.dp))
                TelemetryMonitorCard(stats = stats)
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

@Composable
fun DiagnosticEtaCard(
    totalCollected: Int,
    modifier: Modifier = Modifier
) {
    val minCount = 600 // 최소 20분
    val targetCount = 900 // 권장 30분
    
    val elapsedSeconds = totalCollected * 2
    val minSeconds = minCount * 2
    val targetSeconds = targetCount * 2
    
    val progress = (totalCollected.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
    
    val etaText = when {
        totalCollected >= targetCount -> "권장 진단 완료! 언제든 종료하셔도 좋습니다. 🎉"
        totalCollected >= minCount -> {
            val remain = targetSeconds - elapsedSeconds
            val min = remain / 60
            val sec = remain % 60
            "권장 분석 완료까지: ${min}분 ${sec}초 남음"
        }
        else -> {
            val remain = minSeconds - elapsedSeconds
            val min = remain / 60
            val sec = remain % 60
            "최소 분석 가능까지: ${min}분 ${sec}초 남음"
        }
    }
    
    // 부드러운 숨쉬기 펄스 애니메이션 (스케일 미세 조정)
    val infiniteTransition = rememberInfiniteTransition(label = "etaCard")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⏱️ 실시간 진단 진행 상황",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // ETA 문구
            Text(
                text = etaText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).background(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp)
                ),
                color = if (totalCollected >= minCount) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "진행률: ${(progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "수집: $totalCollected / $targetCount 건",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TelemetryMonitorCard(
    stats: com.han.battery.service.TelemetryStats,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val statusText = when {
        stats.failed > 0 -> "오류"
        stats.httpSuccess > 0 && stats.mqttSuccess == 0 -> "HTTP 우회"
        stats.mqttSuccess > 0 -> "MQTT 안정"
        else -> "대기 중"
    }
    
    val statusColor = when {
        stats.failed > 0 -> MaterialTheme.colorScheme.error
        stats.mqttSuccess > 0 -> Color(0xFF22C55E)
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📡",
                        fontSize = 15.sp
                    )
                    Text(
                        text = "텔레메트리 전송 모니터",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                
                Surface(
                    color = when {
                        stats.failed > 0 -> MaterialTheme.colorScheme.errorContainer
                        stats.mqttSuccess > 0 -> Color(0xFFDCFCE7)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        StatusPulseCircle(color = statusColor)
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                stats.failed > 0 -> MaterialTheme.colorScheme.onErrorContainer
                                stats.mqttSuccess > 0 -> Color(0xFF15803D)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatsRow(label = "📥 누적 수집 로그 수", value = "${stats.totalCollected}건")
                    StatsRow(
                        label = "⚡ AWS IoT (MQTT) 성공",
                        value = "${stats.mqttSuccess}건",
                        valueColor = Color(0xFF16A34A)
                    )
                    StatsRow(
                        label = "🌐 HTTP 대체 전송 성공",
                        value = "${stats.httpSuccess}건",
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    StatsRow(
                        label = "⏳ 전송 대기열 (Pending)",
                        value = "${stats.pending}건",
                        valueColor = if (stats.pending > 0) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatsRow(
                        label = "❌ 전송 실패 (Failed)",
                        value = "${stats.failed}건",
                        valueColor = if (stats.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "터치하여 실시간 데이터 전송 통계 보기",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

/**
 * 실시간 상태 펄스를 시각적으로 나타내는 애니메이션 서클 컴포넌트
 */
@Composable
fun StatusPulseCircle(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseCircle")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = androidx.compose.animation.core.EaseOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = androidx.compose.animation.core.EaseOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier.size(14.dp),
        contentAlignment = Alignment.Center
    ) {
        // 외곽 펄스 링
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha
                )
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        // 중심 고정 서클
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
    }
}

/**
 * 시스템 경고 안내 박스
 */
@Composable
fun WarningNotificationBox(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFFBEB), // Amber 50 (매우 부드러운 주황색 미색)
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)) // Amber 200 선명한 주황색 실선
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 18.sp
            )
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB45309) // Amber 700 차분한 진한 주황색 텍스트
            )
        }
    }
}

/**
 * 시스템 에러/제한 안내 박스
 */
@Composable
fun ErrorNotificationBox(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFEF2F2), // Red 50 (매우 부드러운 빨간색 미색)
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFECACA)) // Red 200
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "🛑",
                fontSize = 18.sp
            )
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB91C1C) // Red 700
            )
        }
    }
}