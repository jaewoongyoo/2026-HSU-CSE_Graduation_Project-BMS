package com.han.battery

import android.app.Application
import com.han.battery.data.storage.BatteryDatabase
import com.han.battery.data.repository.BatteryRepository
import com.han.battery.data.repository.AWSIoTManager

class BatteryApplication : Application() {
    // ✅ 1. 데이터베이스와 리포지토리 준비
    private val database by lazy { BatteryDatabase.getDatabase(this) }
    val repository by lazy {
        // AWSIoTManager가 필요하므로 아래 선언된 객체를 전달합니다.
        BatteryRepository(database.batteryDao(), awsIoTManager)
    }

    // ✅ 2. AWS 매니저 준비 (에러의 주인공!)
    // 이 줄이 있어야 서비스에서 (application as BatteryApplication).awsIoTManager로 접근 가능합니다.
    val awsIoTManager by lazy { AWSIoTManager(this) }

    override fun onCreate() {
        super.onCreate()
        java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }
}