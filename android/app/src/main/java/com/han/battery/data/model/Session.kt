
package com.han.battery.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionRequest(
    val device_id: Int
)

@Serializable
data class SessionResponse(
    val session_id: Int
)