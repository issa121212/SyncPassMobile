package com.rojas.syncpass.data.remote

import com.rojas.syncpass.BuildConfig
import com.rojas.syncpass.core.config.AppConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // by lazy = inicialización perezosa y thread-safe: el bloque NO se ejecuta al cargar el object,
    // sino la primera vez que alguien usa RetrofitClient.authApi. Así no pagas el costo de crear
    // OkHttp/Retrofit al abrir la app si nadie hace login, y las siguientes veces reutiliza la misma
    // instancia (singleton). Sin lazy, se crearía al inicio aunque no se use.
    val authApi: AuthApiService by lazy {
        // .apply = scope function: crea el objeto (HttpLoggingInterceptor()) y dentro del bloque
        // el receptor es ese objeto como 'this' IMPLÍCITO (no se escribe). Por eso ves solo
        // 'level = ...' y no 'this.level = ...' ni 'logging.level = ...': ambas formas valen,
        // pero Kotlin te deja omitir 'this' cuando no hay ambigüedad.
        // Equivale a: val logging = HttpLoggingInterceptor(); logging.level = ...; pero en una sola expresión
        // que retorna el mismo objeto ya configurado. Si escribieras 'this.level = ...' sería idéntico.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(AppConfig.apiBaseUrl) // <- nunca hardcodeada
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}