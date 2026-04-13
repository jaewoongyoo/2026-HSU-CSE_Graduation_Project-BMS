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

        // API 엔드포인트 검증
        if (!baseUrl.startsWith("http")) {
            throw IllegalArgumentException("Invalid baseUrl: $baseUrl")
        }

        // Capacity validation
        if (request.capacity_mah == null || request.capacity_mah <= 0) {
            throw IllegalArgumentException("용량이 설정되지 않았습니다. 1~200000 mAh 범위의 값을 입력하세요.")
        }

        val response = client.post("$baseUrl/api/v1/batteries") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "배터리 등록 실패: ${response.status.value} - $errorBody")

            // 상세한 에러 메시지 생성
            val errorMessage = when (response.status.value) {
                400 -> "요청 형식이 잘못되었습니다. 모든 필수 필드를 확인하세요."
                401 -> "인증이 필요합니다. 다시 로그인하세요."
                403 -> "권한이 없습니다. 관리자에게 문의하세요."
                404 -> "서버의 배터리 등록 엔드포인트를 찾을 수 없습니다. 서버 URL을 확인하세요: $baseUrl"
                409 -> "이미 등록된 배터리입니다."
                422 -> "입력 데이터가 올바르지 않습니다. 모델명(1-100자), 용량(1-200000 mAh)을 확인하세요."
                500, 502, 503 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
                else -> "배터리 등록 실패 (${response.status.value}): $errorBody"
            }
            throw Exception(errorMessage)
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

