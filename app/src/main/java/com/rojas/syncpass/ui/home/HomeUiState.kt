package com.rojas.syncpass.ui.home

import com.rojas.syncpass.core.jwt.SessionUser

// data class inmutable = una sola fuente de verdad para la Home (ver 01: inmutabilidad para UDF).
data class HomeUiState(
    val user: SessionUser? = null,
    val decodeError: String? = null
)