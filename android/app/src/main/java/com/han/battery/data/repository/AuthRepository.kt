package com.han.battery.data.repository

import android.os.Build
import android.provider.Settings
import android.content.Context
import java.time.Instant
import com.han.battery.data.api.ApiService
import com.han.battery.data.model.*
import com.han.battery.data.storage.UserManager
import com.han.battery.data.common.AppLogger

/**
 * 사용자 인증 및 관리를 담당하는 Repository
 * - 로그인/회원가입 API 호출 (세션 기반 인증)
 * - 사용자 정보 저장
 * - 에러 처리 및 로깅
 *
 * 🔐 인증 방식: 세션 기반 (쿠키 자동 포함)
 * - 토큰 저장 불필요
 * - HttpClient가 자동으로 쿠키 처리
 */
class AuthRepository(
    private val apiService: ApiService,
    private val userManager: UserManager,
    private val context: Context
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Android 기기 고유 ID 가져오기 (ERD의 users.phone_uid)
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: (Build.DEVICE + "_" + Build.SERIAL)
        } catch (e: Exception) {
            Build.DEVICE + "_" + Build.SERIAL
        }
    }

    /**
     * 기기 모델명 가져오기 (ERD의 users.phone_model)
     */
    private fun getPhoneModel(): String {
        return try {
            Build.MODEL ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * 사용자 로그인
     * @param username 사용자명
     * @param password 비밀번호
     * @return 로그인 결과 (성공: AuthResponse, 실패: Exception)
     */
    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            AppLogger.info("로그인 시도: $username", TAG)
            
            val request = LoginRequest(username, password)
            val response = apiService.login(request).getOrThrow()

            // ✅ 로그인 성공 - 사용자 정보 저장
            userManager.setCurrentUser(username, response.id)
            AppLogger.info("로그인 성공: $username (ID: ${response.id})", TAG)
            Result.success(response)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true ->
                    "사용자명 또는 비밀번호가 잘못되었습니다."
                e.message?.contains("400", ignoreCase = true) == true ->
                    "요청 형식이 잘못되었습니다. 입력 정보를 다시 확인하세요."
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "서버에 접속할 수 없습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력하신 정보가 올바르지 않습니다. 사용자명과 비밀번호를 확인하세요."
                e.message?.contains("500", ignoreCase = true) == true ->
                    "서버에 문제가 발생했습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("502", ignoreCase = true) == true ->
                    "서버 게이트웨이 오류입니다. 잠시 후 다시 시도하세요."
                e.message?.contains("503", ignoreCase = true) == true ->
                    "서버가 점검 중입니다. 잠시 후 다시 시도하세요."
                else -> {
                    AppLogger.error("로그인 예상치 못한 에러", e, TAG)
                    e.message ?: "로그인 실패"
                }
            }
            AppLogger.error("로그인 실패: $errorMessage", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 사용자 회원가입
     * @param username 사용자명
     * @param password 비밀번호
     * @return 회원가입 결과 (성공: AuthResponse, 실패: Exception)
     */
    suspend fun signup(username: String, password: String): Result<AuthResponse> {
        return try {
            AppLogger.info("회원가입 시도: $username", TAG)
            
            // 기기 정보 수집 (실패해도 진행)
            val phoneModel = try {
                getPhoneModel()
            } catch (e: Exception) {
                AppLogger.info("기기 모델명 수집 실패: ${e.message}", TAG)
                null
            }
            
            val phoneUid = try {
                getDeviceId()
            } catch (e: Exception) {
                AppLogger.info("기기 UID 수집 실패: ${e.message}", TAG)
                null
            }

            val request = SignupRequest(
                username = username,
                password = password,
                phone_model = phoneModel,
                phone_uid = phoneUid
            )

            AppLogger.info("회원가입 요청: username=$username, phone_model=$phoneModel, phone_uid=$phoneUid", TAG)

            val response = apiService.signup(request).getOrThrow()

            // ✅ 회원가입 성공 - 자동 로그인 (사용자 정보 저장)
            userManager.setCurrentUser(username, response.id)

            AppLogger.info("회원가입 성공: $username (ID: ${response.id})", TAG)
            Result.success(response)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "서버에 접속할 수 없습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("400", ignoreCase = true) == true ->
                    "요청 형식이 잘못되었습니다. 입력 정보를 다시 확인하세요."
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력하신 정보가 올바르지 않습니다. 모든 필드를 올바르게 입력하세요."
                e.message?.contains("409", ignoreCase = true) == true ->
                    "이미 사용 중인 사용자명입니다. 다른 사용자명을 시도하세요."
                e.message?.contains("already exists", ignoreCase = true) == true ->
                    "이미 사용 중인 사용자명입니다. 다른 사용자명을 시도하세요."
                e.message?.contains("500", ignoreCase = true) == true ->
                    "서버에 문제가 발생했습니다. 입력하신 정보를 다시 확인하고 잠시 후 다시 시도하세요."
                e.message?.contains("502", ignoreCase = true) == true ->
                    "서버 게이트웨이 오류입니다. 잠시 후 다시 시도하세요."
                e.message?.contains("503", ignoreCase = true) == true ->
                    "서버가 점검 중입니다. 잠시 후 다시 시도하세요."
                else -> {
                    AppLogger.error("회원가입 예상치 못한 에러", e, TAG)
                    e.message ?: "회원가입 실패"
                }
            }
            AppLogger.error("회원가입 실패: $errorMessage", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 모든 사용자 조회 (관리자용)
     */
    suspend fun getUsers(skip: Int = 0, limit: Int = 10): Result<List<UserResponse>> {
        return try {
            AppLogger.info("사용자 목록 조회 (skip: $skip, limit: $limit)", TAG)
            val users = apiService.getUsers(skip, limit).getOrThrow()
            AppLogger.info("사용자 목록 조회 완료: ${users.size}명", TAG)
            Result.success(users)
        } catch (e: Exception) {
            AppLogger.error("사용자 목록 조회 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 특정 사용자 정보 조회
     */
    suspend fun getUser(userId: Int): Result<UserResponse> {
        return try {
            AppLogger.info("사용자 정보 조회: ID $userId", TAG)
            val user = apiService.getUser(userId).getOrThrow()
            AppLogger.info("사용자 정보 조회 완료: ${user.username}", TAG)
            Result.success(user)
        } catch (e: Exception) {
            AppLogger.error("사용자 정보 조회 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 사용자 정보 수정
     */
    suspend fun updateUser(userId: Int, password: String? = null): Result<UserResponse> {
        return try {
            AppLogger.info("사용자 정보 수정 시도: ID $userId", TAG)
            val request = UpdateUserRequest(password = password)
            val user = apiService.updateUser(userId, request).getOrThrow()
            AppLogger.info("사용자 정보 수정 완료: ${user.username}", TAG)
            Result.success(user)
        } catch (e: Exception) {
            AppLogger.error("사용자 정보 수정 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 사용자 삭제
     */
    suspend fun deleteUser(userId: Int): Result<String> {
        return try {
            AppLogger.info("사용자 삭제 시도: ID $userId", TAG)
            val message = apiService.deleteUser(userId).getOrThrow()
            AppLogger.info("사용자 삭제 완료: ID $userId", TAG)
            Result.success(message)
        } catch (e: Exception) {
            AppLogger.error("사용자 삭제 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 로그아웃
     * - 세션 종료 (서버에서 관리)
     * - 로컬 사용자 정보 초기화
     */
    fun logout() {
        AppLogger.info("로그아웃 실행", TAG)
        userManager.logout()
    }

    /**
     * 로그인 상태 확인
     */
    fun isLoggedIn(): Boolean {
        return userManager.getCurrentUser() != null
    }

    /**
     * 현재 로그인한 사용자 정보 조회
     */
    fun getCurrentUser(): String? {
        return userManager.getCurrentUser()
    }

    suspend fun startBatterySession(
        deviceId: Int,
        powerbankId: String? = null,
        powerbankCapacityStartMah: Double? = null,
        sessionStartTs: Instant = Instant.now()
    ): Result<SessionStartResponse> {
        return try {
            require(deviceId > 0) { "유효한 배터리 ID가 필요합니다." }

            val request = SessionStartRequest(
                device_id = deviceId
            )

            AppLogger.info(
                "세션 시작 요청: device_id=$deviceId, android_api_level=${Build.VERSION.SDK_INT}, session_start_ts=${sessionStartTs}",
                TAG
            )

            val response = apiService.startSession(request).getOrThrow()
            AppLogger.info(
                "세션 시작 성공: id=${response.id}, status=${response.status}",
                TAG
            )
            Result.success(response)
        } catch (e: IllegalArgumentException) {
            AppLogger.error("세션 시작 검증 실패: ${e.message}", e, TAG)
            Result.failure(e)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true -> "세션 시작 권한이 없습니다. 다시 로그인하세요."
                e.message?.contains("404", ignoreCase = true) == true -> "세션 시작 API를 찾을 수 없습니다."
                e.message?.contains("422", ignoreCase = true) == true -> "세션 시작 요청 형식이 올바르지 않습니다."
                e.message?.contains("failed to connect", ignoreCase = true) == true -> "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("timeout", ignoreCase = true) == true -> "세션 시작 요청 시간이 초과되었습니다."
                else -> e.message ?: "세션 시작 실패"
            }
            AppLogger.error("세션 시작 실패: $errorMessage", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun finishBatterySession(
        sessionId: Int,
        powerbankCapacityStartMah: Double? = null,
        sessionStartTs: Instant? = null,
        sessionEndTs: Instant = Instant.now(),
        capacityAh: Double,
        powerbankCapacityEndMah: Double? = null,
        labelCapacityAh: Double? = null
    ): Result<SessionFinishResponse> {
        return try {
            require(sessionId > 0) { "유효한 세션 ID가 필요합니다." }
            require(capacityAh >= 0.0) { "capacity_ah는 0 이상이어야 합니다." }

            val request = SessionFinishRequest(
                android_api_level = Build.VERSION.SDK_INT,
                powerbank_capacity_start_mah = powerbankCapacityStartMah,
                session_start_ts = sessionStartTs?.toString(),
                session_end_ts = sessionEndTs.toString(),
                capacity_ah = capacityAh,
                powerbank_capacity_end_mah = powerbankCapacityEndMah,
                label_capacity_ah = labelCapacityAh
            )

            AppLogger.info(
                "세션 종료 요청: session_id=$sessionId, capacity_ah=$capacityAh, session_end_ts=$sessionEndTs",
                TAG
            )

            val response = apiService.finishSession(sessionId, request).getOrThrow()
            AppLogger.info(
                "세션 종료 성공: id=${response.id}, status=${response.status}",
                TAG
            )
            Result.success(response)
        } catch (e: IllegalArgumentException) {
            AppLogger.error("세션 종료 검증 실패: ${e.message}", e, TAG)
            Result.failure(e)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true -> "세션 종료 권한이 없습니다. 다시 로그인하세요."
                e.message?.contains("404", ignoreCase = true) == true -> "세션 종료 API 또는 세션을 찾을 수 없습니다."
                e.message?.contains("422", ignoreCase = true) == true -> "세션 종료 요청 형식이 올바르지 않습니다."
                e.message?.contains("failed to connect", ignoreCase = true) == true -> "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("timeout", ignoreCase = true) == true -> "세션 종료 요청 시간이 초과되었습니다."
                else -> e.message ?: "세션 종료 실패"
            }
            AppLogger.error("세션 종료 실패: $errorMessage", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 배터리 등록 (서버에 저장 - ERD devices 테이블)
     * @param modelName 배터리 모델명 (필수)
     * @param powerbankCapacityMah 파워뱅크 용량 (필수, mAh 단위)
     * @param manufactureDate 제조년월 (선택, YYYY-MM 형식)
     * @return 배터리 등록 결과
     */
    suspend fun registerBattery(
        modelName: String,
        powerbankCapacityMah: Int,
        manufactureDate: String? = null,
        manufacturer: String? = null
    ): Result<BatteryResponse> {
        return try {
            // 입력값 검증
            if (modelName.isBlank()) {
                throw IllegalArgumentException("모델명은 필수입니다")
            }
            if (powerbankCapacityMah <= 0) {
                throw IllegalArgumentException("용량은 0보다 커야 합니다")
            }
            if (powerbankCapacityMah > 100000) {
                throw IllegalArgumentException("용량이 너무 많습니다 (최대 100000 mAh)")
            }

            AppLogger.info("배터리 등록 시도: $modelName ($powerbankCapacityMah mAh)", TAG)

            val userId = userManager.getCurrentUserId()
            if (userId <= 0) {
                throw IllegalStateException("로그인된 사용자가 없습니다.")
            }

            val request = BatteryRegistrationRequest(
                user_id = userId.toString(),    // ✅ String으로 변환
                manufacturer = manufacturer?.trim()?.ifBlank { null },
                model_name = modelName.trim(),
                powerbank_capacity_mah = powerbankCapacityMah,
                manufacture_date = manufactureDate?.ifBlank { null }
            )

            AppLogger.info("API 요청: user_id=$userId, manufacturer=$manufacturer, model_name=$modelName, powerbank_capacity_mah=$powerbankCapacityMah, date=$manufactureDate", TAG)

            val response = apiService.registerBattery(request).getOrThrow()

            AppLogger.info("배터리 등록 성공: ${response.model_name} (ID: ${response.id}, user_id: ${response.user_id})", TAG)
            Result.success(response)
        } catch (e: IllegalArgumentException) {
            AppLogger.error("배터리 등록 입력값 검증 실패: ${e.message}", e, TAG)
            Result.failure(e)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true ->
                    "로그인이 필요합니다. 다시 로그인하세요."
                e.message?.contains("403", ignoreCase = true) == true ->
                    "배터리 등록 권한이 없습니다."
                e.message?.contains("404", ignoreCase = true) == true ->
                    "서버의 배터리 API가 준비되지 않았습니다. 백엔드 서버를 확인하세요."
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력 정보가 올바르지 않습니다. 각 항목을 다시 확인하세요."
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("500", ignoreCase = true) == true ||
                e.message?.contains("502", ignoreCase = true) == true ||
                e.message?.contains("503", ignoreCase = true) == true ->
                    "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
                else -> e.message ?: "배터리 등록 실패"
            }
            AppLogger.error("배터리 등록 실패: $errorMessage", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 배터리 목록 조회
     */
    suspend fun getBatteries(): Result<List<BatteryResponse>> {
        return try {
            AppLogger.info("배터리 목록 조회", TAG)
            val batteries = apiService.getBatteries().getOrThrow()
            AppLogger.info("배터리 목록 조회 완료: ${batteries.size}개", TAG)
            Result.success(batteries)
        } catch (e: Exception) {
            AppLogger.error("배터리 목록 조회 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 현재 로그인한 사용자 소유의 배터리만 서버에서 직접 조회합니다.
     */
    suspend fun getCurrentUserBatteries(): Result<List<BatteryResponse>> {
        return try {
            val currentUserId = userManager.getCurrentUserId()
            if (currentUserId <= 0) {
                throw IllegalStateException("로그인된 사용자가 없습니다.")
            }
            AppLogger.info("현재 사용자 배터리 조회: userId=$currentUserId", TAG)
            val result = apiService.getBatteriesForUser(currentUserId.toString()).getOrThrow()
            AppLogger.info("현재 사용자 배터리 조회 완료: ${result.size}개", TAG)
            Result.success(result)
        } catch (e: Exception) {
            AppLogger.error("현재 사용자 배터리 조회 실패", e, TAG)
            Result.failure(e)
        }
    }

    /**
     * 배터리 수정 (서버에서 수정)
     * @param batteryId 수정할 배터리 ID
     * @param modelName 배터리 모델명 (선택)
     * @param powerbankCapacityMah 파워뱅크 용량 (선택)
     * @param manufactureDate 제조년월 (선택)
     * @return 배터리 수정 결과
     */
    suspend fun updateBattery(
        batteryId: Int,
        modelName: String? = null,
        powerbankCapacityMah: Int? = null,
        manufactureDate: String? = null
    ): Result<BatteryResponse> {
        return try {
            // 수정할 항목이 없으면 실패
            if (modelName.isNullOrBlank() && powerbankCapacityMah == null && manufactureDate.isNullOrBlank()) {
                throw IllegalArgumentException("수정할 항목이 하나 이상 필요합니다")
            }

            // 용량 검증
            if (powerbankCapacityMah != null && powerbankCapacityMah <= 0) {
                throw IllegalArgumentException("용량은 0보다 커야 합니다")
            }
            if (powerbankCapacityMah != null && powerbankCapacityMah > 100000) {
                throw IllegalArgumentException("용량이 너무 많습니다 (최대 100000 mAh)")
            }

            AppLogger.info("배터리 수정 시도: ID $batteryId", TAG)

            val request = BatteryUpdateRequest(
                model_name = modelName?.trim(),
                powerbank_capacity_mah = powerbankCapacityMah,
                manufacture_date = manufactureDate?.ifBlank { null }
            )

            val response = apiService.updateBattery(batteryId, request).getOrThrow()

            AppLogger.info("✅ 배터리 수정 성공: ID $batteryId, 모델명: ${response.model_name}", TAG)
            Result.success(response)
        } catch (e: IllegalArgumentException) {
            AppLogger.error("배터리 수정 입력값 검증 실패: ${e.message}", e, TAG)
            Result.failure(e)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true ->
                    "로그인이 필요합니다. 다시 로그인하세요."
                e.message?.contains("403", ignoreCase = true) == true ->
                    "배터리 수정 권한이 없습니다."
                e.message?.contains("404", ignoreCase = true) == true ->
                    "수정할 배터리를 찾을 수 없습니다."
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력 정보가 올바르지 않습니다."
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("500", ignoreCase = true) == true ||
                e.message?.contains("502", ignoreCase = true) == true ||
                e.message?.contains("503", ignoreCase = true) == true ->
                    "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
                else -> e.message ?: "배터리 수정 실패"
            }
            AppLogger.error("❌ 배터리 수정 실패: $errorMessage (ID: $batteryId)", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * 배터리 삭제 (서버에서 삭제)
     * @param batteryId 삭제할 배터리 ID
     * @return 배터리 삭제 결과
     */
    suspend fun deleteBattery(batteryId: Int): Result<String> {
        return try {
            AppLogger.info("배터리 삭제 시도: ID $batteryId", TAG)
            val message = apiService.deleteBattery(batteryId).getOrThrow()
            AppLogger.info("✅ 배터리 삭제 성공: ID $batteryId, 서버 응답: $message", TAG)
            Result.success(message)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true ->
                    "로그인이 필요합니다. 다시 로그인하세요."
                e.message?.contains("403", ignoreCase = true) == true ->
                    "배터리 삭제 권한이 없습니다."
                e.message?.contains("404", ignoreCase = true) == true ->
                    "삭제할 배터리를 찾을 수 없습니다. 배터리 ID를 확인하세요."
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("500", ignoreCase = true) == true ||
                e.message?.contains("502", ignoreCase = true) == true ||
                e.message?.contains("503", ignoreCase = true) == true ->
                    "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
                else -> e.message ?: "배터리 삭제 실패"
            }
            AppLogger.error("❌ 배터리 삭제 실패: $errorMessage (ID: $batteryId)", e, TAG)
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun predictSoh(sessionId: Int): Result<SohPredictResponse> {
        return try {
            AppLogger.info("SOH 예측 트리거: sessionId=$sessionId", TAG)
            val response = apiService.predictSoh(sessionId).getOrThrow()
            AppLogger.info("SOH 예측 트리거 성공: $response", TAG)
            Result.success(response)
        } catch (e: Exception) {
            AppLogger.error("SOH 예측 트리거 실패: sessionId=$sessionId", e, TAG)
            Result.failure(e)
        }
    }

    suspend fun getSessionResult(sessionId: Int): Result<SessionResultResponse> {
        return try {
            AppLogger.info("세션 결과 조회: sessionId=$sessionId", TAG)
            val response = apiService.getSessionResult(sessionId).getOrThrow()
            AppLogger.info("세션 결과 조회 성공: $response", TAG)
            Result.success(response)
        } catch (e: Exception) {
            AppLogger.error("세션 결과 조회 실패: sessionId=$sessionId", e, TAG)
            Result.failure(e)
        }
    }

    suspend fun getLatestDeviceResult(deviceId: Int): Result<SessionResultResponse> {
        return try {
            AppLogger.info("기기 최신 결과 조회: deviceId=$deviceId", TAG)
            val response = apiService.getLatestDeviceResult(deviceId).getOrThrow()
            AppLogger.info("기기 최신 결과 조회 성공: $response", TAG)
            Result.success(response)
        } catch (e: Exception) {
            AppLogger.error("기기 최신 결과 조회 실패: deviceId=$deviceId", e, TAG)
            Result.failure(e)
        }
    }
}
