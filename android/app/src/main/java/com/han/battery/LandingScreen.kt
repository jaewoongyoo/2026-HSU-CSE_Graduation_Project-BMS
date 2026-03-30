package com.han.battery

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(onStart: (DeviceInfo) -> Unit) {
    var brand by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var manufactureDate by remember { mutableStateOf("") }

    val isFormValid = nickname.isNotBlank() && capacity.isNotBlank()

    // 배터리 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val batteryScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )
    val batteryAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "alpha"
    )
    val fillProgress by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "fill"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 배경 글로우
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── 로고 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "BatteryAI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── 배터리 애니메이션 ──
            Box(
                modifier = Modifier.graphicsLayer(
                    scaleX = batteryScale,
                    scaleY = batteryScale,
                    alpha = batteryAlpha
                )
            ) {
                BatteryVisual(fillProgress = fillProgress)
            }

            Spacer(Modifier.height(28.dp))

            // ── 헤드라인 ──
            Text(
                text = "내 보조배터리의\n진짜 상태를 확인하세요",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 35.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "AI가 분석한 배터리 건강도, 충전 예측,\n케이블 진단까지 한눈에 확인하세요.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(28.dp))

            // ── 폼 카드 ──
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Battery5Bar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "기기 정보 입력",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    BatteryFormField(
                        label = "제조사",
                        value = brand,
                        onValueChange = { brand = it },
                        placeholder = "예: Anker, 삼성, 샤오미"
                    )
                    Spacer(Modifier.height(12.dp))

                    BatteryFormField(
                        label = "모델명 *",
                        value = nickname,
                        onValueChange = { nickname = it },
                        placeholder = "예: PowerCore 10000, MagSafe Battery Pack"
                    )
                    Spacer(Modifier.height(12.dp))

                    BatteryFormField(
                        label = "정격 용량 (mAh) *",
                        value = capacity,
                        onValueChange = { capacity = it },
                        placeholder = "예: 10000",
                        keyboardType = KeyboardType.Number,
                        suffix = "mAh"
                    )
                    Spacer(Modifier.height(12.dp))

                    BatteryFormField(
                        label = "제조년월",
                        value = manufactureDate,
                        onValueChange = { manufactureDate = it },
                        placeholder = "예: 2023-06"
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (isFormValid) {
                                onStart(DeviceInfo(nickname, capacity, manufactureDate, brand))
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("배터리 진단 시작하기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── 기능 뱃지 ──
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("🔋" to "실시간 SOC", "🛡️" to "건강도 분석", "🤖" to "AI 예측").forEach { (emoji, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emoji, fontSize = 22.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ── 배터리 시각화 ──
@Composable
fun BatteryVisual(fillProgress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillProgress)
                    .background(
                        Brush.horizontalGradient(
                            listOf(primary.copy(alpha = 0.6f), primary)
                        )
                    )
            )
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 16.dp)
                .background(
                    primary.copy(alpha = 0.45f),
                    RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                )
        )
    }
}

// ── 공통 폼 필드 ──
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
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            },
            trailingIcon = if (suffix != null) ({
                Text(
                    suffix,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
    }
}