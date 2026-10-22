package com.rojas.syncpass

import android.app.Application
import com.rojas.syncpass.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncPassApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Scope del proceso para trabajo que no depende de una pantalla.
     *
     * `SupervisorJob` evita que un fallo en el seed cancele otros trabajos
     * futuros (por ejemplo, el arranque de la sincronización en la Fase 9).
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // El seed es idempotente y corre fuera del hilo principal: la UI de
        // login no debe esperar a que se siembren los datos ficticios.
        applicationScope.launch {
            container.seedDataInitializer.sembrar()
        }
    }
}
