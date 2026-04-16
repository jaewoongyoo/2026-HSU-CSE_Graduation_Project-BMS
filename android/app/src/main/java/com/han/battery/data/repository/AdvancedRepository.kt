package com.han.battery.data.repository

import android.content.Context
import android.util.Log
import com.han.battery.data.api.ApiService
import com.han.battery.data.common.AppLogger
import com.han.battery.data.common.RetryPolicy
import com.han.battery.data.common.BatteryCacheManager
import com.han.battery.data.common.UserCacheManager
import com.han.battery.data.model.*
import com.han.battery.data.storage.UserManager

/**
 * 고급 배터리 관리 Repository
 * - 재시도 로직
 * - 캐싱 지원
 * - 향상된 에러 처리
 */
class AdvancedBatteryRepository(
    private val apiService: ApiService,
    private val userManager: UserManager,
    private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "AdvancedBatteryRepository"
    }

    /**
     * 배터리 목록 조회 (캐싱 + 재시도)
     */
    suspend fun getBatteriesWithCache(): Result<List<BatteryResponse>> {
        return try {
            // 먼저 캐시 확인
            val cachedBatteries = BatteryCacheManager.getBatteryListCache()
            if (cachedBatteries != null) {
                AppLogger.info("캐시된 배터리 목록 사용: ${cachedBatteries.size}개", TAG)
                @Suppress("UNCHECKED_CAST")
                return Result.success(cachedBatteries as List<BatteryResponse>)
            }

            // 캐시가 없으면 네트워크에서 조회 (재시도 포함)
            AppLogger.info("네트워크에서 배터리 목록 조회", TAG)
            val result = RetryPolicy.retryWithExponentialBackoff(
                maxRetries = 3,
                initialDelayMs = 500,
                maxDelayMs = 3000
            ) {
                apiService.getBatteries()
            }

            result.onSuccess { batteries ->
                // 성공하면 캐시 저장
                BatteryCacheManager.cacheBatteryList(batteries)
                AppLogger.info("✅ 배터리 목록 조회 성공 및 캐시 저장", TAG)
            }

            result
        } catch (e: Exception) {
            AppLogger.error("배터리 목록 조회 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 배터리 등록 (캐시 무효화)
     */
    suspend fun registerBatteryWithCacheInvalidation(
        modelName: String,
        powerbankCapacityMah: Int,
        manufactureDate: String? = null
    ): Result<BatteryResponse> {
        return try {
            // 원래 등록 로직
            val result = authRepository.registerBattery(modelName, powerbankCapacityMah, manufactureDate)

            // 등록 성공하면 배터리 목록 캐시 무효화
            result.onSuccess {
                AppLogger.info("✅ 배터리 등록 성공, 캐시 무효화", TAG)
                BatteryCacheManager.invalidateBatteryListCache()
            }

            result
        } catch (e: Exception) {
            AppLogger.error("배터리 등록 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 배터리 수정 (캐시 무효화)
     */
    suspend fun updateBatteryWithCacheInvalidation(
        batteryId: Int,
        modelName: String? = null,
        powerbankCapacityMah: Int? = null,
        manufactureDate: String? = null
    ): Result<BatteryResponse> {
        return try {
            // 원래 수정 로직
            val result = authRepository.updateBattery(batteryId, modelName, powerbankCapacityMah, manufactureDate)

            // 수정 성공하면 캐시 무효화
            result.onSuccess {
                AppLogger.info("✅ 배터리 수정 성공, 캐시 무효화", TAG)
                BatteryCacheManager.invalidateBatteryListCache()
            }

            result
        } catch (e: Exception) {
            AppLogger.error("배터리 수정 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 배터리 삭제 (캐시 무효화)
     */
    suspend fun deleteBatteryWithCacheInvalidation(batteryId: Int): Result<String> {
        return try {
            // 원래 삭제 로직
            val result = authRepository.deleteBattery(batteryId)

            // 삭제 성공하면 캐시 무효화
            result.onSuccess {
                AppLogger.info("✅ 배터리 삭제 성공, 캐시 무효화", TAG)
                BatteryCacheManager.invalidateBatteryListCache()
            }

            result
        } catch (e: Exception) {
            AppLogger.error("배터리 삭제 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 배터리 조회 (재시도)
     */
    suspend fun getBatteryWithRetry(batteryId: Int): Result<BatteryResponse> {
        return try {
            RetryPolicy.retryWithExponentialBackoff(
                maxRetries = 3
            ) {
                apiService.getBattery(batteryId)
            }
        } catch (e: Exception) {
            AppLogger.error("배터리 조회 실패: ID $batteryId", e, TAG)
            Result.failure(e)
        }
    }
}

/**
 * 고급 사용자 관리 Repository
 * - 재시도 로직
 * - 캐싱 지원
 */
class AdvancedUserRepository(
    private val apiService: ApiService,
    private val userManager: UserManager,
    private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "AdvancedUserRepository"
    }

    /**
     * 사용자 목록 조회 (캐싱 + 재시도)
     */
    suspend fun getUsersWithCache(skip: Int = 0, limit: Int = 10): Result<List<UserResponse>> {
        return try {
            // 캐시 확인 (skip=0, limit=10 기본값일 때만)
            if (skip == 0 && limit == 10) {
                val cachedUsers = UserCacheManager.getUserListCache()
                if (cachedUsers != null) {
                    AppLogger.info("캐시된 사용자 목록 사용", TAG)
                    @Suppress("UNCHECKED_CAST")
                    return Result.success(cachedUsers as List<UserResponse>)
                }
            }

            // 네트워크에서 조회
            val result = RetryPolicy.retryWithExponentialBackoff(
                maxRetries = 3
            ) {
                apiService.getUsers(skip, limit)
            }

            result.onSuccess { users ->
                if (skip == 0 && limit == 10) {
                    UserCacheManager.cacheUserList(users)
                }
            }

            result
        } catch (e: Exception) {
            AppLogger.error("사용자 목록 조회 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 사용자 정보 조회 (캐싱 + 재시도)
     */
    suspend fun getUserWithCache(userId: Int): Result<UserResponse> {
        return try {
            // 캐시 확인
            val cachedUser = UserCacheManager.getUserProfileCache(userId)
            if (cachedUser != null) {
                @Suppress("UNCHECKED_CAST")
                return Result.success(cachedUser as UserResponse)
            }

            // 네트워크에서 조회
            val result = RetryPolicy.retryWithExponentialBackoff(
                maxRetries = 3
            ) {
                apiService.getUser(userId)
            }

            result.onSuccess { user ->
                UserCacheManager.cacheUserProfile(userId, user)
            }

            result
        } catch (e: Exception) {
            AppLogger.error("사용자 조회 실패: ID $userId", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 사용자 정보 수정 (캐시 무효화)
     */
    suspend fun updateUserWithCacheInvalidation(userId: Int, password: String? = null): Result<UserResponse> {
        return try {
            val result = authRepository.updateUser(userId, password)

            result.onSuccess {
                AppLogger.info("✅ 사용자 정보 수정 성공, 캐시 무효화", TAG)
                UserCacheManager.invalidateUserProfileCache(userId)
                UserCacheManager.invalidateUserListCache()
            }

            result
        } catch (e: Exception) {
            AppLogger.error("사용자 정보 수정 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 사용자 삭제 (캐시 무효화)
     */
    suspend fun deleteUserWithCacheInvalidation(userId: Int): Result<String> {
        return try {
            val result = authRepository.deleteUser(userId)

            result.onSuccess {
                AppLogger.info("✅ 사용자 삭제 성공, 캐시 무효화", TAG)
                UserCacheManager.invalidateUserProfileCache(userId)
                UserCacheManager.invalidateUserListCache()
            }

            result
        } catch (e: Exception) {
            AppLogger.error("사용자 삭제 실패", e, TAG)
            Result.failure(e)
        }
    }
}

