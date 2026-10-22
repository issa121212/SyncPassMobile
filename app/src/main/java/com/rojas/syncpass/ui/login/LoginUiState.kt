package com.rojas.syncpass.ui.login

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val loginError: String? = null,
    val authToken: String? = null, // <- agregado 02: guarda el access_token del 200
    val isSuccess: Boolean = false
) {
    val canSubmit: Boolean =
        username.isNotBlank() && password.length >= 6 && !isLoading
}