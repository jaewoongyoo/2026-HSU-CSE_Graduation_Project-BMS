package com.han.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import com.han.battery.data.common.NetworkStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 네트워크 상태 변화를 감지하는 BroadcastReceiver
 * 온라인 → 오프라인 또는 오프라인 → 온라인으로 변할 때 감지
 */
class NetworkStateReceiver(
    private val networkStateManager: NetworkStateManager,
    private val onNetworkStateChanged: (isOnline: Boolean) -> Unit = {}
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkStateReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Log.d(TAG, "네트워크 상태 변화 감지")

            // 백그라운드에서 상태 업데이트
            CoroutineScope(Dispatchers.Default).launch {
                networkStateManager.updateNetworkState()

                val isOnline = networkStateManager.isOnline.value
                Log.d(TAG, if (isOnline) "✅ 온라인 상태 복구" else "⚠️ 오프라인 상태")

                // UI 스레드에서 콜백 실행
                onNetworkStateChanged(isOnline)
            }
        }
    }
}

