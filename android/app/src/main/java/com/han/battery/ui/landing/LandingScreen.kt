package com.han.battery.ui.landing

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.DeviceInfo
import com.han.battery.ui.theme.Slate50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    onStartClick: (DeviceInfo) -> Unit,
    onBackClick: () -> Unit
) {
    // ── 상태 관리 ──
    var brand by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var manufactureDate by remember { mutableStateOf("") }

    val isFormValid = nickname.isNotBlank() && capacity.isNotBlank()

    // ── 애니메이션 로직 ──
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val batteryScale by infiniteTransition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )
    val batteryAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alpha"
    )
    val fillProgress by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "fill"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Slate50, Color(0xFFF6F8FC), Slate50))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 배경 글로우 (첫 번째 코드의 BackgroundGlow 스타일 적용)
        Box(
            modifier = Modifier
                .size(600.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
        )

        // 뒤로가기 버튼 (왼쪽 상단)
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 로고 섹션
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "BATTERYAI",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            // 2. 히어로 섹션 (애니메이션 배터리)
            Box(
                modifier = Modifier.graphicsLayer(
                    scaleX = batteryScale,
                    scaleY = batteryScale,
                    alpha = batteryAlpha
                )
            ) {
                BatteryVisual(fillProgress = fillProgress)
            }

            Spacer(Modifier.height(32.dp))

            // 3. 헤드라인 섹션
            Text(
                text = "내 보조배터리의\n진짜 상태를 확인하세요",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "AI 분석을 통해 건강도와 충전 효율,\n미래 상태까지 정확하게 예측해 드립니다.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(36.dp))

            // 4. 입력 섹션 (카드 형태)
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddToPhotos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("기기 정보 등록", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(20.dp))

                    BatteryFormField(
                        label = "제조사",
                        value = brand,
                        onValueChange = { brand = it },
                        placeholder = "Anker, 삼성, 샤오미 등"
                    )
                    Spacer(Modifier.height(16.dp))

                    BatteryFormField(
                        label = "모델명 *",
                        value = nickname,
                        onValueChange = { nickname = it },
                        placeholder = "예: PowerCore 10000"
                    )
                    Spacer(Modifier.height(16.dp))

                    BatteryFormField(
                        label = "정격 용량 (mAh) *",
                        value = capacity,
                        onValueChange = { capacity = it },
                        placeholder = "예: 10000",
                        keyboardType = KeyboardType.Number,
                        suffix = "mAh"
                    )
                    Spacer(Modifier.height(16.dp))

                    BatteryFormField(
                        label = "제조년월",
                        value = manufactureDate,
                        onValueChange = { manufactureDate = it },
                        placeholder = "예: 2024-05"
                    )

                    Spacer(Modifier.height(28.dp))

                    Button(
                        onClick = {
                            if (isFormValid) {
                                // capacity를 숫자로 변환합니다. 변환 실패 시 0으로 처리합니다.
                                val capacityInt = capacity.toIntOrNull() ?: 0

                                onStartClick(
                                    DeviceInfo(
                                        nickname = nickname,
                                        capacity = capacityInt, // 여기서 숫자로 넘겨줍니다!
                                        manufactureDate = manufactureDate,
                                        brand = brand
                                    )
                                )
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("배터리 진단 시작하기", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // 5. 기능 요약 섹션 (FeatureSection)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("🔋" to "실시간 SOC", "🛡️" to "SOH 분석", "🤖" to "AI 예측").forEach { (emoji, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier.size(48.dp),
                            shadowElevation = 1.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryVisual(fillProgress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 54.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.5.dp, primary.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .padding(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillProgress)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(listOf(primary.copy(alpha = 0.7f), primary))
                    )
            )
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }
        Spacer(Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 20.dp)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(primary.copy(alpha = 0.2f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 14.sp, color = Color.LightGray) },
            trailingIcon = if (suffix != null) ({
                Text(suffix, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(end = 12.dp))
            }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFF0F0F0),
                focusedContainerColor = Color(0xFFFAFBFC),
                unfocusedContainerColor = Color(0xFFFAFBFC),
            )
        )
    }
}