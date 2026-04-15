package com.han.battery.data.common

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Serializable

/**
 * 오프라인 상태에서 수행한 작업을 저장하고 온라인 복귀 시 동기화하는 큐
 */
sealed class OfflineOperation : Serializable {
    abstract val timestamp: Long

    // 배터리 관련 작업
    data class RegisterBattery(
        val modelName: String,
        val capacity: Int,
        val manufactureDate: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OfflineOperation()

    data class UpdateBattery(
        val batteryId: Int,
        val modelName: String? = null,
        val capacity: Int? = null,
        val manufactureDate: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OfflineOperation()

    data class DeleteBattery(
        val batteryId: Int,
        val modelName: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : OfflineOperation()
}

/**
 * 오프라인 작업 큐 매니저
 */
class OfflineOperationQueue {
    private val TAG = "OfflineOperationQueue"
    private val queue = mutableListOf<OfflineOperation>()
    private val mutex = Mutex()

    /**
     * 작업 추가
     */
    suspend fun add(operation: OfflineOperation) {
        mutex.withLock {
            queue.add(operation)
            Log.d(TAG, "오프라인 작업 큐에 추가: ${operation::class.simpleName}")
        }
    }

    /**
     * 모든 작업 조회
     */
    suspend fun getAll(): List<OfflineOperation> {
        return mutex.withLock { queue.toList() }
    }

    /**
     * 작업 제거
     */
    suspend fun remove(operation: OfflineOperation) {
        mutex.withLock {
            queue.remove(operation)
            Log.d(TAG, "오프라인 작업 큐에서 제거: ${operation::class.simpleName}")
        }
    }

    /**
     * 모든 작업 제거 (동기화 완료 시)
     */
    suspend fun clear() {
        mutex.withLock {
            queue.clear()
            Log.d(TAG, "오프라인 작업 큐 초기화")
        }
    }

    /**
     * 큐 크기
     */
    suspend fun size(): Int = mutex.withLock { queue.size }

    /**
     * 큐 상태 확인
     */
    suspend fun isEmpty(): Boolean = mutex.withLock { queue.isEmpty() }
}

