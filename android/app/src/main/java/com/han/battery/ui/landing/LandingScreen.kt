package com.han.battery.ui.landing

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.DeviceInfo
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Emerald500
import com.han.battery.ui.theme.Slate200
import com.han.battery.ui.theme.Slate300
import com.han.battery.ui.theme.Slate500
import com.han.battery.ui.theme.Slate50
import androidx.compose.animation.core.EaseInOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun LandingScreen(
    onStart: (DeviceInfo) -> Unit
) {
    var brand by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var manufactureDate by remember { mutableStateOf("") }

    val isFormValid = nickname.isNotBlank() && capacity.isNotBlank()

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
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        BackgroundGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            LogoSection()

            Spacer(modifier = Modifier.height(28.dp))

            BatteryHeroVisual()

            Spacer(modifier = Modifier.height(30.dp))

            LandingHeadline()

            Spacer(modifier = Modifier.height(26.dp))

            DeviceInputCard(
                brand = brand,
                onBrandChange = { brand = it },
                nickname = nickname,
                onNicknameChange = { nickname = it },
                capacity = capacity,
                onCapacityChange = { capacity = it },
                manufactureDate = manufactureDate,
                onManufactureDateChange = { manufactureDate = it },
                isFormValid = isFormValid,
                onStartClick = {
                    onStart(
                        DeviceInfo(
                            brand = brand,
                            nickname = nickname,
                            capacity = capacity,
                            manufactureDate = manufactureDate
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            BottomFeatureRow()

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BackgroundGlow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .offset(y = (-30).dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Blue600.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun LogoSection() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Blue600.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Blue600,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "BATTERYAI",
            color = Blue600,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.9.sp
        )
    }
}

@Composable
private fun BatteryHeroVisual() {
    val transition = rememberInfiniteTransition(label = "battery_hero")
    val floatOffset by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.60f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .offset(y = floatOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .alpha(glowAlpha * 0.35f)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                Blue600.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            BatteryIllustration()
        }
    }
}

@Composable
private fun BatteryIllustration() {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(120.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Color(0xFFD9E3FF))
        )

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(66.dp)
                .height(102.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(
                    BorderStroke(1.dp, Blue600.copy(alpha = 0.15f)),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF7BB0FF),
                                Blue600
                            )
                        )
                    )
            )

            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 12.dp, y = 20.dp)
                    .size(4.dp)
                    .background(Color(0xFFFFD54F).copy(alpha = 0.85f), CircleShape)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-14).dp, y = 10.dp)
                    .size(5.dp)
                    .background(Color(0xFFFFD54F).copy(alpha = 0.75f), CircleShape)
            )
        }
    }
}

@Composable
private fun LandingHeadline() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "내 보조배터리의",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "진짜 상태",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Blue600
            )
            Text(
                text = "를 확인하세요",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "AI가 분석한 배터리 건강도, 충전 예측,\n케이블 진단까지 한눈에 확인하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun DeviceInputCard(
    brand: String,
    onBrandChange: (String) -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    capacity: String,
    onCapacityChange: (String) -> Unit,
    manufactureDate: String,
    onManufactureDateChange: (String) -> Unit,
    isFormValid: Boolean,
    onStartClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            Blue600.copy(alpha = 0.10f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = Blue600,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "기기 정보 입력",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LandingField(
                label = "제조사",
                value = brand,
                onValueChange = onBrandChange,
                placeholder = "예: Anker, 삼성, 샤오미"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LandingField(
                label = "모델명",
                value = nickname,
                onValueChange = onNicknameChange,
                placeholder = "예: PowerCore 10000, MagSafe Battery Pack"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LandingField(
                label = "정격 용량 (MAH)",
                value = capacity,
                onValueChange = onCapacityChange,
                placeholder = "예: 10000",
                keyboardType = KeyboardType.Number,
                trailingText = "mAh"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LandingField(
                label = "제조년월",
                value = manufactureDate,
                onValueChange = onManufactureDateChange,
                placeholder = "----년 --월",
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onStartClick,
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue600,
                    disabledContainerColor = Blue600.copy(alpha = 0.35f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "배터리 진단 시작하기",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LandingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Slate500.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = {
                when {
                    trailingText != null -> {
                        Text(
                            text = trailingText,
                            color = Slate500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    trailingIcon != null -> trailingIcon()
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue600.copy(alpha = 0.45f),
                unfocusedBorderColor = Slate300.copy(alpha = 0.8f),
                focusedContainerColor = Color(0xFFF8FAFD),
                unfocusedContainerColor = Color(0xFFF8FAFD),
                cursorColor = Blue600
            )
        )
    }
}

@Composable
private fun BottomFeatureRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LandingFeatureItem(
            icon = Icons.Default.BatteryChargingFull,
            color = Emerald500,
            label = "실시간 SOC"
        )
        LandingFeatureItem(
            icon = Icons.Default.Shield,
            color = Blue600,
            label = "건강도 분석"
        )
        LandingFeatureItem(
            icon = Icons.Default.TipsAndUpdates,
            color = Color(0xFF8B5CF6),
            label = "AI 예측"
        )
    }
}

@Composable
private fun LandingFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Slate500,
            fontWeight = FontWeight.Medium
        )
    }
}