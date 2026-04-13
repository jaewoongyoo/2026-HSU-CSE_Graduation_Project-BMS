package com.han.battery.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.han.battery.data.common.AppLogger

/**
 * 현재 로그인 사용자 정보 관리 (로컬 저장소)
 * - 로그인 상태 유지용으로만 사용
 * - 사용자 인증/검증은 모두 서버에서 처리 (세션 기반)
 */
class UserManager(context: Context) {
    private val currentUserPrefs: SharedPreferences = context.getSharedPreferences(
        "current_user",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val CURRENT_USERNAME_KEY = "current_username"
        private const val TAG = "UserManager"
    }

    /**
     * 현재 로그인한 사용자 설정
     * @param username 사용자명
     */
    fun setCurrentUser(username: String) {
        currentUserPrefs.edit().apply {
            putString(CURRENT_USERNAME_KEY, username)
            apply()
        }
        AppLogger.info("현재 사용자 설정: $username", TAG)
    }

    /**
     * 현재 로그인한 사용자명 조회
     * @return 사용자명 (로그아웃 상태면 null)
     */
    fun getCurrentUser(): String? {
        return currentUserPrefs.getString(CURRENT_USERNAME_KEY, null)
    }

    /**
     * 로그아웃 (현재 사용자 초기화)
     */
    fun logout() {
        currentUserPrefs.edit().apply {
            remove(CURRENT_USERNAME_KEY)
            apply()
        }
        AppLogger.info("사용자 로그아웃", TAG)
    }

    /**
     * 로그인 상태 확인
     */
    fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }
}

