package com.han.battery.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class SignupRequest(
    val username: String,
    val password: String,
    val phone_model: String? = null,
    val phone_uid: String? = null
)

@Serializable
data class AuthResponse(
    val id: Int,
    val username: String,
    val phone_model: String? = null,   // ✅ nullable로 변경
    val phone_uid: String? = null,     // ✅ nullable로 변경
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

// 배터리 관련 데이터 모델 (ERD 기준: devices 테이블)
@Serializable
data class BatteryRegistrationRequest(
    val user_id: String,           // ✅ API 명세: user_id는 String
    val model_name: String,
    val powerbank_capacity_mah: Int,
    val manufacture_date: String? = null
)

@Serializable
data class BatteryResponse(
    val id: Int,
    val user_id: Int,
    val model_name: String,
    val powerbank_capacity_mah: Int? = null,   // ✅ nullable로 변경
    val manufacture_date: String? = null,       // ✅ nullable로 변경
    val created_at: String? = null              // ✅ nullable로 변경
)

@Serializable
data class BatteryUpdateRequest(
    val model_name: String? = null,
    val powerbank_capacity_mah: Int? = null,
    val manufacture_date: String? = null
)

