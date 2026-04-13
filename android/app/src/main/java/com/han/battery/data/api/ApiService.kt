package com.han.battery.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.han.battery.data.model.*
import io.ktor.client.engine.android.*
import com.han.battery.DevConfig

class ApiService(private val baseUrl: String = DevConfig.API_BASE_URL) {
    private val client = HttpClient(Android) {
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
        client.post("$baseUrl/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse> = runCatching {
        client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
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

    fun close() {
        client.close()
    }
}

