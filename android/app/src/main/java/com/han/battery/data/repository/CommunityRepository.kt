package com.han.battery.data.repository

import com.han.battery.data.api.ApiService
import com.han.battery.data.common.AppLogger
import com.han.battery.data.model.BatteryPerformancePost
import com.han.battery.data.model.CommunityCardResponse
import com.han.battery.data.model.CommunityDeleteResponse
import com.han.battery.data.model.CommunityFilterOptionsResponse
import com.han.battery.data.model.CommunityFilterRequest
import com.han.battery.data.model.CommunityShareRequest
import com.han.battery.data.model.CommunityShareResponse
import com.han.battery.data.model.CommunityShareUpdateRequest
import kotlin.math.roundToInt

class CommunityRepository(
    private val apiService: ApiService
) {
    suspend fun getCommunity(limit: Int = 50, offset: Int = 0): Result<List<BatteryPerformancePost>> {
        return apiService.getCommunity(limit, offset).mapCatching { responses ->
            responses.map { it.toPerformancePost() }
        }.onFailure { error ->
            AppLogger.error("커뮤니티 피드 조회 실패", error, TAG)
        }
    }

    suspend fun getCommunityFiltered(
        request: CommunityFilterRequest
    ): Result<List<BatteryPerformancePost>> {
        return apiService.getCommunityFiltered(request).mapCatching { responses ->
            responses.map { it.toPerformancePost() }
        }.onFailure { error ->
            AppLogger.error("커뮤니티 필터 조회 실패", error, TAG)
        }
    }

    suspend fun getFilterOptions(): Result<CommunityFilterOptionsResponse> {
        return apiService.getCommunityFilterOptions().onFailure { error ->
            AppLogger.error("커뮤니티 필터 옵션 조회 실패", error, TAG)
        }
    }

    suspend fun shareDevice(deviceId: Int, isPublic: Boolean = true): Result<CommunityShareResponse> {
        return apiService.shareDevice(
            deviceId = deviceId,
            request = CommunityShareRequest(device_id = deviceId, is_public = isPublic)
        )
    }

    suspend fun updateShareStatus(
        sharedReportId: Int,
        isPublic: Boolean
    ): Result<CommunityShareResponse> {
        return apiService.updateShareStatus(
            sharedReportId = sharedReportId,
            request = CommunityShareUpdateRequest(is_public = isPublic)
        )
    }

    suspend fun deleteShare(sharedReportId: Int): Result<CommunityDeleteResponse> {
        return apiService.deleteShare(sharedReportId)
    }

    private fun CommunityCardResponse.toPerformancePost(): BatteryPerformancePost {
        val soh = efficiency_stats.soh_percentage?.roundToInt()
        val manufacturer = device.manufacturer?.ifBlank { null }

        return BatteryPerformancePost(
            id = shared_report_id.toString(),
            sharedReportId = shared_report_id,
            userName = user.username,
            smartphoneModel = user.phone_model,
            powerBankManufacturer = manufacturer,
            powerBankModel = device.model_name,
            powerBankCapacityMah = device.powerbank_capacity_mah,
            totalUsageHours = session_stats.total_usage_hours,
            totalCapacityAh = session_stats.total_capacity_ah,
            finishedSessionCount = session_stats.finished_session_count,
            estimatedFullCharges = estimateFullCharges(),
            estimatedSoh = soh,
            efficiencyPct = efficiency_stats.efficiency_pct,
            meanTemperatureC = efficiency_stats.mean_temperature_c,
            sohHistory = generateMockSohHistory(soh),
            comment = buildComment(),
            createdAt = created_at.toDisplayDate()
        )
    }

    private fun generateMockSohHistory(currentSoh: Int?): List<Int> {
        if (currentSoh == null) return emptyList()
        return listOf(
            (currentSoh + 3).coerceAtMost(100),
            (currentSoh + 2).coerceAtMost(100),
            (currentSoh + 1).coerceAtMost(100),
            currentSoh
        )
    }

    private fun CommunityCardResponse.estimateFullCharges(): Int? {
        val capacityMah = device.powerbank_capacity_mah ?: return null
        if (capacityMah <= 0) return null
        val totalMah = session_stats.total_capacity_ah * 1000.0
        if (totalMah <= 0.0) return null
        return (totalMah / capacityMah).roundToInt().coerceAtLeast(0)
    }

    private fun CommunityCardResponse.buildComment(): String {
        val parts = buildList {
            add("완료 세션 ${session_stats.finished_session_count}회")
            add("누적 ${formatOneDecimal(session_stats.total_usage_hours)}시간")
            efficiency_stats.efficiency_pct?.let { add("효율 ${formatOneDecimal(it)}%") }
            efficiency_stats.mean_temperature_c?.let { add("평균 온도 ${formatOneDecimal(it)}도") }
        }
        return parts.joinToString(" · ")
    }

    private fun String.toDisplayDate(): String {
        return take(10).replace("-", ".")
    }

    private fun formatOneDecimal(value: Double): String {
        return String.format("%.1f", value)
    }

    private companion object {
        private const val TAG = "CommunityRepository"
    }
}
