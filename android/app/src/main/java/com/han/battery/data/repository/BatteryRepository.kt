package com.han.battery.data.repository

import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.model.BatteryStatus

/**
 * 배터리 관련 데이터에 접근하는 인터페이스입니다.
 * 실제 구현은 서브클래스에서 제공됩니다.
 */
interface BatteryRepository {
    
    /**
     * 지정된 기기의 현재 배터리 상태를 반환합니다.
     */
    suspend fun getBatteryStatus(device: BatteryDevice): BatteryStatus
    
    /**
     * 배터리 상태를 저장합니다.
     */
    suspend fun saveBatteryStatus(device: BatteryDevice, status: BatteryStatus)
    
    /**
     * 기기를 저장합니다.
     */
    suspend fun saveDevice(device: BatteryDevice)
    
    /**
     * 모든 저장된 기기를 반환합니다.
     */
    suspend fun getAllDevices(): List<BatteryDevice>
    
    /**
     * 지정된 ID의 기기를 반환합니다.
     */
    suspend fun getDevice(nickname: String): BatteryDevice?
    
    /**
     * 기기를 삭제합니다.
     */
    suspend fun deleteDevice(nickname: String)
}

