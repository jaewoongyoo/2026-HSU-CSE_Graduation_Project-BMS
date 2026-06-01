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
    
    @Volatile
    private var isConnected = false
    
    @Volatile
    private var isConnecting = false
    
    @Volatile
    private var connectedDeviceId: Int? = null

    // 중복 연결 시도 방지를 위해 대기 중인 콜백 리스트
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    fun initAndConnect(clientId: String, deviceId: Int, onConnected: () -> Unit = {}) {
        if (deviceId <= 0) {
            synchronized(this) {
                isConnected = false
                isConnecting = false
            }
            Log.w("AWSIoTManager", "유효한 deviceId가 없어 MQTT 연결을 건너뜁니다.")
            return
        }

        topic = "battery/$deviceId/telemetry"

        synchronized(this) {
            if (isConnected && connectedDeviceId == deviceId) {
                Log.d("AWSIoTManager", "이미 연결되어 있음, 스킵 (콜백 실행)")
                onConnected()
                return
            }

            if (isConnecting && connectedDeviceId == deviceId) {
                Log.d("AWSIoTManager", "현재 연결이 진행 중입니다. 콜백을 대기 목록에 추가합니다.")
                pendingCallbacks.add(onConnected)
                return
            }

            // 연결 중인 디바이스가 다른 경우, 기존 연결 및 상태를 정리하고 재연결
            if (connectedDeviceId != null && connectedDeviceId != deviceId) {
                Log.d("AWSIoTManager", "활성 deviceId 변경 감지 ($connectedDeviceId -> $deviceId) → 기존 MQTT 연결 정리")
                cleanupConnection()
            }

            isConnecting = true
            pendingCallbacks.add(onConnected)
        }

        try {
            mqttManager = AWSIotMqttManager(clientId, endpoint).apply {
                keepAlive = 15 // AWS IoT 연결 유지용 Ping 주기 설정 (초 단위)
                isAutoReconnect = true // 자동 재연결 활성화
            }
            
            val keyStore = KeyStoreHelper.getKeyStore(
                context,
                R.raw.certificate,
                R.raw.private_key,
                R.raw.amazon_root_ca1
            )
            Log.d("AWSIoTManager", "KeyStore 로드 성공 ✅ 타입: ${keyStore.type}, 크기: ${keyStore.size()}")

            mqttManager.connect(keyStore) { status, throwable ->
                synchronized(this@AWSIoTManager) {
                    when (status) {
                        AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected -> {
                            isConnected = true
                            isConnecting = false
                            connectedDeviceId = deviceId
                            Log.d("AWSIoTManager", "AWS 연결 성공! 🚀 topic=$topic")
                            
                            // 대기 중이던 모든 콜백을 순차적으로 안전하게 실행하고 비움
                            val callbacks = ArrayList(pendingCallbacks)
                            pendingCallbacks.clear()
                            callbacks.forEach { it.invoke() }
                        }
                        AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Reconnecting -> {
                            isConnected = false
                            Log.d("AWSIoTManager", "AWS 재연결 시도 중...")
                        }
                        AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.ConnectionLost -> {
                            isConnected = false
                            isConnecting = false
                            connectedDeviceId = null
                            pendingCallbacks.clear()
                            Log.e("AWSIoTManager", "AWS 연결 끊김: ${throwable?.message}", throwable)
                        }
                        else -> Log.d("AWSIoTManager", "AWS 상태 변경: $status")
                    }
                }
            }
        } catch (e: Exception) {
            synchronized(this) {
                isConnecting = false
                pendingCallbacks.clear()
            }
            Log.e("AWSIoTManager", "AWS 초기화 및 연결 시도 실패 ❌", e)
        }
    }

    private fun cleanupConnection() {
        if (::mqttManager.isInitialized) {
            runCatching { mqttManager.disconnect() }
        }
        isConnected = false
        isConnecting = false
        connectedDeviceId = null
        pendingCallbacks.clear()
    }

    fun disconnect() {
        synchronized(this) {
            cleanupConnection()
            Log.d("AWSIoTManager", "AWS MQTT 연결 해제 및 상태 리셋 완료")
        }
    }

    fun publishLogs(
        payload: Any,
        onSuccess: () -> Unit,
        onFailure: (Throwable?) -> Unit = {}
    ) {
        val isMqttConnected = synchronized(this) { isConnected }
        if (!isMqttConnected) {
            Log.w("AWSIoTManager", "MQTT 미연결 상태 - 전송 스킵")
            onFailure(null)
            return
        }

        try {
            val jsonPayload = Gson().toJson(payload)

            mqttManager.publishString(
                jsonPayload,
                topic,
                AWSIotMqttQos.QOS1,
                { _, _ ->
                    onSuccess()
                },
                null
            )
            Log.d("AWSIoTManager", "Telemetry 배치 전송 완료! 🚀")
        } catch (e: Exception) {
            Log.e("AWSIoTManager", "Telemetry 전송 중 오류 발생", e)
            onFailure(e)
        }
    }
}