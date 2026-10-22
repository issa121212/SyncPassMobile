package com.rojas.syncpass.data.local.seed

import com.rojas.syncpass.core.database.AppDatabase
import com.rojas.syncpass.data.repository.Transaccionador

/**
 * Siembra el catálogo ficticio de la demo.
 *
 * **Idempotencia:** el seed no lleva una bandera "ya sembrado" ni borra nada.
 * Se apoya en dos propiedades:
 *
 * 1. Los IDs del [SeedData] son deterministas (`proyecto-1`, `evento-2`, ...).
 * 2. Cada escritura es un `upsert` por PK.
 *
 * Por eso, ejecutarlo N veces deja exactamente las mismas filas, y **no pisa**
 * cambios que el usuario haya hecho después (por ejemplo, editar un evento no
 * se revierte al reiniciar la app).
 *
 * Todo el seed corre en **una sola transacción**: si algo falla, no queda un
 * catálogo a medias (por ejemplo, eventos sin proyecto).
 *
 * El orden respeta las claves foráneas:
 * usuarios → proyectos → eventos → participantes → inscripciones → asistencias.
 */
class SeedDataInitializer(
    private val database: AppDatabase,
    private val transaccionador: Transaccionador
) {

    /** Ejecuta el seed. Devuelve `true` si sembró filas en esta llamada. */
    suspend fun sembrar(): Boolean {
        if (yaSembrado()) return false

        transaccionador.enTransaccion {
            val usuarioDao = database.usuarioDao()
            val proyectoDao = database.proyectoDao()
            val eventoDao = database.eventoDao()
            val participanteDao = database.participanteDao()
            val inscripcionDao = database.inscripcionDao()
            val asistenciaDao = database.asistenciaDao()

            SeedData.usuarios.forEach { usuarioDao.upsert(it) }
            SeedData.proyectos.forEach { proyectoDao.upsert(it) }
            SeedData.eventos.forEach { eventoDao.upsert(it) }
            SeedData.participantes.forEach { participanteDao.upsert(it) }
            SeedData.inscripciones.forEach { inscripcionDao.upsert(it) }
            SeedData.asistencias.forEach { asistenciaDao.upsert(it) }
        }
        return true
    }

    /**
     * Detecta si el seed ya está aplicado. Se usa un proyecto y un usuario
     * conocidos: si ambos existen, el catálogo ya fue sembrado.
     *
     * No se cuentan filas totales porque un coordinador puede haber creado
     * datos propios y ese conteo daría un falso positivo.
     */
    private suspend fun yaSembrado(): Boolean {
        val usuarioExiste = database.usuarioDao().obtenerPorId(ID_USUARIO_ANCLA) != null
        val proyectoExiste = database.proyectoDao().obtenerPorId(ID_PROYECTO_ANCLA) != null
        return usuarioExiste && proyectoExiste
    }

    private companion object {
        const val ID_USUARIO_ANCLA = "usuario-2" // coordinadora demo
        const val ID_PROYECTO_ANCLA = "proyecto-1" // alfabetización digital
    }
}
