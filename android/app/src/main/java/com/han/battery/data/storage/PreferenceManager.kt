package com.han.battery.data.storage

// 배터리 정보를 로컬 저장소(SharedPreferences)에 저장/로드하는 매니저 (사용자별로 데이터 분리)
// ERD의 devices 테이블 기준

import android.content.Context
import android.content.SharedPreferences
import com.han.battery.data.model.BatteryDevice
import com.han.battery.data.common.AppLogger
import org.json.JSONArray
import org.json.JSONObject

class PreferenceManager(context: Context, private val userManager: UserManager? = null) {
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
     * 현재 로그인한 사용자의 고유 키를 생성합니다.
     * 사용자별로 배터리 데이터를 분리하기 위해 사용됩니다.
     */
    private fun getUserDevicesKey(): String {
        val currentUser = userManager?.getCurrentUser() ?: "default"
        return "${currentUser}_$KEY_DEVICES"
    }

    private fun getUserDevicesExistsKey(): String {
        val currentUser = userManager?.getCurrentUser() ?: "default"
        return "${currentUser}_$KEY_DEVICES_EXISTS"
    }

    /**
     * 배터리 기기 정보를 저장합니다. (ERD devices 테이블 기준)
     * 현재 로그인한 사용자별로 데이터가 분리되어 저장됩니다.
     */
    fun saveBatteryDevice(device: BatteryDevice) {
        try {
            val devices = getAllDevices().toMutableList()
            // 같은 모델명의 기기가 있으면 제거
            devices.removeAll { it.model_name == device.model_name }
            devices.add(device)

            val jsonArray = JSONArray()
            for (d in devices) {
                val json = JSONObject().apply {
                    put("id", d.id)
                    put("model_name", d.model_name)
                    put("powerbank_capacity_mah", d.powerbank_capacity_mah)
                    put("manufacture_date", d.manufacture_date)
                }
                jsonArray.put(json)
            }

            val jsonString = jsonArray.toString()
            val userDevicesKey = getUserDevicesKey()
            val userDevicesExistsKey = getUserDevicesExistsKey()

            prefs.edit().apply {
                putString(userDevicesKey, jsonString)
                putBoolean(userDevicesExistsKey, true)
                apply()  // commit() 대신 apply() 사용 (비동기, 더 빠름)
            }
            val currentUser = userManager?.getCurrentUser() ?: "default"
            AppLogger.info("배터리 저장 성공 [$currentUser]: ${device.model_name}, 총 ${devices.size}개", TAG)
        } catch (e: Exception) {
            AppLogger.error("배터리 저장 실패", e, TAG)
        }
    }

    /**
     * 저장된 모든 배터리 기기 정보를 로드합니다. (ERD devices 테이블 기준)
     * 현재 로그인한 사용자의 데이터만 반환됩니다.
     */
    fun getAllDevices(): List<BatteryDevice> {
        return try {
            val userDevicesKey = getUserDevicesKey()
            val jsonString = prefs.getString(userDevicesKey, null)

            if (jsonString.isNullOrEmpty()) {
                return emptyList()
            }

            val devices = mutableListOf<BatteryDevice>()
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val device = BatteryDevice(
                    model_name = json.getString("model_name"),
                    powerbank_capacity_mah = json.getInt("powerbank_capacity_mah"),
                    manufacture_date = json.getString("manufacture_date"),
                    id = json.optInt("id", 0)  // ✅ ID 필드 추가 (없으면 기본값 0)
                )
                devices.add(device)
            }
            
            val currentUser = userManager?.getCurrentUser() ?: "default"
            AppLogger.info("배터리 로드 성공 [$currentUser]: ${devices.size}개", TAG)
            devices
        } catch (e: Exception) {
            AppLogger.error("배터리 로드 실패", e, TAG)
            emptyList()
        }
    }

    /**
     * 특정 모델명의 배터리 기기 정보를 로드합니다.
     */
    fun getBatteryDevice(modelName: String): BatteryDevice? {
        return getAllDevices().find { it.model_name == modelName }
    }

    /**
     * 배터리 기기가 등록되어 있는지 확인합니다.
     */
    fun isDeviceRegistered(): Boolean {
        val devices = getAllDevices()
        val userDevicesExistsKey = getUserDevicesExistsKey()
        val isRegistered = prefs.getBoolean(userDevicesExistsKey, false) && devices.isNotEmpty()
        return isRegistered
    }

    /**
     * 특정 기기를 삭제합니다.
     */
    fun deleteDevice(modelName: String) {
        try {
            val devices = getAllDevices().toMutableList()
            devices.removeAll { it.model_name == modelName }

            if (devices.isEmpty()) {
                clearAllDevices()
            } else {
                val jsonArray = JSONArray()
                for (d in devices) {
                    val json = JSONObject().apply {
                        put("id", d.id)
                        put("model_name", d.model_name)
                        put("powerbank_capacity_mah", d.powerbank_capacity_mah)
                        put("manufacture_date", d.manufacture_date)
                    }
                    jsonArray.put(json)
                }

                val jsonString = jsonArray.toString()
                val userDevicesKey = getUserDevicesKey()
                prefs.edit().apply {
                    putString(userDevicesKey, jsonString)
                    apply()  // commit() 대신 apply() 사용
                }
            }
            
            val currentUser = userManager?.getCurrentUser() ?: "default"
            AppLogger.info("배터리 삭제 완료 [$currentUser]: $modelName", TAG)
        } catch (e: Exception) {
            AppLogger.error("배터리 삭제 실패", e, TAG)
        }
    }

    /**
     * 저장된 모든 배터리 기기 정보를 삭제합니다.
     * 현재 사용자의 데이터만 삭제됩니다.
     */
    fun clearAllDevices() {
        val userDevicesKey = getUserDevicesKey()
        val userDevicesExistsKey = getUserDevicesExistsKey()
        prefs.edit().apply {
            remove(userDevicesKey)
            putBoolean(userDevicesExistsKey, false)
            apply()  // commit() 대신 apply() 사용
        }
        val currentUser = userManager?.getCurrentUser() ?: "default"
        AppLogger.info("모든 배터리 삭제됨 [$currentUser]", TAG)
    }

    /**
     * 사용자 로그아웃 시 호출되는 메서드
     * 로그아웃 전 현재 사용자의 배터리 데이터를 초기화할 수 있습니다. (선택사항)
     */
    fun onUserLogout() {
        // 로그아웃 시 실행할 정리 작업 (현재는 추가 작업 없음)
        // 향후 필요 시 여기에 추가
        val previousUser = userManager?.getCurrentUser() ?: "default"
        AppLogger.info("사용자 로그아웃 처리 [$previousUser]", TAG)
    }
}
