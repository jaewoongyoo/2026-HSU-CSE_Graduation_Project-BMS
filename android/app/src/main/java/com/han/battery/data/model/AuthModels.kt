package com.han.battery.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class SignupRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val accessToken: String
)

@Serializable
data class SignupResponse(
    val id: String,
    val username: String
)
