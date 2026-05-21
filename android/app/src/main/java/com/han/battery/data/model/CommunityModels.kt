package com.han.battery.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CommunityCardResponse(
    val shared_report_id: Int,
    val user: CommunityUserInfo,
    val device: CommunityDeviceInfo,
    val session_stats: CommunitySessionStats,
    val efficiency_stats: CommunityEfficiencyStats,
    val created_at: String
)

@Serializable
data class CommunityUserInfo(
    val username: String,
    val phone_model: String? = null
)

@Serializable
data class CommunityDeviceInfo(
    val manufacturer: String? = null,
    val model_name: String,
    val powerbank_capacity_mah: Int? = null
)

@Serializable
data class CommunitySessionStats(
    val total_usage_hours: Double,
    val total_capacity_ah: Double,
    val finished_session_count: Int
)

@Serializable
data class CommunityEfficiencyStats(
    val soh_percentage: Double? = null,
    val efficiency_pct: Double? = null,
    val mean_temperature_c: Double? = null
)

@Serializable
data class CommunityFilterRequest(
    val phone_models: List<String>? = null,
    val manufacturers: List<String>? = null,
    val soh_min: Double? = null,
    val soh_max: Double? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

@Serializable
data class CommunityFilterOptionsResponse(
    val phone_models: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList()
)

@Serializable
data class CommunityShareRequest(
    val device_id: Int,
    val is_public: Boolean = true
)

@Serializable
data class CommunityShareUpdateRequest(
    val is_public: Boolean
)

@Serializable
data class CommunityShareResponse(
    val shared_report_id: Int,
    val device_id: Int,
    val is_public: Boolean,
    val share_token: String,
    val created_at: String
)

@Serializable
data class CommunityDeleteResponse(
    val success: Boolean,
    val message: String
)
