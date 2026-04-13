package com.han.battery.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class SignupRequest(
    val name: String? = null,
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val id: Int? = null,
    val name: String? = null,
    val username: String? = null,
    val message: String? = null
)

@Serializable
data class UserResponse(
    val id: Int,
    val name: String,
    val username: String
)

@Serializable
data class UsersListResponse(
    val users: List<UserResponse>
)

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val password: String? = null
)

@Serializable
data class ErrorResponse(
    val detail: List<ErrorDetail>? = null
)

@Serializable
data class ErrorDetail(
    val loc: List<JsonElement>? = null,
    val msg: String,
    val type: String
)

// 배터리 관련 데이터 모델
@Serializable
data class BatteryRegistrationRequest(
    val nickname: String,
    val capacity: Int,
    val brand: String? = null,
    val manufacture_date: String? = null
)

@Serializable
data class BatteryResponse(
    val id: Int,
    val user_id: Int,
    val nickname: String,
    val capacity: Int,
    val brand: String? = null,
    val manufacture_date: String? = null,
    val created_at: String? = null
)

