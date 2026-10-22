package com.rojas.syncpass.data.remote

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long = 3600,
    val user: UserDto? = null
)

data class UserDto(
    val id: Int? = null,
    val username: String? = null,
    val email: String? = null
)