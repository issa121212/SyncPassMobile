package com.rojas.syncpass.core.config

import com.rojas.syncpass.BuildConfig

object AppConfig {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL.trim().let {
        if (it.endsWith("/")) it else "$it/"
    }
}