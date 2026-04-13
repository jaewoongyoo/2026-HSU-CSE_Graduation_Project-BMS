package com.han.battery.data.repository

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
    private val userManager: UserManager
) {
    companion object {
        private const val TAG = "AuthRepository"
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

            // ✅ 로그인 성공 - 사용자 정보만 저장 (세션은 서버에서 관리)
            userManager.setCurrentUser(username)
            AppLogger.info("로그인 성공: $username (ID: ${response.id})", TAG)
            Result.success(response)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true ->
                    "사용자명 또는 비밀번호가 잘못되었습니다."
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "서버에 접속할 수 없습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요."
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력하신 정보가 올바르지 않습니다. 사용자명과 비밀번호를 확인하세요."
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
            
            val request = SignupRequest(null, username, password)
            val response = apiService.signup(request).getOrThrow()

            // ✅ 회원가입 성공 - 자동 로그인 (사용자 정보만 저장)
            userManager.setCurrentUser(username)

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
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력하신 정보가 올바르지 않습니다. 모든 필드를 올바르게 입력하세요."
                e.message?.contains("already exists", ignoreCase = true) == true ->
                    "이미 사용 중인 사용자명입니다. 다른 사용자명을 시도하세요."
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
    suspend fun updateUser(userId: Int, name: String? = null, password: String? = null): Result<UserResponse> {
        return try {
            AppLogger.info("사용자 정보 수정 시도: ID $userId", TAG)
            val request = UpdateUserRequest(name, password)
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

    /**
     * 배터리 등록 (서버에 저장)
     */
    suspend fun registerBattery(nickname: String, capacity: Int, brand: String? = null, manufactureDate: String? = null): Result<BatteryResponse> {
        return try {
            AppLogger.info("배터리 등록 시도: $nickname ($capacity mAh)", TAG)

            val request = BatteryRegistrationRequest(
                nickname = nickname,
                capacity = capacity,
                brand = brand,
                manufacture_date = manufactureDate
            )
            val response = apiService.registerBattery(request).getOrThrow()

            AppLogger.info("배터리 등록 성공: ${response.nickname} (ID: ${response.id})", TAG)
            Result.success(response)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401", ignoreCase = true) == true ->
                    "로그인이 필요합니다."
                e.message?.contains("422", ignoreCase = true) == true ->
                    "입력 정보가 올바르지 않습니다."
                e.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "서버에 연결할 수 없습니다."
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
}
