package com.han.battery.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.han.battery.data.model.*
import io.ktor.client.engine.android.*
import com.han.battery.DevConfig
import android.util.Log

class ApiService(private val baseUrl: String = DevConfig.API_BASE_URL) {
    private val client = HttpClient(Android) {
        // 쿠키 자동 관리 (세션 기반 인증용)
        install(HttpCookies)

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO  // ALL → INFO로 변경 (프로덕션: NONE)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000  // 30초 → 10초로 단축
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
    }

    suspend fun signup(request: SignupRequest): Result<AuthResponse> = runCatching {
        val response = client.post("$baseUrl/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw Exception("${response.status.value}: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse> = runCatching {
        val response = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw Exception("${response.status.value}: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun getUsers(skip: Int = 0, limit: Int = 10): Result<List<UserResponse>> = runCatching {
        client.get("$baseUrl/api/v1/users") {
            parameter("skip", skip)
            parameter("limit", limit)
        }.body()
    }

    suspend fun getUser(userId: Int): Result<UserResponse> = runCatching {
        client.get("$baseUrl/api/v1/users/$userId") {}.body()
    }

    suspend fun updateUser(userId: Int, request: UpdateUserRequest): Result<UserResponse> = runCatching {
        client.patch("$baseUrl/api/v1/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteUser(userId: Int): Result<String> = runCatching {
        client.delete("$baseUrl/api/v1/users/$userId") {}.body()
    }

    // 배터리 관련 API
    suspend fun registerBattery(request: BatteryRegistrationRequest): Result<BatteryResponse> = runCatching {
        Log.d("ApiService", "배터리 등록 API 호출: POST $baseUrl/api/v1/batteries")
        Log.d("ApiService", "요청 데이터: model_name=${request.model_name}, capacity_mah=${request.capacity_mah}")

        val response = client.post("$baseUrl/api/v1/batteries") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "배터리 등록 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }

        val result = response.body<BatteryResponse>()
        Log.d("ApiService", "배터리 등록 성공: ID=${result.id}, model_name=${result.model_name}")
        result
    }

    suspend fun getBatteries(): Result<List<BatteryResponse>> = runCatching {
        client.get("$baseUrl/api/v1/batteries") {}.body()
    }

    suspend fun getBattery(batteryId: Int): Result<BatteryResponse> = runCatching {
        client.get("$baseUrl/api/v1/batteries/$batteryId") {}.body()
    }

    suspend fun deleteBattery(batteryId: Int): Result<String> = runCatching {
        client.delete("$baseUrl/api/v1/batteries/$batteryId") {}.body()
    }

    fun close() {
        client.close()
    }
}

