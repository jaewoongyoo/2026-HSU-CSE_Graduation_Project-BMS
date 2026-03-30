package com.han.battery.ui.landing.sections
// 랜딩 스크린의 배터리 정보 입력 섹션 - 기기 제조사, 모델명, 제조년월일 입력 폼

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.DeviceInfo
import com.han.battery.convertFormStateToBatteryDevice
import com.han.battery.data.model.BatteryDevice
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate300
import com.han.battery.ui.theme.Slate500

data class FormState(
    val brand: String = "",
    val nickname: String = "",
    val capacity: String = "",
    val manufactureDate: String = ""
)

/**
 * 폼 입력 검증 함수
 * @return 검증 에러 메시지, 또는 null (성공)
 */
fun validateFormState(formState: FormState): String? {
    return when {
        formState.nickname.isBlank() -> "모델명을 입력해주세요"
        formState.capacity.isBlank() -> "용량을 입력해주세요"
        formState.capacity.toIntOrNull() == null -> "용량은 숫자로 입력해주세요"
        formState.capacity.toInt() <= 0 -> "용량은 0보다 커야 합니다"
        formState.capacity.toInt() > 100000 -> "용량이 너무 많습니다 (최대 100000mAh)"
        else -> null  // 검증 성공
    }
}

@Composable
fun InputSection(
    formState: FormState,
    onFormChange: (FormState) -> Unit,
    onStart: (DeviceInfo) -> Unit
) {
    val isFormValid = formState.nickname.isNotBlank() && formState.capacity.isNotBlank()
    val focusManager = LocalFocusManager.current


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
                        contentDescription = "배터리 충전 아이콘",
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
                value = formState.brand,
                onValueChange = { onFormChange(formState.copy(brand = it)) },
                placeholder = "예: Anker, 삼성, 샤오미",
                keyboardType = KeyboardType.Text,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LandingField(
                label = "모델명",
                value = formState.nickname,
                onValueChange = { onFormChange(formState.copy(nickname = it)) },
                placeholder = "예: PowerBank Pro, 나의 맥세이프",
                keyboardType = KeyboardType.Text,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LandingField(
                label = "정격 용량 (MAH)",
                value = formState.capacity,
                onValueChange = { input ->
                    // 숫자만 입력 가능하게 필터링
                    val filtered = input.filter { it.isDigit() }
                    onFormChange(formState.copy(capacity = filtered))
                },
                placeholder = "예: 10000",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                trailingText = "mAh",
                onNext = { focusManager.clearFocus() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            DatePickerField(
                label = "제조년월",
                value = formState.manufactureDate,
                onValueChange = { onFormChange(formState.copy(manufactureDate = it)) },
                placeholder = "YYYY-MM 형식"
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    // 폼 검증
                    val validationError = validateFormState(formState)
                    if (validationError != null) {
                        // TODO: 에러 메시지 표시 (SnackBar 등)
                        return@Button
                    }
                    
                    // FormState를 BatteryDevice로 변환하여 전달
                    val batteryDevice = convertFormStateToBatteryDevice(
                        brand = formState.brand,
                        nickname = formState.nickname,
                        capacity = formState.capacity,
                        manufactureDate = formState.manufactureDate
                    )

                    // DeviceInfo는 BatteryDevice의 별칭이므로 직접 전달
                    onStart(batteryDevice)
                },
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
                    contentDescription = "마법봉 아이콘",
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
                    contentDescription = "다음으로 이동",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    var showDatePicker by remember { mutableStateOf(false) }

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
            onValueChange = { input ->
                // YYYY-MM 형식만 허용 (숫자와 하이픈만)
                if (input.isEmpty() || input.all { it.isDigit() || it == '-' }) {
                    onValueChange(input)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            singleLine = false,
            maxLines = 1,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Slate500.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                androidx.compose.material3.IconButton(
                    onClick = { showDatePicker = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "날짜 선택",
                        tint = Blue600
                    )
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

    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { year, month ->
                val dateStr = String.format("%04d-%02d", year, month + 1)
                onValueChange(dateStr)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun DatePickerDialog(
    onDateSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = java.util.Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(calendar.get(java.util.Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(calendar.get(java.util.Calendar.MONTH)) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("제조년월 선택")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%04d년 %02d월", selectedYear, selectedMonth + 1),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Blue600
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 연도 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Button(
                        onClick = { selectedYear-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-")
                    }

                    Text(
                        text = selectedYear.toString(),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    androidx.compose.material3.Button(
                        onClick = { selectedYear++ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 월 선택
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Button(
                        onClick = { if (selectedMonth > 0) selectedMonth-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-")
                    }

                    Text(
                        text = String.format("%02d", selectedMonth + 1),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    androidx.compose.material3.Button(
                        onClick = { if (selectedMonth < 11) selectedMonth++ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+")
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onDateSelected(selectedYear, selectedMonth) }
            ) {
                Text("선택")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss
            ) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun LandingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    trailingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onNext: (() -> Unit)? = null
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
                .height(56.dp),
            singleLine = false,
            maxLines = 1,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Slate500.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = if (onNext != null) {
                KeyboardActions(onNext = { onNext() })
            } else {
                KeyboardActions.Default
            },
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

