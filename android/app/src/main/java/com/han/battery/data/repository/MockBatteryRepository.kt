package com.han.battery.data.repository

import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.model.BatteryStatus
import kotlin.random.Random

/**
 * 테스트 및 개발용 Mock BatteryRepository입니다.
 * 실제 센서 데이터 대신 더미 데이터를 반환합니다.
 */
class MockBatteryRepository : BatteryRepository {
    
    private val devices = mutableListOf<BatteryDevice>()
    private val statusMap = mutableMapOf<String, BatteryStatus>()
    private val random = Random
    
    override suspend fun getBatteryStatus(device: BatteryDevice): BatteryStatus {
        return statusMap[device.nickname] ?: BatteryStatus(
            soc = random.nextInt(60, 96),
            soh = random.nextInt(80, 101),
            current = random.nextInt(1000, 3001),
            voltage = 4.8 + (random.nextDouble() * 0.4),
            power = 10.0 + (random.nextDouble() * 10.0)
        )
    }
    
    override suspend fun saveBatteryStatus(device: BatteryDevice, status: BatteryStatus) {
        statusMap[device.nickname] = status
    }
    
    override suspend fun saveDevice(device: BatteryDevice) {
        devices.removeIf { it.nickname == device.nickname }
        devices.add(device)
    }
    
    override suspend fun getAllDevices(): List<BatteryDevice> {
        return devices.toList()
    }
    
    override suspend fun getDevice(nickname: String): BatteryDevice? {
        return devices.find { it.nickname == nickname }
    }
    
    override suspend fun deleteDevice(nickname: String) {
        devices.removeIf { it.nickname == nickname }
        statusMap.remove(nickname)
    }
}

