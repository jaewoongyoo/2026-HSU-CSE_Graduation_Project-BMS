package com.han.battery.ui.home
// 저장된 배터리 기기 목록을 표시하고 기기를 선택/등록하는 홈 화면

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.data.model.BatteryDevice
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate50
import com.han.battery.ui.components.common.AppLogo
import com.han.battery.ui.components.common.LogoSize

@Composable
fun HomeScreen(
    devices: List<BatteryDevice>,
    onDeviceSelected: (BatteryDevice) -> Unit,
    onAddNewDevice: () -> Unit,
    onDeleteDevice: (BatteryDevice) -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToBoard: () -> Unit
) {
    var deviceToDelete by remember { mutableStateOf<BatteryDevice?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 로그아웃 확인 다이얼로그
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃") },
            text = { Text("정말 로그아웃하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("로그아웃", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 삭제 확인 다이얼로그
    deviceToDelete?.let { device ->
        DeleteDeviceDialog(
            device = device,
            onConfirm = {
                onDeleteDevice(device)
                deviceToDelete = null
            },
            onDismiss = { deviceToDelete = null }
        )
    }
    
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGradient))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
        ) {
            // 헤더
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 헤더 상단 - 로그아웃 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "로그아웃",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AppLogo(size = LogoSize.Medium, showText = false)
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Battery Insight",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "나의 배터리를 관리하세요",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            UserManualCard(isDefaultExpanded = devices.isEmpty())
            
            Spacer(modifier = Modifier.height(16.dp))

            // 기기 목록
            if (devices.isEmpty()) {
                // 등록된 기기 없음
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        EmptyDeviceIllustration()
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "등록된 배터리가 없습니다",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "아래 버튼을 눌러 새 배터리를 등록하세요",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 저장된 기기 목록
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceSelected(device) },
                            onDeleteClick = { deviceToDelete = device }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 새 기기 등록 버튼
            Button(
                onClick = onAddNewDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "새 기기 추가",
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "새 배터리 등록",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun DeviceCard(
    device: BatteryDevice,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFFFAFBFC),
                Color(0xFFF1F5F9)
            )
        )
    }
    
    val borderStroke = if (isDark) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    } else {
        BorderStroke(1.dp, Color(0xFFE2E8F0))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDark) 0.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.model_name.ifBlank { "배터리" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${device.powerbank_capacity_mah} mAh",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (device.manufacturer.isNotBlank()) {
                    Text(
                        text = "제조사: ${device.manufacturer}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                    text = "등록일: ${device.manufacture_date}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 삭제 버튼 - 세련된 디자인
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(onClick = onDeleteClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 고급 배터리 팩 모양 아이콘 인디케이터
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 기기 삭제 확인 다이얼로그
 */
@Composable
fun DeleteDeviceDialog(
    device: BatteryDevice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("배터리 삭제", fontWeight = FontWeight.Bold) },
        text = { Text("'${device.model_name}'을(를) 정말 삭제하시겠습니까?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 사용자를 위한 아코디언 타입 사용 설명서 카드
 */
@Composable
fun UserManualCard(
    isDefaultExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(isDefaultExpanded) { mutableStateOf(isDefaultExpanded) }
    val isDark = isSystemInDarkTheme()
    
    val borderStroke = if (isDark) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    } else {
        BorderStroke(1.dp, Color(0xFFE2E8F0))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "사용자를 위한 사용 설명서 💡",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ManualStepItem(
                        stepNumber = "1",
                        title = "새 배터리 등록",
                        description = "하단의 '새 배터리 등록' 버튼을 눌러 보조배터리 제조사 및 용량을 입력합니다."
                    )
                    ManualStepItem(
                        stepNumber = "2",
                        title = "AI 배터리 진단",
                        description = "등록된 배터리 카드를 눌러 대시보드로 이동한 후, 충전 중에 'AI 진단 시작'을 터치합니다. (최소 20분 이상 충전 분석 권장)"
                    )
                    ManualStepItem(
                        stepNumber = "3",
                        title = "SOH 성능 공유하기",
                        description = "진단 완료 후 상세 대시보드 화면 우측 상단 더보기(⋮) 메뉴에서 '커뮤니티에 공유'를 누르면 다른 사용자들과 분석 결과가 공유됩니다."
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 기기가 등록되어 있지 않을 때 표시되는 Canvas 기반 펄싱 일러스트레이션
 */
@Composable
fun EmptyDeviceIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyState")
    val batteryPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val primary = MaterialTheme.colorScheme.primary
    
    Box(
        modifier = modifier.size(width = 140.dp, height = 90.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // 배터리 몸체 그리기 (둥근 사각형)
            val bodyWidth = w * 0.82f
            val bodyHeight = h * 0.7f
            val bodyLeft = (w - bodyWidth) / 2f - 4f
            val bodyTop = (h - bodyHeight) / 2f
            
            drawRoundRect(
                color = primary.copy(alpha = 0.08f),
                topLeft = Offset(bodyLeft, bodyTop),
                size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = primary.copy(alpha = 0.5f),
                topLeft = Offset(bodyLeft, bodyTop),
                size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                style = Stroke(width = 4f)
            )
            
            // 배터리 단자 그리기 (우측 돌기)
            val capWidth = w * 0.05f
            val capHeight = h * 0.22f
            val capLeft = bodyLeft + bodyWidth
            val capTop = (h - capHeight) / 2f
            
            drawRoundRect(
                color = primary.copy(alpha = 0.5f),
                topLeft = Offset(capLeft, capTop),
                size = androidx.compose.ui.geometry.Size(capWidth, capHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            
            // 내부 충전 펄스 게이지 그리기 (3칸)
            val innerLeft = bodyLeft + 12f
            val innerTop = bodyTop + 12f
            val innerWidth = bodyWidth - 24f
            val innerHeight = bodyHeight - 24f
            val segmentWidth = (innerWidth - 16f) / 3f
            
            for (i in 0..2) {
                val segLeft = innerLeft + i * (segmentWidth + 8f)
                drawRoundRect(
                    color = primary.copy(alpha = batteryPulseAlpha),
                    topLeft = Offset(segLeft, innerTop),
                    size = androidx.compose.ui.geometry.Size(segmentWidth, innerHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
        }
    }
}
