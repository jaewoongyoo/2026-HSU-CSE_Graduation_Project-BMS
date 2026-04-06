package com.han.battery.data.storage

import android.content.Context
import android.content.SharedPreferences

data class UserInfo(
    val username: String,
    val password: String
)

class UserManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("users", Context.MODE_PRIVATE)

    fun registerUser(username: String, password: String): Boolean {
        // 이미 존재하는 사용자인지 확인
        if (userExists(username)) {
            return false
        }

        // 사용자 정보 저장
        prefs.edit().apply {
            putString("${username}_password", password)
            putStringSet("registered_users", (getRegisteredUsers() + username).toSet())
            apply()
        }
        return true
    }

    fun validateUser(username: String, password: String): Boolean {
        if (!userExists(username)) {
            return false
        }

        val storedPassword = prefs.getString("${username}_password", "")
        return storedPassword == password
    }

    fun userExists(username: String): Boolean {
        return prefs.contains("${username}_password")
    }

    fun getUser(username: String): UserInfo? {
        if (!userExists(username)) {
            return null
        }

        return UserInfo(
            username = username,
            password = prefs.getString("${username}_password", "") ?: ""
        )
    }

    private fun getRegisteredUsers(): Set<String> {
        return prefs.getStringSet("registered_users", emptySet()) ?: emptySet()
    }

    fun clearAllUsers() {
        prefs.edit().clear().apply()
    }
}

