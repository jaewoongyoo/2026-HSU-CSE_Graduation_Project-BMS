package com.han.battery.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.han.battery.ui.components.common.AppLogo
import com.han.battery.ui.components.common.LogoSize
import com.han.battery.ui.theme.Slate50
import com.han.battery.ui.theme.Slate950
import com.han.battery.data.repository.AuthRepository
import kotlinx.coroutines.launch


@Composable
fun SignupScreen(
    authRepository: AuthRepository,
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var uiState by remember { mutableStateOf<AuthUiState>(AuthUiState.Idle) }
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(size = LogoSize.Large, showText = true)

            Text(
                text = "회원가입",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 32.dp, top = 16.dp)
            )


            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("사용자명") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                enabled = uiState !is AuthUiState.Loading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                enabled = uiState !is AuthUiState.Loading
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("비밀번호 확인") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                enabled = uiState !is AuthUiState.Loading
            )

            if (uiState is AuthUiState.Error) {
                Text(
                    text = (uiState as AuthUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (password != confirmPassword && confirmPassword.isNotEmpty()) {
                Text(
                    text = "비밀번호가 일치하지 않습니다",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    // 입력 검증
                    if (username.isBlank()) {
                        uiState = AuthUiState.Error("사용자명을 입력하세요")
                        return@Button
                    }
                    if (username.length < 1) {
                        uiState = AuthUiState.Error("사용자명은 1자 이상이어야 합니다")
                        return@Button
                    }
                    if (password.isBlank()) {
                        uiState = AuthUiState.Error("비밀번호를 입력하세요")
                        return@Button
                    }
                    if (password.length < 4) {
                        uiState = AuthUiState.Error("비밀번호는 4자 이상이어야 합니다")
                        return@Button
                    }

                    if (password != confirmPassword) {
                        uiState = AuthUiState.Error("비밀번호가 일치하지 않습니다")
                        return@Button
                    }

                    uiState = AuthUiState.Loading
                    coroutineScope.launch {
                        val result = authRepository.signup(username, password)
                        uiState = if (result.isSuccess) {
                            AuthUiState.Success("회원가입 성공")
                            onSignupSuccess()
                            AuthUiState.Idle
                        } else {
                            val errorMessage = result.exceptionOrNull()?.message ?: "회원가입 실패"
                            AuthUiState.Error(errorMessage)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = username.isNotBlank() && password.isNotBlank() && password == confirmPassword && uiState !is AuthUiState.Loading
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("회원가입 중...")
                } else {
                    Text("회원가입")
                }
            }

            TextButton(
                onClick = onNavigateToLogin,
                enabled = uiState !is AuthUiState.Loading
            ) {
                Text("로그인으로 돌아가기")
            }
        }
    }
}
