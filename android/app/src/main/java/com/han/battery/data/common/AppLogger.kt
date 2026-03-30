package com.han.battery.data.common
// 앱 전체에서 사용하는 로깅 유틸리티 (Debug, Info, Warning, Error 레벨)

import android.util.Log

const val DEFAULT_TAG = "BatteryInsight"

/**
 * 앱 전체에서 사용하는 로깅 유틸리티입니다.
 */
object AppLogger {
    
    /**
     * 디버그 레벨 로그를 출력합니다.
     */
    fun debug(message: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, message)
    }
    
    /**
     * 정보 레벨 로그를 출력합니다.
     */
    fun info(message: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, message)
    }
    
    /**
     * 경고 레벨 로그를 출력합니다.
     */
    fun warning(message: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, message)
    }
    
    /**
     * 에러 레벨 로그를 출력합니다.
     */
    fun error(message: String, exception: Throwable? = null, tag: String = DEFAULT_TAG) {
        Log.e(tag, message, exception)
    }
    
    /**
     * 함수 실행을 로깅하면서 실행합니다.
     */
    inline fun <T> logExecution(
        functionName: String,
        tag: String = DEFAULT_TAG,
        block: () -> T
    ): T {
        debug(">>> $functionName 시작", tag)
        val startTime = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - startTime
            debug("<<< $functionName 완료 (${duration}ms)", tag)
            result
        } catch (e: Exception) {
            error("!!! $functionName 실패", e, tag)
            throw e
        }
    }
}

