package com.han.battery.data.storage
// 배터리 정보를 로컬 저장소(SharedPreferences)에 저장/로드하는 매니저

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.han.battery.data.model.BatteryDevice
import org.json.JSONArray
import org.json.JSONObject

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "battery_insight_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_DEVICES = "all_devices"
        private const val KEY_DEVICES_EXISTS = "devices_exist"
        private const val TAG = "PreferenceManager"
    }

    /**
     * 배터리 기기 정보를 저장합니다.
     */
    fun saveBatteryDevice(device: BatteryDevice) {
        try {
            val devices = getAllDevices().toMutableList()
            // 같은 별칭의 기기가 있으면 제거
            devices.removeAll { it.nickname == device.nickname }
            devices.add(device)

            val jsonArray = JSONArray()
            for (d in devices) {
                val json = JSONObject().apply {
                    put("brand", d.brand)
                    put("nickname", d.nickname)
                    put("capacity", d.capacity)
                    put("manufactureDate", d.manufactureDate)
                }
                jsonArray.put(json)
            }

            val jsonString = jsonArray.toString()
            prefs.edit().apply {
                putString(KEY_DEVICES, jsonString)
                putBoolean(KEY_DEVICES_EXISTS, true)
                commit()
            }
            Log.d(TAG, "✅ 배터리 저장 성공: ${device.nickname}, 총 ${devices.size}개")
            Log.d(TAG, "📝 저장된 JSON: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 배터리 저장 실패", e)
            e.printStackTrace()
        }
    }

    /**
     * 저장된 모든 배터리 기기 정보를 로드합니다.
     */
    fun getAllDevices(): List<BatteryDevice> {
        return try {
            val jsonString = prefs.getString(KEY_DEVICES, null)
            Log.d(TAG, "📖 로드 시도 - JSON: $jsonString")
            
            if (jsonString.isNullOrEmpty()) {
                Log.d(TAG, "⚠️ 저장된 데이터 없음")
                return emptyList()
            }

            val devices = mutableListOf<BatteryDevice>()
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val device = BatteryDevice(
                    brand = json.getString("brand"),
                    nickname = json.getString("nickname"),
                    capacity = json.getInt("capacity"),
                    manufactureDate = json.getString("manufactureDate")
                )
                devices.add(device)
                Log.d(TAG, "  ✅ 로드: ${device.nickname} (${device.capacity}mAh)")
            }
            
            Log.d(TAG, "✅ 배터리 로드 성공: ${devices.size}개")
            devices
        } catch (e: Exception) {
            Log.e(TAG, "❌ 배터리 로드 실패", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 특정 별칭의 배터리 기기 정보를 로드합니다.
     */
    fun getBatteryDevice(nickname: String): BatteryDevice? {
        return getAllDevices().find { it.nickname == nickname }
    }

    /**
     * 배터리 기기가 등록되어 있는지 확인합니다.
     */
    fun isDeviceRegistered(): Boolean {
        val devices = getAllDevices()
        val isRegistered = prefs.getBoolean(KEY_DEVICES_EXISTS, false) && devices.isNotEmpty()
        Log.d(TAG, "🔍 deviceRegistered check: $isRegistered, devices count: ${devices.size}")
        return isRegistered
    }

    /**
     * 특정 기기를 삭제합니다.
     */
    fun deleteDevice(nickname: String) {
        try {
            val devices = getAllDevices().toMutableList()
            Log.d(TAG, "🗑️ 삭제 전 기기 목록: ${devices.map { it.nickname }}")
            
            devices.removeAll { it.nickname == nickname }
            Log.d(TAG, "🗑️ 삭제 후 기기 목록: ${devices.map { it.nickname }}")

            if (devices.isEmpty()) {
                Log.d(TAG, "🗑️ 모든 기기가 삭제됨 - clearAllDevices() 호출")
                clearAllDevices()
            } else {
                val jsonArray = JSONArray()
                for (d in devices) {
                    val json = JSONObject().apply {
                        put("brand", d.brand)
                        put("nickname", d.nickname)
                        put("capacity", d.capacity)
                        put("manufactureDate", d.manufactureDate)
                    }
                    jsonArray.put(json)
                }

                val jsonString = jsonArray.toString()
                prefs.edit().apply {
                    putString(KEY_DEVICES, jsonString)
                    commit()
                }
                Log.d(TAG, "💾 삭제된 기기를 제외한 데이터 저장 완료")
                Log.d(TAG, "📝 저장된 JSON: $jsonString")
            }
            
            // 삭제 후 즉시 확인
            val remainingDevices = getAllDevices()
            Log.d(TAG, "✅ 배터리 삭제 완료: $nickname, 남은 기기: ${remainingDevices.size}개")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 배터리 삭제 실패", e)
            e.printStackTrace()
        }
    }

    /**
     * 저장된 모든 배터리 기기 정보를 삭제합니다.
     */
    fun clearAllDevices() {
        prefs.edit().apply {
            remove(KEY_DEVICES)
            putBoolean(KEY_DEVICES_EXISTS, false)
            commit()
        }
        Log.d(TAG, "✅ 모든 배터리 삭제됨")
    }
}
