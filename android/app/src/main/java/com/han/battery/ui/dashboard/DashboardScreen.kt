package com.han.battery.ui.dashboard
// 배터리 상태 분석 결과를 표시하는 메인 대시보드 스크린 (SOC, SOH, 예측 차트 등)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    device: DeviceInfo,
    onBack: () -> Unit,
    onChangeDevice: (() -> Unit)? = null,
    onDeleteDevice: (() -> Unit)? = null
) {
    val menuExpanded = remember { mutableStateOf(false) }
    
    // 더미 데이터 (향후 ViewModel과 연동될 예정)
    val soc = 78
    val soh = 92
    val power = 18.5
    val voltage = 5.1
    val current = 2400

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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    LiveStatusBadge()
                    
                    IconButton(onClick = { menuExpanded.value = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "옵션 메뉴"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = menuExpanded.value,
                        onDismissRequest = { menuExpanded.value = false }
                    ) {
                        if (onChangeDevice != null) {
                            DropdownMenuItem(
                                text = { Text("기기 변경") },
                                onClick = {
                                    menuExpanded.value = false
                                    onChangeDevice()
                                }
                            )
                        }
                        
                        if (onDeleteDevice != null) {
                            DropdownMenuItem(
                                text = { Text("기기 삭제") },
                                onClick = {
                                    menuExpanded.value = false
                                    onDeleteDevice()
                                }
                            )
                        }
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
                        listOf(
                            Slate50,
                            Color(0xFFF6F8FC),
                            Slate50
                        )
                    )
                )
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // ...existing code...
            MonitoringSection(
                soc = soc,
                soh = soh,
                power = power,
                voltage = voltage,
                current = current
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(16.dp))

            // ...existing code...
            AiAnalysisSection()

            Spacer(modifier = Modifier.height(14.dp))

            // ...existing code...
            PredictionSection()
        }
    }
}