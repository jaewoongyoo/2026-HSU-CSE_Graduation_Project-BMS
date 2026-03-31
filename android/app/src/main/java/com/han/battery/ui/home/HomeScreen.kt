package com.han.battery.ui.home
// 저장된 배터리 기기 목록을 표시하고 기기를 선택/등록하는 홈 화면

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
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
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate50
import com.han.battery.ui.components.common.AppLogo
import com.han.battery.ui.components.common.LogoSize

@Composable
fun HomeScreen(
    key: Int = 0,
    devices: List<BatteryDevice>,
    onDeviceSelected: (BatteryDevice) -> Unit,
    onAddNewDevice: () -> Unit,
    onDeleteDevice: (BatteryDevice) -> Unit = {}
) {
    var deviceToDelete by remember { mutableStateOf<BatteryDevice?>(null) }
    
    // 삭제 확인 다이얼로그
    if (deviceToDelete != null) {
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text("배터리 삭제") },
            text = { Text("'${deviceToDelete!!.nickname}'을(를) 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val device = deviceToDelete
                        if (device != null) {
                            onDeleteDevice(device)
                        }
                        deviceToDelete = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deviceToDelete = null }
                ) {
                    Text("취소")
                }
            }
        )
    }
    
    Box(
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // 헤더
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppLogo(size = LogoSize.Medium, showText = false)
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Battery Insight",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Blue600
                )
                Text(
                    text = "나의 배터리를 관리하세요",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                        Text(
                            text = "등록된 배터리가 없습니다",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "아래 버튼을 눌러 새 배터리를 등록하세요",
                            fontSize = 13.sp,
                            color = Color.Gray
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
                    containerColor = Blue600
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
        }
    }
}

@Composable
fun DeviceCard(
    device: BatteryDevice,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.nickname.ifBlank { "배터리" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${device.brand} · ${device.capacity}mAh",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "제조: ${device.manufactureDate}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 삭제 버튼
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "삭제",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // AppLogo 사용
                AppLogo(
                    size = LogoSize.Small,
                    showText = false
                )
            }
        }
    }
}
