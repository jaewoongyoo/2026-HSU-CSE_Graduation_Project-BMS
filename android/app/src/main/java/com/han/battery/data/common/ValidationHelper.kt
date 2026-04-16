package com.han.battery.data.common

import android.util.Log

/**
 * 입력값 검증을 담당하는 클래스
 * 사용자 입력과 API 응답 데이터를 검증
 */
object ValidationHelper {
    private const val TAG = "ValidationHelper"

    // 정규식 패턴
    private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")
    private val PASSWORD_PATTERN = Regex("^.{6,}$") // 최소 6자
    private val MODEL_NAME_PATTERN = Regex("^.{1,100}$")
    private val MANUFACTURE_DATE_PATTERN = Regex("^\\d{4}-\\d{2}$") // YYYY-MM 형식

    /**
     * 사용자명 검증
     */
    fun validateUsername(username: String): Result<String> {
        return when {
            username.isBlank() -> {
                Log.w(TAG, "❌ 사용자명이 비어있음")
                Result.failure(Exception("사용자명을 입력하세요"))
            }
            !USERNAME_PATTERN.matches(username) -> {
                Log.w(TAG, "❌ 사용자명 형식 오류: $username")
                Result.failure(Exception("사용자명은 3~20자의 알파벳, 숫자, _만 사용 가능합니다"))
            }
            else -> {
                Log.d(TAG, "✅ 사용자명 검증 통과: $username")
                Result.success(username)
            }
        }
    }

    /**
     * 비밀번호 검증
     */
    fun validatePassword(password: String): Result<String> {
        return when {
            password.isBlank() -> {
                Log.w(TAG, "❌ 비밀번호가 비어있음")
                Result.failure(Exception("비밀번호를 입력하세요"))
            }
            password.length < 6 -> {
                Log.w(TAG, "❌ 비밀번호 길이 부족")
                Result.failure(Exception("비밀번호는 최소 6자 이상이어야 합니다"))
            }
            else -> {
                Log.d(TAG, "✅ 비밀번호 검증 통과")
                Result.success(password)
            }
        }
    }

    /**
     * 배터리 모델명 검증
     */
    fun validateModelName(modelName: String): Result<String> {
        return when {
            modelName.isBlank() -> {
                Log.w(TAG, "❌ 모델명이 비어있음")
                Result.failure(Exception("배터리 모델명을 입력하세요"))
            }
            !MODEL_NAME_PATTERN.matches(modelName) -> {
                Log.w(TAG, "❌ 모델명 길이 초과: ${modelName.length}")
                Result.failure(Exception("모델명은 1~100자여야 합니다"))
            }
            else -> {
                Log.d(TAG, "✅ 모델명 검증 통과: $modelName")
                Result.success(modelName)
            }
        }
    }

    /**
     * 배터리 용량 검증
     */
    fun validateCapacity(capacity: Int): Result<Int> {
        return when {
            capacity <= 0 -> {
                Log.w(TAG, "❌ 용량이 0 이하: $capacity")
                Result.failure(Exception("용량은 0보다 커야 합니다"))
            }
            capacity > 100000 -> {
                Log.w(TAG, "❌ 용량이 너무 높음: $capacity")
                Result.failure(Exception("용량은 최대 100000 mAh입니다"))
            }
            else -> {
                Log.d(TAG, "✅ 용량 검증 통과: ${capacity}mAh")
                Result.success(capacity)
            }
        }
    }

    /**
     * 제조년월 검증 (YYYY-MM 형식)
     */
    fun validateManufactureDate(date: String): Result<String> {
        return when {
            date.isBlank() -> {
                // 선택 항목이므로 빈 값 허용
                Log.d(TAG, "✅ 제조년월 미입력 (선택 항목)")
                Result.success("")
            }
            !MANUFACTURE_DATE_PATTERN.matches(date) -> {
                Log.w(TAG, "❌ 제조년월 형식 오류: $date")
                Result.failure(Exception("제조년월은 YYYY-MM 형식이어야 합니다 (예: 2024-05)"))
            }
            else -> {
                Log.d(TAG, "✅ 제조년월 검증 통과: $date")
                Result.success(date)
            }
        }
    }

    /**
     * 모든 배터리 정보 한 번에 검증
     */
    fun validateBatteryInfo(
        modelName: String,
        capacity: Int,
        manufactureDate: String? = null
    ): Result<Triple<String, Int, String>> {
        return try {
            val validatedName = validateModelName(modelName).getOrThrow()
            val validatedCapacity = validateCapacity(capacity).getOrThrow()
            val validatedDate = validateManufactureDate(manufactureDate ?: "").getOrThrow()

            Log.d(TAG, "✅ 모든 배터리 정보 검증 통과")
            Result.success(Triple(validatedName, validatedCapacity, validatedDate))
        } catch (e: Exception) {
            Log.e(TAG, "❌ 배터리 정보 검증 실패: ${e.message}")
            Result.failure(e)
        }
    }
}

