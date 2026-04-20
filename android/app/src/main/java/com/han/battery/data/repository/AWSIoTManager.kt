package com.han.battery.data.repository

import android.content.Context
import android.util.Log
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.google.gson.Gson
import com.han.battery.R
import com.han.battery.data.common.KeyStoreHelper
import com.han.battery.data.model.BatteryLog
import com.han.battery.data.storage.UserManager

class AWSIoTManager(private val context: Context) {
    private val endpoint = "adp3srf4gjqs5-ats.iot.ap-northeast-2.amazonaws.com"
    private lateinit var topic: String
    private lateinit var mqttManager: AWSIotMqttManager
    private var isConnected = false

    fun initAndConnect(clientId: String, onConnected: () -> Unit = {}) {
        val userId = UserManager(context).getCurrentUserId()
        if (userId <= 0) {
            isConnected = false
            Log.w("AWSIoTManager", "로그인된 사용자 ID가 없어 MQTT 연결을 건너뜁니다.")
            return
        }

        topic = "battery/$userId/telemetry"

        if (::mqttManager.isInitialized && isConnected) {
            Log.d("AWSIoTManager", "이미 연결되어 있음, 스킵")
            return
        }
        try {
            mqttManager = AWSIotMqttManager(clientId, endpoint)
            val keyStore = KeyStoreHelper.getKeyStore(
                context,
                R.raw.certificate,
                R.raw.private_key,
                R.raw.amazon_root_ca1
            )
            // ✅ KeyStore 로드 확인 로그 추가
            Log.d("AWSIoTManager", "KeyStore 로드 성공 ✅ 타입: ${keyStore.type}, 크기: ${keyStore.size()}")
            mqttManager.connect(keyStore) { status, throwable ->
                when (status) {
                    AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected -> {
                        isConnected = true
                        Log.d("AWSIoTManager", "AWS 연결 성공! 🚀")
                        onConnected()
                    }
                    AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Reconnecting -> {
                        isConnected = false
                        Log.d("AWSIoTManager", "재연결 중...")
                    }
                    AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost -> {
                        isConnected = false
                        Log.e("AWSIoTManager", "연결 끊김: ${throwable?.message}")
                    }
                    else -> Log.d("AWSIoTManager", "상태 변경: $status")
                }
            }
        } catch (e: Exception) {
            Log.e("AWSIoTManager", "초기화 실패", e)
        }
    }

    fun publishLogs(
        logs: List<BatteryLog>,
        onSuccess: () -> Unit,
        onFailure: (Throwable?) -> Unit = {}
    ) {
        if (!isConnected) {
            Log.w("AWSIoTManager", "MQTT 미연결 상태 - 전송 스킵")
            onFailure(null)
            return
        }
        val payload = Gson().toJson(logs)
        try {
            mqttManager.publishString(
                payload,
                topic,
                AWSIotMqttQos.QOS1,
                { _, _ ->
                    onSuccess()
                },
                null
            )
        } catch (e: Exception) {
            Log.e("AWSIoTManager", "전송 실패", e)
            onFailure(e)
        }
    }
}
