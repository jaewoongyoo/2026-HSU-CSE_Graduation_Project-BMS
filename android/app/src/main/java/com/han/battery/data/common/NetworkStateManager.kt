package com.han.battery.data.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 네트워크 연결 상태를 감지하고 관리하는 매니저
 */
class NetworkStateManager(private val context: Context) {
    private val TAG = "NetworkStateManager"

    private val _isOnline = MutableStateFlow(isNetworkAvailable())
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        Log.d(TAG, "네트워크 상태 모니터링 시작: ${_isOnline.value}")
    }

    /**
     * 현재 네트워크 연결 상태 확인
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Log.d(TAG, "네트워크 상태 확인: $hasInternetCapability")
            hasInternetCapability
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            networkInfo.isConnected
        }
    }

    /**
     * 네트워크 상태 업데이트
     */
    fun updateNetworkState() {
        val newState = isNetworkAvailable()
        val oldState = _isOnline.value

        if (newState != oldState) {
            _isOnline.value = newState
            Log.d(TAG, if (newState) "✅ 온라인 상태" else "⚠️ 오프라인 상태")
        }
    }

    /**
     * 오프라인 상태 여부
     */
    fun isOffline(): Boolean = !_isOnline.value
}

