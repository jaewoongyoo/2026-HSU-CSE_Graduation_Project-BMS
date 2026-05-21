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
    val manufacturer: String? = null,
    val model_name: String,
    val powerbank_capacity_mah: Int,
    val manufacture_date: String? = null
)

@Serializable
data class BatteryResponse(
    val id: Int,
    val user_id: Int,
    val manufacturer: String? = null,
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

@Serializable
data class SessionStartRequest(
    val device_id: Int
)

@Serializable
data class SessionStartResponse(
    val id: Int,
    val status: String
)

@Serializable
data class SessionFinishRequest(
    val android_api_level: Int,
    val powerbank_capacity_start_mah: Double? = null,
    val session_start_ts: String? = null,
    val session_end_ts: String,
    val capacity_ah: Double,
    val powerbank_capacity_end_mah: Double? = null,
    val label_capacity_ah: Double? = null
)

@Serializable
data class SessionFinishResponse(
    val id: Int,
    val status: String
)

@Serializable
data class SohPredictResponse(
    val soh_percentage: Double,
    val condition: String,
    val estimated_full_charges: Double,
    val powerbank_usable_mah: Double,
    val smartphone_received_mah: Double? = null,
    val mean_temperature_c: Double? = null,
    @SerialName("standard_soh_percentage") val standardSohPercentage: Double? = null,
    @SerialName("degradation_rate_ratio") val degradationRateRatio: Double? = null,
    @SerialName("sessions_used") val sessionsUsed: Int? = null,
    @SerialName("sessions_total") val sessionsTotal: Int? = null,
    @SerialName("confidence") val confidence: Double? = null
)

@Serializable
data class SessionResultResponse(
    val id: Int,
    val status: String,
    val soh_percentage: Double? = null,
    val condition: String? = null,
    val estimated_full_charges: Double? = null,
    val powerbank_usable_mah: Double? = null,
    val smartphone_received_mah: Double? = null,
    val mean_temperature_c: Double? = null,
    val analyzed_at: String? = null,
    @SerialName("standard_soh_percentage") val standardSohPercentage: Double? = null,
    @SerialName("degradation_rate_ratio") val degradationRateRatio: Double? = null,
    @SerialName("sessions_used") val sessionsUsed: Int? = null,
    @SerialName("sessions_total") val sessionsTotal: Int? = null,
    @SerialName("confidence") val confidence: Double? = null
)

