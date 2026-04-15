package com.han.battery.data.model

// ERD의 devices 테이블 기준
data class BatteryDevice(
    val model_name: String,
    val powerbank_capacity_mah: Int = 0,
    val manufacture_date: String = "",
    val id: Int = 0  // ✅ 서버 DB의 device ID (삭제할 때 필요)
) {
    init {
        require(model_name.isNotBlank()) { "model_name is required" }
        require(model_name.length <= 100) { "model_name must be 100 characters or fewer" }
        if (powerbank_capacity_mah > 0) {
            require(powerbank_capacity_mah in 100..100000) { "powerbank_capacity_mah must be between 100 and 100000 mAh" }
        }
    }

    fun isValid(): Boolean = model_name.isNotBlank() && powerbank_capacity_mah in 100..100000

    fun getDisplayManufactureDate(): String = manufacture_date.takeIf { it.isNotBlank() } ?: "Unknown"

    fun toRegistrationRequest(userId: Int): BatteryRegistrationRequest {
        return BatteryRegistrationRequest(
            user_id = userId.toString(),    // ✅ String으로 변환
            model_name = model_name,
            powerbank_capacity_mah = powerbank_capacity_mah,
            manufacture_date = manufacture_date.takeIf { it.isNotBlank() }
        )
    }
}


