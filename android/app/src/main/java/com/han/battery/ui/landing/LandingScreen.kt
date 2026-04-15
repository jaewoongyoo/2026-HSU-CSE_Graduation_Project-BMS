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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.han.battery.ui.theme.Slate950
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    onStartClick: (DeviceInfo) -> Unit,
    onBackClick: () -> Unit
) {
    // ── 상태 관리 ──
    var currentStep by remember { mutableIntStateOf(0) } // 0: 초기, 1: 모델명, 2: 용량, 3: 제조년월
    var nickname by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var manufactureDate by remember { mutableStateOf("") }

    // ── 입력 필드 포커스 ──
    val nicknameFocusRequester = remember { FocusRequester() }
    val capacityFocusRequester = remember { FocusRequester() }

    // ── 유효성 검사 ──
    var nicknameError by remember { mutableStateOf("") }
    var capacityError by remember { mutableStateOf("") }

    val isDarkTheme = isSystemInDarkTheme()

    // ── 애니메이션 로직 ──
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val batteryScale by infiniteTransition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )
    val fillProgress by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "fill"
    )

    // 첫 렌더링 시 첫 번째 필드에 포커스
    LaunchedEffect(currentStep) {
        when (currentStep) {
            1 -> nicknameFocusRequester.requestFocus()
            2 -> capacityFocusRequester.requestFocus()
        }
    }

    if (currentStep == 0) {
        // ── 초기 화면 ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (isDarkTheme) {
                            listOf(Slate950, Color(0xFF1A1F35), Slate950)
                        } else {
                            listOf(Slate50, Color(0xFFF6F8FC), Slate50)
                        }
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 배경 글로우
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
                        scaleY = batteryScale
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

                // 4. CTA 버튼
                Button(
                    onClick = { currentStep = 1 },
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

                Spacer(Modifier.height(32.dp))

                // 5. 기능 요약 섹션
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

            // 뒤로가기 버튼
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        StepInputScreen(
            currentStep = currentStep,
            nickname = nickname,
            onNicknameChange = {
                nickname = it
                nicknameError = when {
                    it.isBlank() -> "모델명을 입력하세요"
                    it.length > 100 -> "모델명은 100자 이하여야 합니다"
                    else -> ""
                }
            },
            capacity = capacity,
            onCapacityChange = {
                capacity = it
                capacityError = when {
                    it.isNotEmpty() && it.toIntOrNull() == null -> "숫자만 입력 가능합니다"
                    it.toIntOrNull() !in 1..100000 -> "100 ~ 100,000 mAh 범위의 용량을 입력해주세요"
                    else -> ""
                }
            },
            manufactureDate = manufactureDate,
            onManufactureDateChange = { date ->
                manufactureDate = date
            },
            nicknameError = nicknameError,
            capacityError = capacityError,
            nicknameFocusRequester = nicknameFocusRequester,
            capacityFocusRequester = capacityFocusRequester,
            onNextClick = {
                currentStep++
            },
            onPrevClick = {
                currentStep--
            },
            onCompleteClick = {
                val capacityInt = capacity.toIntOrNull() ?: 0
                if (capacityInt > 0 && capacityInt <= 100000 && nickname.isNotBlank() && manufactureDate.isNotBlank()) {
                    onStartClick(
                        DeviceInfo(
                            model_name = nickname.trim(),
                            powerbank_capacity_mah = capacityInt,
                            manufacture_date = manufactureDate.trim()
                        )
                    )
                }
            }
        )
    }
}

