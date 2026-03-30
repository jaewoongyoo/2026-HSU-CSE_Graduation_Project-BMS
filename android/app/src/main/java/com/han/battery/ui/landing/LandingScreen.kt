package com.han.battery.ui.landing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LandingScreen(onStart: (String, String) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("배터리 정보 입력", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("모델명") })
        OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("용량(mAh)") })
        Button(onClick = { onStart(nickname, capacity) }, modifier = Modifier.fillMaxWidth()) {
            Text("진단 시작")
        }
    }
}