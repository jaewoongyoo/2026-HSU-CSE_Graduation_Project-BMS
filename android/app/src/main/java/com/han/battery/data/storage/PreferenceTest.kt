package com.han.battery.data.storage
// SharedPreferences 저장/로드 테스트 유틸리티

import android.content.Context
import android.util.Log
import com.han.battery.data.model.BatteryDevice

/**
 * PreferenceManager의 저장 기능을 테스트하는 유틸리티 클래스
 * 앱 실행 시 자동으로 테스트 데이터를 저장하고 확인합니다.
 */
object PreferenceTest {
    private const val TAG = "PreferenceTest"

    fun testSaveAndLoad(context: Context) {
        val pm = PreferenceManager(context)
        
        // 기존 데이터 확인
        Log.d(TAG, "=== 테스트 시작 ===")
        Log.d(TAG, "기존 기기 수: ${pm.getAllDevices().size}")
        pm.getAllDevices().forEach {
            Log.d(TAG, "  - ${it.nickname} (${it.capacity}mAh)")
        }
        
        // 테스트 데이터 생성
        val testDevice = BatteryDevice(
            brand = "테스트제조사",
            nickname = "테스트배터리",
            capacity = 5000,
            manufactureDate = "2024-01"
        )
        
        // 저장
        Log.d(TAG, "테스트 데이터 저장 중...")
        pm.saveBatteryDevice(testDevice)
        
        // 즉시 확인
        Log.d(TAG, "저장 후 즉시 확인:")
        val loaded = pm.getBatteryDevice("테스트배터리")
        if (loaded != null) {
            Log.d(TAG, "✅ 저장된 데이터 확인: ${loaded.nickname} (${loaded.capacity}mAh)")
        } else {
            Log.e(TAG, "❌ 저장된 데이터를 찾을 수 없음!")
        }
        
        // 등록 상태 확인
        val isRegistered = pm.isDeviceRegistered()
        Log.d(TAG, "isDeviceRegistered: $isRegistered")
        
        // 전체 기기 목록
        Log.d(TAG, "현재 저장된 기기 목록:")
        pm.getAllDevices().forEach {
            Log.d(TAG, "  - ${it.nickname} (${it.capacity}mAh)")
        }
        Log.d(TAG, "=== 테스트 완료 ===")
    }
}

