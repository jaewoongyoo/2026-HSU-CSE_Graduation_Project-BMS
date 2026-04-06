package com.han.battery.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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


@Composable
fun LoginScreen(
    userManager: UserManager,
    onNavigateToSignup: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(size = LogoSize.Large, showText = true)

            Text(
                text = "로그인",
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

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        uiState = AuthUiState.Error("모든 필드를 입력하세요")
                        return@Button
                    }

                    // 사용자 검증
                    if (userManager.validateUser(username, password)) {
                        uiState = AuthUiState.Success("로그인 성공")
                        onLoginSuccess()
                    } else {
                        uiState = AuthUiState.Error("사용자명 또는 비밀번호가 잘못되었습니다")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = username.isNotBlank() && password.isNotBlank() && uiState !is AuthUiState.Loading
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("로그인 중...")
                } else {
                    Text("로그인")
                }
            }

            TextButton(
                onClick = onNavigateToSignup,
                enabled = uiState !is AuthUiState.Loading
            ) {
                Text("회원가입")
            }
        }
    }
}
