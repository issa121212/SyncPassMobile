package com.rojas.syncpass.data.repository

import com.rojas.syncpass.core.time.TimeProvider
import com.rojas.syncpass.data.local.dao.AsistenciaDao
import com.rojas.syncpass.data.local.dao.InscripcionDao
import com.rojas.syncpass.data.local.entity.AsistenciaEntity
import com.rojas.syncpass.data.local.projection.AsistenciaDetalle
import com.rojas.syncpass.data.local.relation.InscripcionConAsistencia
import com.rojas.syncpass.domain.model.EstadoInscripcion
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de asistencia.
 *
 * En esta fase cubre la **encuesta** y la **cola de sincronización**: listar
 * pendientes, editar la encuesta y marcar como subida. La acreditación
 * transaccional completa (walk-in + geofence) llega en la Fase 4 con
 * `RegistrarAsistenciaUseCase`, que reutilizará estos métodos.
 *
 * Regla clave: editar la encuesta **siempre** devuelve la asistencia a
 * `isSynced = false`, incluso si ya se había subido.
 */
class AsistenciaRepository(
    private val asistenciaDao: AsistenciaDao,
    private val inscripcionDao: InscripcionDao,
    private val timeProvider: TimeProvider
) {

    // ------------------------------------------------------------------
    // Lecturas
    // ------------------------------------------------------------------

    /** Cola de subida en orden de marcación (FIFO) para el worker de sync. */
    fun observarPendientesDeSync(): Flow<List<AsistenciaEntity>> =
        asistenciaDao.observarPendientes()

    /** Contador para el indicador offline de la UI. */
    fun observarCantidadPendientes(): Flow<Int> = asistenciaDao.observarPendientesSync()

    fun observarAsistenciasDeEvento(eventoId: String): Flow<List<AsistenciaDetalle>> =
        asistenciaDao.observarDetallePorEvento(eventoId)

    fun observarDetallePendientes(): Flow<List<AsistenciaDetalle>> =
        asistenciaDao.observarDetallePendientes()

    fun observarInscripcionesConAsistencia(
        eventoId: String
    ): Flow<List<InscripcionConAsistencia>> =
        inscripcionDao.observarConAsistenciaPorEvento(eventoId)

    suspend fun obtenerPorInscripcion(inscripcionId: String): AsistenciaEntity? =
        asistenciaDao.obtenerPorInscripcion(inscripcionId)

    // ------------------------------------------------------------------
    // Encuesta
    // ------------------------------------------------------------------

    /**
     * Guarda la encuesta de una inscripción acreditada.
     *
     * Validaciones de dominio que SQLite no puede expresar:
     * - `satisfaccion` y `utilidad` deben ser `null` o estar en `1..5`.
     * - La inscripción debe existir y tener una asistencia registrada.
     * - Una inscripción cancelada no acepta encuesta nueva.
     */
    suspend fun responderEncuesta(
        inscripcionId: String,
        satisfaccion: Int?,
        utilidad: Int?,
        comentarios: String? = null
    ): ResultadoOperacion<Int> {
        if (satisfaccion != null && satisfaccion !in ESCALA_ENCUESTA) {
            return fallo(ErrorOperacion.OPERACION_INVALIDA)
        }
        if (utilidad != null && utilidad !in ESCALA_ENCUESTA) {
            return fallo(ErrorOperacion.OPERACION_INVALIDA)
        }

        return intentarSuspend {
            if (asistenciaDao.obtenerPorInscripcion(inscripcionId) == null) {
                throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
            }
            val inscripcion = inscripcionDao.obtenerPorId(inscripcionId)
            if (inscripcion?.estado == EstadoInscripcion.CANCELADO) {
                throw OperacionRechazada(ErrorOperacion.OPERACION_INVALIDA)
            }

            // `actualizarEncuesta` fuerza isSynced = 0: editar después de
            // sincronizar vuelve a dejar la fila pendiente.
            asistenciaDao.actualizarEncuesta(
                inscripcionId = inscripcionId,
                satisfaccion = satisfaccion,
                utilidad = utilidad,
                comentarios = comentarios?.trim()?.ifEmpty { null },
                updatedAt = timeProvider.ahoraMillis()
            )
        }
    }

    /**
     * Marca una asistencia como subida. `timestampSync` es auditoría de cuándo
     * el servidor confirmó el registro.
     */
    suspend fun marcarSincronizada(
        asistenciaId: String,
        timestampSync: Long = timeProvider.ahoraMillis()
    ): ResultadoOperacion<Int> = intentarSuspend {
        val filas = asistenciaDao.marcarSincronizada(
            id = asistenciaId,
            timestampSync = timestampSync,
            updatedAt = timeProvider.ahoraMillis()
        )
        if (filas == 0) throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        filas
    }

    /** ¿La asistencia de esta inscripción está pendiente de subir? */
    suspend fun estaPendienteDeSync(inscripcionId: String): Boolean =
        asistenciaDao.obtenerPorInscripcion(inscripcionId)?.isSynced == false

    private companion object {
        val ESCALA_ENCUESTA = 1..5
    }
}