@Composable
fun StepInputScreen(
    currentStep: Int,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    capacity: String,
    onCapacityChange: (String) -> Unit,
    manufactureDate: String,
    onManufactureDateChange: (String) -> Unit,
    nicknameError: String,
    capacityError: String,
    nicknameFocusRequester: FocusRequester,
    capacityFocusRequester: FocusRequester,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onCompleteClick: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    var showDatePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (isDarkTheme) {
                        listOf(Slate950, Color(0xFF1A1F35), Slate950)
                    } else {
                        listOf(Slate50, Color(0xFFF6F8FC), Slate50)
                    }
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 진행도 표시 ──
            StepProgressBar(currentStep)

            Spacer(Modifier.height(40.dp))

            // ── 각 단계별 입력 ──
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    when (currentStep) {
                        1 -> {
                            // ── Step 1: 모델명 ──
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))

                            Text(
                                "배터리 모델명",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "정확한 모델명을 입력해주세요",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Spacer(Modifier.height(24.dp))

                            BatteryFormField(
                                label = "모델명 *",
                                value = nickname,
                                onValueChange = onNicknameChange,
                                placeholder = "예: PowerCore 10000",
                                focusRequester = nicknameFocusRequester,
                                maxLength = 100,
                                isError = nicknameError.isNotEmpty(),
                                helperText = "배터리 모델명을 정확하게 입력하세요 (${nickname.length}/100)",
                                errorText = nicknameError
                            )
                        }

                        2 -> {
                            // ── Step 2: 용량 ──
                            Icon(
                                Icons.Default.Battery6Bar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))

                            Text(
                                "배터리 용량",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "배터리 용량을 mAh로 입력해주세요",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Spacer(Modifier.height(24.dp))

                            BatteryFormField(
                                label = "정격 용량 (mAh) *",
                                value = capacity,
                                onValueChange = onCapacityChange,
                                placeholder = "예: 10000",
                                focusRequester = capacityFocusRequester,
                                keyboardType = KeyboardType.Number,
                                suffix = "mAh",
                                isError = capacityError.isNotEmpty(),
                                helperText = "100 ~ 100,000 mAh 범위의 용량을 입력하세요",
                                errorText = capacityError
                            )
                        }

                        3 -> {
                            // ── Step 3: 제조년월 ──
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))

                            Text(
                                "제조년월",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "배터리의 제조연월을 선택해주세요",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Spacer(Modifier.height(24.dp))

                            // 캘린더 선택 버튼
                            Button(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (manufactureDate.isEmpty()) "캘린더에서 선택" else manufactureDate,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (manufactureDate.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "선택된 날짜: $manufactureDate",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // ── 버튼 영역 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (currentStep > 1) {
                            OutlinedButton(
                                onClick = onPrevClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("이전", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        val isCurrentStepValid = when (currentStep) {
                            1 -> nickname.isNotBlank() && nicknameError.isEmpty()
                            2 -> capacity.isNotBlank() && capacityError.isEmpty()
                            3 -> manufactureDate.isNotBlank()
                            else -> false
                        }

                        Button(
                            onClick = {
                                if (currentStep < 3) {
                                    onNextClick()
                                } else {
                                    onCompleteClick()
                                }
                            },
                            enabled = isCurrentStepValid,
                            modifier = Modifier
                                .weight(if (currentStep > 1) 1f else 1.2f)
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                if (currentStep == 3) "등록" else "다음",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // 캘린더 다이얼로그
    if (showDatePicker && currentStep == 3) {
        DatePickerDialog(
            selectedDate = manufactureDate,
            onDateSelected = { date ->
                onManufactureDateChange(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun StepProgressBar(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val stepNum = index + 1

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        color = if (stepNum <= currentStep) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Step $currentStep / 3",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYearMonth by remember { mutableStateOf(
        if (selectedDate.isEmpty()) YearMonth.now()
        else try {
            YearMonth.parse(selectedDate)
        } catch (_: Exception) {
            YearMonth.now()
        }
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "제조년월 선택",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 연도 선택
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            selectedYearMonth = selectedYearMonth.minusYears(1)
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "이전 연도")
                    }

                    Text(
                        selectedYearMonth.year.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = {
                            selectedYearMonth = selectedYearMonth.plusYears(1)
                        }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "다음 연도")
                    }
                }

                // 월 선택 그리드
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(3) { col ->
                                val month = row * 3 + col + 1
                                if (month <= 12) {
                                    Button(
                                        onClick = {
                                            selectedYearMonth = selectedYearMonth.withMonth(month)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedYearMonth.monthValue == month) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color(0xFFE0E0E0)
                                            }
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "${month.toString().padStart(2, '0')}월",
                                            color = if (selectedYearMonth.monthValue == month) {
                                                Color.White
                                            } else {
                                                Color.Black
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 선택된 날짜 표시
                Text(
                    "선택: ${selectedYearMonth.year}-${selectedYearMonth.monthValue.toString().padStart(2, '0')}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
                    val dateString = selectedYearMonth.format(formatter)
                    onDateSelected(dateString)
                }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
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
    suffix: String? = null,
    focusRequester: FocusRequester? = null,
    maxLength: Int? = null,
    isError: Boolean = false,
    helperText: String? = null,
    errorText: String? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isError -> Color.Red
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                },
                modifier = Modifier.padding(start = 4.dp)
            )

            // 입력 길이 표시 (maxLength가 있을 때)
            if (maxLength != null) {
                Text(
                    text = "${value.length}/$maxLength",
                    fontSize = 10.sp,
                    color = when {
                        isError -> Color.Red
                        value.length >= maxLength * 0.8 -> Color(0xFFFF9800)
                        else -> Color.Gray.copy(alpha = 0.5f)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // maxLength 체크
                if (maxLength == null || newValue.length <= maxLength) {
                    onValueChange(newValue)
                }
            },
            placeholder = { Text(placeholder, fontSize = 14.sp, color = Color.LightGray) },
            trailingIcon = if (suffix != null) ({
                Text(suffix, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(end = 12.dp))
            }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = RoundedCornerShape(14.dp),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = when {
                    isError -> Color.Red
                    else -> MaterialTheme.colorScheme.primary
                },
                unfocusedBorderColor = when {
                    isError -> Color.Red.copy(alpha = 0.3f)
                    else -> Color(0xFFF0F0F0)
                },
                focusedContainerColor = when {
                    isError -> Color.Red.copy(alpha = 0.05f)
                    else -> Color(0xFFFAFBFC)
                },
                unfocusedContainerColor = when {
                    isError -> Color.Red.copy(alpha = 0.02f)
                    else -> Color(0xFFFAFBFC)
                },
                errorBorderColor = Color.Red,
                errorContainerColor = Color.Red.copy(alpha = 0.05f)
            )
        )

        // 헬퍼 텍스트 또는 에러 메시지
        Spacer(Modifier.height(6.dp))
        if (errorText != null && errorText.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "에러",
                    tint = Color.Red,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = errorText,
                    fontSize = 11.sp,
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (helperText != null && helperText.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "정보",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = helperText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        }
    }
}