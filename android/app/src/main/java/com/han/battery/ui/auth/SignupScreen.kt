package com.han.battery.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.han.battery.ui.components.common.AppLogo
import com.han.battery.ui.components.common.LogoSize
import com.han.battery.ui.theme.Slate50
import com.han.battery.data.storage.UserManager
import androidx.compose.foundation.rememberScrollState


@Composable
fun SignupScreen(
    userManager: UserManager,
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var uiState by remember { mutableStateOf<AuthUiState>(AuthUiState.Idle) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Slate50, Color(0xFFF6F8FC), Slate50)
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
                enabled = uiState !is AuthUiState.Loading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = uiState !is AuthUiState.Loading
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("비밀번호 확인") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    // 검증
                    if (username.isBlank() || password.isBlank()) {
                        uiState = AuthUiState.Error("모든 필드를 입력하세요")
                        return@Button
                    }

                    if (password != confirmPassword) {
                        uiState = AuthUiState.Error("비밀번호가 일치하지 않습니다")
                        return@Button
                    }

                    // 중복 확인
                    if (userManager.userExists(username)) {
                        uiState = AuthUiState.Error("이미 존재하는 사용자명입니다")
                        return@Button
                    }

                    // 회원가입 처리
                    val success = userManager.registerUser(username, password)
                    if (success) {
                        uiState = AuthUiState.Success("회원가입 성공")
                        onSignupSuccess()
                    } else {
                        uiState = AuthUiState.Error("회원가입 실패")
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
