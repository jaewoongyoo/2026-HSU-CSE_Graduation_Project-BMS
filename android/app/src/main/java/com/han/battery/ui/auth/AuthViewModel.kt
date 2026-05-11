package com.han.battery.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.han.battery.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface AuthUiEvent {
    data object SignedIn : AuthUiEvent
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<AuthUiEvent>()
    val events: SharedFlow<AuthUiEvent> = _events.asSharedFlow()

    fun login(username: String, password: String) {
        when {
            username.isBlank() -> {
                _uiState.value = AuthUiState.Error("사용자명을 입력하세요")
                return
            }
            password.isBlank() -> {
                _uiState.value = AuthUiState.Error("비밀번호를 입력하세요")
                return
            }
            username.length < 1 -> {
                _uiState.value = AuthUiState.Error("사용자명은 1자 이상이어야 합니다")
                return
            }
            password.length < 4 -> {
                _uiState.value = AuthUiState.Error("비밀번호는 4자 이상이어야 합니다")
                return
            }
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.login(username, password)
            _uiState.value = if (result.isSuccess) {
                _events.emit(AuthUiEvent.SignedIn)
                AuthUiState.Success("로그인 성공")
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "로그인 실패")
            }
        }
    }

    fun signup(username: String, password: String, confirmPassword: String) {
        when {
            username.isBlank() -> {
                _uiState.value = AuthUiState.Error("사용자명을 입력하세요")
                return
            }
            username.length < 1 -> {
                _uiState.value = AuthUiState.Error("사용자명은 1자 이상이어야 합니다")
                return
            }
            password.isBlank() -> {
                _uiState.value = AuthUiState.Error("비밀번호를 입력하세요")
                return
            }
            password.length < 4 -> {
                _uiState.value = AuthUiState.Error("비밀번호는 4자 이상이어야 합니다")
                return
            }
            password != confirmPassword -> {
                _uiState.value = AuthUiState.Error("비밀번호가 일치하지 않습니다")
                return
            }
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signup(username, password)
            _uiState.value = if (result.isSuccess) {
                _events.emit(AuthUiEvent.SignedIn)
                AuthUiState.Success("회원가입 성공")
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "회원가입 실패")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
