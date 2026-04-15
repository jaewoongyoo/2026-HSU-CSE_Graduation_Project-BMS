package com.han.battery.data.common

import android.util.Log
import kotlinx.coroutines.delay

/**
 * 재시도 가능한 작업을 처리하는 클래스
 * 네트워크 오류 등에서 자동으로 재시도할 수 있음
 */
object RetryPolicy {
    private const val TAG = "RetryPolicy"

    /**
     * 지수 백오프(Exponential Backoff)를 사용한 재시도
     * @param maxRetries 최대 재시도 횟수 (기본값: 3)
     * @param initialDelayMs 초기 지연 시간 (기본값: 1000ms)
     * @param maxDelayMs 최대 지연 시간 (기본값: 10000ms)
     * @param block 실행할 작업
     */
    suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastException: Throwable? = null
        var delayMs = initialDelayMs

        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "작업 시도 ${attempt + 1}/$maxRetries")
                val result = block()

                if (result.isSuccess) {
                    Log.d(TAG, "✅ 작업 성공 (시도 ${attempt + 1})")
                    return result
                } else {
                    lastException = result.exceptionOrNull()
                    Log.w(TAG, "❌ 작업 실패: ${lastException?.message}")
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "⚠️ 예외 발생: ${e.message}")
            }

            // 마지막 시도가 아니면 재시도
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "⏳ ${delayMs}ms 후 재시도...")
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
            }
        }

        Log.e(TAG, "❌ 모든 재시도 실패")
        return Result.failure(lastException ?: Exception("알 수 없는 오류"))
    }

    /**
     * 선형 백오프를 사용한 재시도 (더 간단함)
     * @param maxRetries 최대 재시도 횟수
     * @param delayMs 매번 대기할 시간
     * @param block 실행할 작업
     */
    suspend fun <T> retryWithLinearBackoff(
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastException: Throwable? = null

        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "작업 시도 ${attempt + 1}/$maxRetries")
                val result = block()
                
                if (result.isSuccess) {
                    Log.d(TAG, "✅ 작업 성공")
                    return result
                } else {
                    lastException = result.exceptionOrNull()
                }
            } catch (e: Exception) {
                lastException = e
            }

            if (attempt < maxRetries - 1) {
                delay(delayMs)
            }
        }

        return Result.failure(lastException ?: Exception("알 수 없는 오류"))
    }

    /**
     * 재시도 가능한 작업인지 판단
     */
    fun isRetryable(exception: Exception): Boolean {
        return when {
            exception.message?.contains("timeout", ignoreCase = true) == true -> true
            exception.message?.contains("Connection refused", ignoreCase = true) == true -> true
            exception.message?.contains("failed to connect", ignoreCase = true) == true -> true
            else -> false
        }
    }
}

