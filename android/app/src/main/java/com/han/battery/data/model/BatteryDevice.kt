package com.han.battery.data.model

data class BatteryDevice(
    val brand: String = "",
    val nickname: String,
    val capacity: Int = 0,
    val manufactureDate: String = ""
) {
    init {
        require(nickname.isNotBlank()) { "nickname is required" }
        require(nickname.length <= 100) { "nickname must be 100 characters or fewer" }
        if (capacity > 0) {
            require(capacity in 100..200000) { "capacity must be between 100 and 200000 mAh" }
        }
    }

    fun isValid(): Boolean = nickname.isNotBlank() && capacity in 100..200000

    fun getDisplayBrand(): String = brand.takeIf { it.isNotBlank() } ?: "Unknown"

    fun getDisplayManufactureDate(): String = manufactureDate.takeIf { it.isNotBlank() } ?: "Unknown"

    fun toRegistrationRequest(userId: String): BatteryRegistrationRequest {
        return BatteryRegistrationRequest(
            user_id = userId,
            manufacturer = brand.takeIf { it.isNotBlank() },
            model_name = nickname,
            capacity_mah = capacity,
            manufacture_date = manufactureDate.takeIf { it.isNotBlank() },
            powerbank_capacity_mah = capacity
        )
    }
}
