package com.han.battery.data.repository

import android.content.Context
import android.util.Log
import com.han.battery.data.api.ApiService
import com.han.battery.data.common.AppLogger
import com.han.battery.data.common.NetworkStateManager
import com.han.battery.data.common.OfflineOperation
import com.han.battery.data.common.OfflineOperationQueue
import com.han.battery.data.model.BatteryResponse
import com.han.battery.data.storage.UserManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 오프라인 모드를 지원하는 배터리 Repository
 * - 오프라인 시 로컬 캐시 사용
 * - 오프라인 작업 큐에 저장
 * - 온라인 복귀 시 자동 동기화
 */
class OfflineAwareBatteryRepository(
    private val apiService: ApiService,
    private val userManager: UserManager,
    private val context: Context,
    private val authRepository: AuthRepository,
    private val networkStateManager: NetworkStateManager
) {
    companion object {
        private const val TAG = "OfflineAwareBatteryRepository"
    }

    private val offlineQueue = OfflineOperationQueue()
    private val syncMutex = Mutex()

    /**
     * 배터리 등록 (오프라인 지원)
     */
    suspend fun registerBatteryOfflineAware(
        modelName: String,
        powerbankCapacityMah: Int,
        manufactureDate: String? = null
    ): Result<BatteryResponse> {
        return try {
            // 온라인 상태이면 바로 API 호출
            if (networkStateManager.isOnline.value) {
                Log.d(TAG, "온라인 상태 - API 호출")
                val result = authRepository.registerBattery(
                    modelName,
                    powerbankCapacityMah,
                    manufactureDate
                )

                if (result.isSuccess) {
                    // 성공하면 큐에서 제거 (있다면)
                    offlineQueue.remove(
                        OfflineOperation.RegisterBattery(modelName, powerbankCapacityMah, manufactureDate)
                    )
                }
                result
            } else {
                // 오프라인 상태 - 큐에 저장
                Log.d(TAG, "오프라인 상태 - 큐에 저장")
                offlineQueue.add(
                    OfflineOperation.RegisterBattery(modelName, powerbankCapacityMah, manufactureDate)
                )

                // 로컬 캐시에서 대신 응답
                Result.success(
                    BatteryResponse(
                        id = 0,  // 서버 ID는 나중에 할당됨
                        user_id = userManager.getCurrentUserId(),
                        model_name = modelName,
                        powerbank_capacity_mah = powerbankCapacityMah,
                        manufacture_date = manufactureDate
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "배터리 등록 실패: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 배터리 수정 (오프라인 지원)
     */
    suspend fun updateBatteryOfflineAware(
        batteryId: Int,
        modelName: String? = null,
        powerbankCapacityMah: Int? = null,
        manufactureDate: String? = null
    ): Result<BatteryResponse> {
        return try {
            if (networkStateManager.isOnline.value) {
                Log.d(TAG, "온라인 상태 - API 호출")
                val result = authRepository.updateBattery(
                    batteryId,
                    modelName,
                    powerbankCapacityMah,
                    manufactureDate
                )

                if (result.isSuccess) {
                    offlineQueue.remove(
                        OfflineOperation.UpdateBattery(batteryId, modelName, powerbankCapacityMah, manufactureDate)
                    )
                }
                result
            } else {
                Log.d(TAG, "오프라인 상태 - 큐에 저장")
                offlineQueue.add(
                    OfflineOperation.UpdateBattery(batteryId, modelName, powerbankCapacityMah, manufactureDate)
                )
                Result.success(
                    BatteryResponse(
                        id = batteryId,
                        user_id = userManager.getCurrentUserId(),
                        model_name = modelName ?: "",
                        powerbank_capacity_mah = powerbankCapacityMah,
                        manufacture_date = manufactureDate
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "배터리 수정 실패: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 배터리 삭제 (오프라인 지원)
     */
    suspend fun deleteBatteryOfflineAware(batteryId: Int, modelName: String): Result<String> {
        return try {
            if (networkStateManager.isOnline.value) {
                Log.d(TAG, "온라인 상태 - API 호출")
                val result = authRepository.deleteBattery(batteryId)

                if (result.isSuccess) {
                    offlineQueue.remove(OfflineOperation.DeleteBattery(batteryId, modelName))
                }
                result
            } else {
                Log.d(TAG, "오프라인 상태 - 큐에 저장")
                offlineQueue.add(OfflineOperation.DeleteBattery(batteryId, modelName))
                Result.success("오프라인 상태에서 삭제 대기 중")
            }
        } catch (e: Exception) {
            Log.e(TAG, "배터리 삭제 실패: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 오프라인 작업 동기화
     * 온라인 상태로 복귀했을 때 호출
     */
    suspend fun synchronizeOfflineOperations(): Result<Unit> {
        return syncMutex.withLock {
            try {
                val operations = offlineQueue.getAll()
                Log.d(TAG, "오프라인 작업 동기화 시작: ${operations.size}개")

                if (!networkStateManager.isOnline.value) {
                    return Result.failure(Exception("네트워크에 연결되어 있지 않습니다"))
                }

                for (operation in operations) {
                    try {
                        when (operation) {
                            is OfflineOperation.RegisterBattery -> {
                                Log.d(TAG, "동기화: 배터리 등록 - ${operation.modelName}")
                                val result = authRepository.registerBattery(
                                    operation.modelName,
                                    operation.capacity,
                                    operation.manufactureDate
                                )
                                if (result.isSuccess) {
                                    offlineQueue.remove(operation)
                                    AppLogger.info("✅ 배터리 등록 동기화 완료", TAG)
                                }
                            }
                            is OfflineOperation.UpdateBattery -> {
                                Log.d(TAG, "동기화: 배터리 수정 - ID=${operation.batteryId}")
                                val result = authRepository.updateBattery(
                                    operation.batteryId,
                                    operation.modelName,
                                    operation.capacity,
                                    operation.manufactureDate
                                )
                                if (result.isSuccess) {
                                    offlineQueue.remove(operation)
                                    AppLogger.info("✅ 배터리 수정 동기화 완료", TAG)
                                }
                            }
                            is OfflineOperation.DeleteBattery -> {
                                Log.d(TAG, "동기화: 배터리 삭제 - ID=${operation.batteryId}")
                                val result = authRepository.deleteBattery(operation.batteryId)
                                if (result.isSuccess) {
                                    offlineQueue.remove(operation)
                                    AppLogger.info("✅ 배터리 삭제 동기화 완료", TAG)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "동기화 중 오류: ${e.message}")
                        // 하나의 작업 실패가 전체 동기화를 중단하지 않도록 계속 진행
                    }
                }

                val remainingOps = offlineQueue.size()
                Log.d(TAG, "오프라인 작업 동기화 완료. 남은 작업: $remainingOps")
                AppLogger.info("오프라인 작업 동기화 완료 ($remainingOps 작업 남음)", TAG)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "오프라인 작업 동기화 실패: ${e.message}")
                AppLogger.error("오프라인 작업 동기화 실패", e, TAG)
                Result.failure(e)
            }
        }
    }

    /**
     * 오프라인 작업 큐 상태 조회
     */
    suspend fun getPendingOperations(): List<OfflineOperation> {
        return offlineQueue.getAll()
    }
}

