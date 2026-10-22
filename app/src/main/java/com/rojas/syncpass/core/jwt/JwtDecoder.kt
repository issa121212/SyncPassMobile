package com.rojas.syncpass.core.jwt

import android.util.Base64
import com.google.gson.Gson

// object = singleton sin estado: una sola función pura decode(), sin dependencias.
// Es object (no class) porque no necesita instancia, configuración ni ciclo de vida.
object JwtDecoder {
    private val gson = Gson()

    // Un JWT = header.payload.signature separados por '.'. Solo nos interesa [1] (payload).
    // Base64.URL_SAFE = alfabeto URL (-_ en vez de +/) y sin padding extra.
    // runCatching = si el token viene roto, devuelve failure en vez de crashear (ver 01 AuthRepository).
    fun decode(accessToken: String): Result<SessionUser> = runCatching {
        val parts = accessToken.split(".")
        require(parts.size == 3) { "Token sin formato JWT" }
        val json = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
        gson.fromJson(json, SessionUser::class.java)
    }
}