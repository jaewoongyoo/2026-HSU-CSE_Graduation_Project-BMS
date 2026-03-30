package com.han.battery.data.common
// UI 상태 관리를 위한 Sealed Class (Loading, Success, Error 상태 표현)

/**
 * UI 상태를 나타내는 Sealed Class입니다.
 * 로딩, 성공, 에러 상태를 표현합니다.
 */
sealed class UiState<out T> {
    /**
     * 데이터를 로드 중인 상태입니다.
     */
    object Loading : UiState<Nothing>()
    
    /**
     * 데이터 로드에 성공한 상태입니다.
     * @param data 로드된 데이터
     */
    data class Success<T>(val data: T) : UiState<T>()
    
    /**
     * 데이터 로드에 실패한 상태입니다.
     * @param exception 발생한 예외
     * @param message 사용자에게 표시할 에러 메시지
     */
    data class Error<T>(
        val exception: Exception,
        val message: String = exception.message ?: "알 수 없는 오류가 발생했습니다"
    ) : UiState<T>()
}

/**
 * 현재 로딩 중인지 확인합니다.
 */
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading

/**
 * 현재 성공 상태인지 확인합니다.
 */
fun <T> UiState<T>.isSuccess(): Boolean = this is UiState.Success

/**
 * 현재 에러 상태인지 확인합니다.
 */
fun <T> UiState<T>.isError(): Boolean = this is UiState.Error

/**
 * 성공한 경우의 데이터를 반환합니다.
 */
fun <T> UiState<T>.getOrNull(): T? = when (this) {
    is UiState.Success -> this.data
    else -> null
}

/**
 * 에러 메시지를 반환합니다.
 */
fun <T> UiState<T>.getErrorMessage(): String? = when (this) {
    is UiState.Error -> this.message
    else -> null
}

