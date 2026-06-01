package com.han.battery.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.han.battery.data.model.*
import io.ktor.client.engine.android.*
import com.han.battery.DevConfig
import android.util.Log

class ApiService(private val baseUrl: String = DevConfig.API_BASE_URL) {
    private val client = HttpClient(Android) {
        // 쿠키 자동 관리 (세션 기반 인증용)
        install(HttpCookies)

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO  // ALL → INFO로 변경 (프로덕션: NONE)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000  // 30초 → 10초로 단축
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
    }

    suspend fun signup(request: SignupRequest): Result<AuthResponse> = runCatching {
        Log.d("ApiService", "회원가입 API 호출: POST $baseUrl/api/v1/auth/signup")
        Log.d("ApiService", "요청 데이터: username=${request.username}, password=[${request.password.length}자], phone_model=${request.phone_model}, phone_uid=${request.phone_uid}")
        
        // JSON 직렬화 확인
        val json = Json
        val requestJson = json.encodeToString(request)
        Log.d("ApiService", "요청 JSON: $requestJson")

        val response = client.post("$baseUrl/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "회원가입 실패: ${response.status.value} - $errorBody")

            // 500 에러인 경우 추가 정보 로깅
            if (response.status.value == 500) {
                Log.e("ApiService", "❌ 500 Internal Server Error - 서버 측 오류 발생")
                Log.e("ApiService", "요청 데이터: username=${request.username}, phone_model=${request.phone_model}, phone_uid=${request.phone_uid}")
            }

            throw Exception("${response.status.value}: $errorBody")
        }
        response.body()
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse> = runCatching {
        Log.d("ApiService", "로그인 API 호출: POST $baseUrl/api/v1/auth/login")
        Log.d("ApiService", "요청 데이터: username=${request.username}")
        
        val response = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "로그인 실패: ${response.status.value} - $errorBody")

            // 500 에러인 경우 추가 정보 로깅
            if (response.status.value == 500) {
                Log.e("ApiService", "❌ 500 Internal Server Error - 서버 측 오류 발생")
                Log.e("ApiService", "요청 데이터: username=${request.username}")
            }

            throw Exception("${response.status.value}: $errorBody")
        }
        response.body()
    }

    suspend fun getUsers(skip: Int = 0, limit: Int = 10): Result<List<UserResponse>> = runCatching {
        client.get("$baseUrl/api/v1/users") {
            parameter("skip", skip)
            parameter("limit", limit)
        }.body()
    }

    suspend fun getUser(userId: Int): Result<UserResponse> = runCatching {
        client.get("$baseUrl/api/v1/users/$userId") {}.body()
    }

    suspend fun updateUser(userId: Int, request: UpdateUserRequest): Result<UserResponse> = runCatching {
        client.patch("$baseUrl/api/v1/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteUser(userId: Int): Result<String> = runCatching {
        client.delete("$baseUrl/api/v1/users/$userId") {}.body()
    }

    suspend fun startSession(request: SessionStartRequest): Result<SessionStartResponse> = runCatching {
        Log.d("ApiService", "세션 시작 API 호출: POST $baseUrl/api/v1/sessions/start")
        Log.d(
            "ApiService",
            "요청 데이터: device_id=${request.device_id}"
        )

        val response = client.post("$baseUrl/api/v1/sessions/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "세션 시작 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }

        response.body()
    }

    suspend fun finishSession(
        sessionId: Int,
        request: SessionFinishRequest
    ): Result<SessionFinishResponse> = runCatching {
        Log.d("ApiService", "세션 종료 API 호출: POST $baseUrl/api/v1/sessions/$sessionId/finish")
        Log.d(
            "ApiService",
            "요청 데이터: android_api_level=${request.android_api_level}, capacity_ah=${request.capacity_ah}, session_end_ts=${request.session_end_ts}"
        )

        val response = client.post("$baseUrl/api/v1/sessions/$sessionId/finish") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "세션 종료 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }

        response.body()
    }

    // 배터리 관련 API
    suspend fun registerBattery(request: BatteryRegistrationRequest): Result<BatteryResponse> = runCatching {
        Log.d("ApiService", "배터리 등록 API 호출: POST $baseUrl/api/v1/devices")
        Log.d("ApiService", "요청 데이터: manufacturer=${request.manufacturer}, model_name=${request.model_name}, powerbank_capacity_mah=${request.powerbank_capacity_mah}")

        // API 엔드포인트 검증
        if (!baseUrl.startsWith("http")) {
            throw IllegalArgumentException("Invalid baseUrl: $baseUrl")
        }

        // Capacity validation
        if (request.powerbank_capacity_mah == null || request.powerbank_capacity_mah <= 0) {
            throw IllegalArgumentException("용량이 설정되지 않았습니다. 1~200000 mAh 범위의 값을 입력하세요.")
        }

        val response = client.post("$baseUrl/api/v1/devices") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "배터리 등록 실패: ${response.status.value} - $errorBody")

            // 상세한 에러 메시지 생성
            val errorMessage = when (response.status.value) {
                400 -> "요청 형식이 잘못되었습니다. 모든 필수 필드를 확인하세요."
                401 -> "인증이 필요합니다. 다시 로그인하세요."
                403 -> "권한이 없습니다. 관리자에게 문의하세요."
                404 -> "서버의 배터리 등록 엔드포인트를 찾을 수 없습니다. 서버 URL을 확인하세요: $baseUrl"
                409 -> "이미 등록된 배터리입니다."
                422 -> "입력 데이터가 올바르지 않습니다. 모델명(1-100자), 용량(1-200000 mAh)을 확인하세요."
                500, 502, 503 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
                else -> "배터리 등록 실패 (${response.status.value}): $errorBody"
            }
            throw Exception(errorMessage)
        }

        val result = response.body<BatteryResponse>()
        Log.d("ApiService", "배터리 등록 성공: ID=${result.id}, model_name=${result.model_name}")
        result
    }

    suspend fun getBatteries(): Result<List<BatteryResponse>> = runCatching {
        client.get("$baseUrl/api/v1/devices/all") {}.body()
    }

    suspend fun getBattery(batteryId: Int): Result<BatteryResponse> = runCatching {
        client.get("$baseUrl/api/v1/devices/$batteryId") {}.body()
    }

    suspend fun updateBattery(batteryId: Int, request: BatteryUpdateRequest): Result<BatteryResponse> = runCatching {
        Log.d("ApiService", "배터리 수정 API 호출: PATCH $baseUrl/api/v1/devices/$batteryId")
        Log.d("ApiService", "요청 데이터: model_name=${request.model_name}, powerbank_capacity_mah=${request.powerbank_capacity_mah}")

        val response = client.patch("$baseUrl/api/v1/devices/$batteryId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "배터리 수정 실패: ${response.status.value} - $errorBody")

            val errorMessage = when (response.status.value) {
                400 -> "요청 형식이 잘못되었습니다."
                401 -> "인증이 필요합니다. 다시 로그인하세요."
                403 -> "배터리 수정 권한이 없습니다."
                404 -> "수정할 배터리를 찾을 수 없습니다."
                422 -> "입력 데이터가 올바르지 않습니다."
                500, 502, 503 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도하세요."
                else -> "배터리 수정 실패 (${response.status.value})"
            }
            throw Exception(errorMessage)
        }

        val result = response.body<BatteryResponse>()
        Log.d("ApiService", "배터리 수정 성공: ID=${result.id}, model_name=${result.model_name}")
        result
    }

    suspend fun deleteBattery(batteryId: Int): Result<String> = runCatching {
        Log.d("ApiService", "배터리 삭제 API 호출: DELETE $baseUrl/api/v1/devices/$batteryId")
        
        val response = client.delete("$baseUrl/api/v1/devices/$batteryId") {}
        
        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "배터리 삭제 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }
        
        val result = response.body<String>()
        Log.d("ApiService", "배터리 삭제 성공: ID=$batteryId, message=$result")
        result
    }

    suspend fun getCommunity(
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<CommunityCardResponse>> = runCatching {
        Log.d("ApiService", "커뮤니티 피드 조회: GET $baseUrl/api/v1/community")
        val response = client.get("$baseUrl/api/v1/community") {
            parameter("limit", limit)
            parameter("offset", offset)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "커뮤니티 피드 조회 실패: ${response.status.value} - $errorBody")
            throw Exception("서버에서 데이터를 불러오는데 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun getCommunityFiltered(
        request: CommunityFilterRequest
    ): Result<List<CommunityCardResponse>> = runCatching {
        Log.d("ApiService", "커뮤니티 필터 조회: POST $baseUrl/api/v1/community/filter")
        val response = client.post("$baseUrl/api/v1/community/filter") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "커뮤니티 필터 조회 실패: ${response.status.value} - $errorBody")
            throw Exception("필터링된 데이터를 불러오는데 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun getCommunityFilterOptions(): Result<CommunityFilterOptionsResponse> = runCatching {
        Log.d("ApiService", "커뮤니티 필터 옵션 조회: GET $baseUrl/api/v1/community/filter-options")
        val response = client.get("$baseUrl/api/v1/community/filter-options") {}
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "커뮤니티 필터 옵션 조회 실패: ${response.status.value} - $errorBody")
            throw Exception("필터 옵션을 불러오는데 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun getCommunitySohHistory(
        sharedReportId: Int
    ): Result<CommunitySohHistoryResponse> = runCatching {
        Log.d("ApiService", "커뮤니티 SOH 이력 조회: GET $baseUrl/api/v1/community/$sharedReportId/soh-history")
        val response = client.get("$baseUrl/api/v1/community/$sharedReportId/soh-history") {}
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "커뮤니티 SOH 이력 조회 실패: ${response.status.value} - $errorBody")
            throw Exception("SOH 변화 추세를 불러오는데 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun shareDevice(
        deviceId: Int,
        request: CommunityShareRequest
    ): Result<CommunityShareResponse> = runCatching {
        Log.d("ApiService", "커뮤니티 공유 등록: POST $baseUrl/api/v1/community/$deviceId/share")
        val response = client.post("$baseUrl/api/v1/community/$deviceId/share") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "커뮤니티 공유 등록 실패: ${response.status.value} - $errorBody")
            throw Exception("공유 등록에 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun updateShareStatus(
        sharedReportId: Int,
        request: CommunityShareUpdateRequest
    ): Result<CommunityShareResponse> = runCatching {
        Log.d("ApiService", "커뮤니티 공유 상태 수정: PATCH $baseUrl/api/v1/community/$sharedReportId")
        val response = client.patch("$baseUrl/api/v1/community/$sharedReportId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "공유 상태 수정 실패: ${response.status.value} - $errorBody")
            throw Exception("공유 상태 수정에 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun deleteShare(sharedReportId: Int): Result<CommunityDeleteResponse> = runCatching {
        Log.d("ApiService", "커뮤니티 공유 삭제: DELETE $baseUrl/api/v1/community/$sharedReportId")
        val response = client.delete("$baseUrl/api/v1/community/$sharedReportId") {}
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "공유 삭제 실패: ${response.status.value} - $errorBody")
            throw Exception("공유 삭제에 실패했습니다. (${response.status.value})")
        }
        response.body()
    }

    suspend fun predictSoh(sessionId: Int): Result<SohPredictResponse> = runCatching {
        Log.d("ApiService", "SOH 예측 API 호출: POST $baseUrl/api/v1/sessions/$sessionId/predict-soh")
        val response = client.post("$baseUrl/api/v1/sessions/$sessionId/predict-soh") {
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "SOH 예측 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }
        response.body()
    }

    suspend fun getSessionResult(sessionId: Int): Result<SessionResultResponse> = runCatching {
        Log.d("ApiService", "세션 결과 조회 API 호출: GET $baseUrl/api/v1/sessions/$sessionId/result")
        val response = client.get("$baseUrl/api/v1/sessions/$sessionId/result")
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "세션 결과 조회 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }
        response.body()
    }

    suspend fun getBatteriesForUser(userId: String): Result<List<BatteryResponse>> = runCatching {
        Log.d("ApiService", "사용자 기기 목록 조회 API 호출: GET $baseUrl/api/v1/devices/$userId")
        val response = client.get("$baseUrl/api/v1/devices/$userId")
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "사용자 기기 목록 조회 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }
        response.body()
    }

    suspend fun uploadRaw(sessionId: Int, request: RawUploadRequest): Result<Unit> = runCatching {
        Log.d("ApiService", "Raw 텔레메트리 업로드 API 호출: POST $baseUrl/api/v1/sessions/$sessionId/raw")
        val response = client.post("$baseUrl/api/v1/sessions/$sessionId/raw") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        Log.d("ApiService", "응답 상태: ${response.status} (${response.status.value})")
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e("ApiService", "Raw 텔레메트리 업로드 실패: ${response.status.value} - $errorBody")
            throw Exception("${response.status.value}: $errorBody")
        }
    }

    fun close() {
        client.close()
    }
}
