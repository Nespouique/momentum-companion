package com.momentum.companion.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    @SerialName("accessToken") val token: String,
    val user: UserInfo,
    val expiresAt: String? = null,
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val name: String,
)
