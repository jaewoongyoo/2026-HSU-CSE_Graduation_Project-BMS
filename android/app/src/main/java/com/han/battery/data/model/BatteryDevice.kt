package com.han.battery.data.model
/**
 * 사용자가 등록한 보조배터리의 정보를 담는 데이터 클래스
 */
data class BatteryDevice(
    val brand: String,          // 제조사 (예: 삼성, 샤오미)
    val nickname: String,       // 별칭 (예: 나의 맥세이프)
    val capacity: Int,          // 정격 용량 (mAh)
    val manufactureDate: String // 제조년월 (YYYY-MM)
)