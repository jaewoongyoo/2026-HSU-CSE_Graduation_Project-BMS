package com.han.battery.data.repository

import android.content.Context
import android.util.Log
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos
import com.google.gson.Gson
import com.han.battery.R
import com.han.battery.data.common.KeyStoreHelper

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
            Log.d("AWSIoTManager", "이미 연결되어 있음, 스킵 (콜백은 실행합니다)")
            onConnected() // ⭐ [수정 1] 콜백 누락 버그 해결! 타이머 루프 정상 가동
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

    // ⭐ [수정 2] 서비스 종료(onDestroy) 시 호출할 연결 해제 함수 추가
    fun disconnect() {
        if (::mqttManager.isInitialized && isConnected) {
            runCatching { mqttManager.disconnect() }
            isConnected = false
            connectedDeviceId = null
            Log.d("AWSIoTManager", "AWS MQTT 연결 강제 종료 완료")
        }
    }

    // ⭐ [수정 3] forEach를 제거하고 배치(Batch) 단일 전송으로 로직 최적화
    fun publishLogs(
        payload: Any, // 어떤 데이터 클래스 형태가 오든 하나로 묶어서 전송합니다.
        onSuccess: () -> Unit,
        onFailure: (Throwable?) -> Unit = {}
    ) {
        if (!isConnected) {
            Log.w("AWSIoTManager", "MQTT 미연결 상태 - 전송 스킵")
            onFailure(null)
            return
        }

        try {
            // 여러 개의 데이터가 담긴 BatchPayload 객체를 통째로 1개의 JSON String으로 변환합니다.
            val jsonPayload = Gson().toJson(payload)

            mqttManager.publishString(
                jsonPayload,
                topic,
                AWSIotMqttQos.QOS1,
                { _, _ ->
                    // 단 한 번의 MQTT 전송만 수행하므로 곧바로 onSuccess 호출!
                    onSuccess()
                },
                null
            )
            Log.d("AWSIoTManager", "Telemetry 1분 단위 배치 전송 완료! 🚀")
        } catch (e: Exception) {
            Log.e("AWSIoTManager", "전송 실패", e)
            onFailure(e)
        }
    }
}