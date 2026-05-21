package com.han.battery.data.model

data class BatteryPerformancePost(
    val id: String,
    val sharedReportId: Int? = null,
    val userName: String,
    val smartphoneModel: String?,
    val powerBankManufacturer: String?,
    val powerBankModel: String,
    val powerBankCapacityMah: Int? = null,
    val totalUsageHours: Double,
    val totalCapacityAh: Double? = null,
    val finishedSessionCount: Int? = null,
    val estimatedFullCharges: Int?,
    val estimatedSoh: Int?,
    val efficiencyPct: Double? = null,
    val meanTemperatureC: Double? = null,
    val sohHistory: List<Int> = emptyList(),
    val comment: String,
    val createdAt: String,
    val verified: Boolean = true
) {
    val condition: SohCondition
        get() = when {
            estimatedSoh == null -> SohCondition.UNKNOWN
            estimatedSoh >= 90 -> SohCondition.GOOD
            estimatedSoh >= 80 -> SohCondition.NORMAL
            else -> SohCondition.CAUTION
        }
}

enum class SohCondition(
    val label: String,
    val description: String
) {
    UNKNOWN("미분석", "SOH 분석 전"),
    GOOD("좋음", "SOH 90% 이상"),
    NORMAL("보통", "SOH 80~89%"),
    CAUTION("주의", "SOH 80% 미만")
}
