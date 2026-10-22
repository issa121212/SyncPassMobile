package com.rojas.syncpass.core.jwt

data class SessionUser(
    val username: String = "",
    val roles: List<String> = emptyList(),
    val iss: String = "",
    val sub: String = "",
    val iat: Long = 0,
    val exp: Long = 0
) {
    // Derivado: segundos restantes. Negativo = expirado.
    val secondsRemaining: Long get() = exp - (System.currentTimeMillis() / 1000)
    val isExpired: Boolean get() = secondsRemaining <= 0
}