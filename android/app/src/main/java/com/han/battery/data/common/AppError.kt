package com.han.battery.data.common

import androidx.annotation.StringRes

/**
 * 앱 전체의 에러를 처리하기 위한 sealed class
 * 각 에러 타입별로 구분되어 UI에서 다양하게 처리할 수 있음
 */
sealed class AppError(override val message: String) : Exception(message) {
    // 네트워크 에러
    sealed class NetworkError(message: String) : AppError(message) {
        object NoConnection : NetworkError("서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요.")
        object Timeout : NetworkError("요청 시간이 초과되었습니다. 잠시 후 다시 시도하세요.")
        object ConnectionRefused : NetworkError("서버에 접속할 수 없습니다. 잠시 후 다시 시도하세요.")
        data class HttpError(val statusCode: Int, override val message: String) : NetworkError(message)
    }

    // 인증 에러
    sealed class AuthError(message: String) : AppError(message) {
        object InvalidCredentials : AuthError("사용자명 또는 비밀번호가 잘못되었습니다.")
        object Unauthorized : AuthError("인증이 필요합니다. 다시 로그인하세요.")
        object Forbidden : AuthError("권한이 없습니다.")
        object UserNotFound : AuthError("사용자를 찾을 수 없습니다.")
        object UserAlreadyExists : AuthError("이미 사용 중인 사용자명입니다. 다른 사용자명을 시도하세요.")
    }

    // 배터리 에러
    sealed class BatteryError(message: String) : AppError(message) {
        object NotFound : BatteryError("배터리를 찾을 수 없습니다.")
        object InvalidCapacity : BatteryError("배터리 용량이 올바르지 않습니다. (1~100000 mAh)")
        object InvalidModelName : BatteryError("배터리 모델명이 올바르지 않습니다. (1~100자)")
        object AlreadyExists : BatteryError("이미 등록된 배터리입니다.")
        object NotAuthorized : BatteryError("이 배터리를 수정/삭제할 권한이 없습니다.")
    }

    // 입력값 검증 에러
    sealed class ValidationError(message: String) : AppError(message) {
        object EmptyUsername : ValidationError("사용자명을 입력하세요.")
        object EmptyPassword : ValidationError("비밀번호를 입력하세요.")
        object WeakPassword : ValidationError("비밀번호는 최소 6자 이상이어야 합니다.")
        object EmptyModelName : ValidationError("배터리 모델명을 입력하세요.")
        object InvalidManufactureDate : ValidationError("제조년월 형식이 올바르지 않습니다. (YYYY-MM)")
        data class Custom(override val message: String) : ValidationError(message)
    }

    // 서버 에러
    sealed class ServerError(message: String) : AppError(message) {
        object InternalError : ServerError("서버 내부 오류가 발생했습니다.")
        object BadGateway : ServerError("서버 게이트웨이 오류입니다.")
        object ServiceUnavailable : ServerError("서버가 점검 중입니다.")
        data class UnknownError(override val message: String) : ServerError(message)
    }

    // 기타 에러
    data class UnexpectedError(override val message: String) : AppError(message)

    companion object {
        /**
         * HTTP 상태 코드로부터 AppError를 생성
         */
        fun fromHttpStatusCode(statusCode: Int, message: String): AppError {
            return when (statusCode) {
                400 -> NetworkError.HttpError(statusCode, "요청 형식이 잘못되었습니다.")
                401 -> AuthError.Unauthorized
                403 -> AuthError.Forbidden
                404 -> BatteryError.NotFound
                409 -> BatteryError.AlreadyExists
                422 -> ValidationError.Custom(message)
                500 -> ServerError.InternalError
                502 -> ServerError.BadGateway
                503 -> ServerError.ServiceUnavailable
                else -> ServerError.UnknownError("오류가 발생했습니다. (HTTP $statusCode)")
            }
        }

        /**
         * 예외로부터 AppError를 생성
         */
        fun fromException(exception: Exception): AppError {
            return when {
                exception is AppError -> exception
                exception.message?.contains("failed to connect", ignoreCase = true) == true ->
                    NetworkError.NoConnection
                exception.message?.contains("Connection refused", ignoreCase = true) == true ->
                    NetworkError.ConnectionRefused
                exception.message?.contains("timeout", ignoreCase = true) == true ->
                    NetworkError.Timeout
                else -> UnexpectedError(exception.message ?: "알 수 없는 오류")
            }
        }

        /**
         * 사용자 친화적인 메시지로 변환
         */
        fun getUserMessage(error: AppError): String {
            return error.message
        }
    }
}

