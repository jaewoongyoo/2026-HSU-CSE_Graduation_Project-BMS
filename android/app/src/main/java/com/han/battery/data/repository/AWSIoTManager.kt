package com.han.battery.data.repository

import android.content.Context
import android.util.Log
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.google.gson.Gson
import com.han.battery.R
import com.han.battery.data.common.KeyStoreHelper
import com.han.battery.data.model.BatteryTelemetryPayload

class AWSIoTManager(private val context: Context) {
    private val endpoint = "adp3srf4gjqs5-ats.iot.ap-northeast-2.amazonaws.com"
    private lateinit var topic: String
    private lateinit var mqttManager: AWSIotMqttManager
    private var isConnected = false
    private var connectedDeviceId: Int? = null

    fun initAndConnect(clientId: String, deviceId: Int, onConnected: () -> Unit = {}) {
        if (deviceId <= 0) {
            isConnected = false
            Log.w("AWSIoTManager", "유효한 deviceId가 없어 MQTT 연결을 건너뜁니다.")
            return
        }

        topic = "battery/$deviceId/telemetry"

        if (::mqttManager.isInitialized && isConnected && connectedDeviceId == deviceId) {
            Log.d("AWSIoTManager", "이미 연결되어 있음, 스킵")
            return
        }

        if (::mqttManager.isInitialized && connectedDeviceId != null && connectedDeviceId != deviceId) {
            runCatching { mqttManager.disconnect() }
            isConnected = false
            Log.d("AWSIoTManager", "활성 deviceId 변경 감지 → MQTT 재연결")
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
                        connectedDeviceId = deviceId
                        Log.d("AWSIoTManager", "AWS 연결 성공! 🚀 topic=$topic")
                        onConnected()
                    }
                    AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Reconnecting -> {
                        isConnected = false
                        Log.d("AWSIoTManager", "재연결 중...")
                    }
                    AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost -> {
                        isConnected = false
                        connectedDeviceId = null
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
        logs: List<BatteryTelemetryPayload>,
        onSuccess: () -> Unit,
        onFailure: (Throwable?) -> Unit = {}
    ) {
        if (!isConnected) {
            Log.w("AWSIoTManager", "MQTT 미연결 상태 - 전송 스킵")
            onFailure(null)
            return
        }

        if (logs.isEmpty()) {
            onSuccess()
            return
        }

        try {
            var acknowledgedCount = 0
            logs.forEach { log ->
                val payload = Gson().toJson(log)
                mqttManager.publishString(
                    payload,
                    topic,
                    AWSIotMqttQos.QOS1,
                    { _, _ ->
                        synchronized(this) {
                            acknowledgedCount += 1
                            if (acknowledgedCount == logs.size) {
                                onSuccess()
                            }
                        }
                    },
                    null
                )
            }
            Log.d("AWSIoTManager", "Telemetry 발행 요청 완료: topic=$topic, count=${logs.size}")
        } catch (e: Exception) {
            Log.e("AWSIoTManager", "전송 실패", e)
            onFailure(e)
        }
    }
}
