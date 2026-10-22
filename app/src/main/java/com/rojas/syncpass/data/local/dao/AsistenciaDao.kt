package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.rojas.syncpass.data.local.entity.AsistenciaEntity
import com.rojas.syncpass.data.local.projection.AsistenciaDetalle
import kotlinx.coroutines.flow.Flow

@Dao
interface AsistenciaDao {

    @Query("SELECT * FROM asistencia WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): AsistenciaEntity?

    @Query("SELECT * FROM asistencia WHERE inscripcionId = :inscripcionId LIMIT 1")
    suspend fun obtenerPorInscripcion(inscripcionId: String): AsistenciaEntity?

    @Query("SELECT * FROM asistencia WHERE inscripcionId = :inscripcionId LIMIT 1")
    fun observarPorInscripcion(inscripcionId: String): Flow<AsistenciaEntity?>

    @Query("SELECT * FROM asistencia WHERE isSynced = 0 ORDER BY timestampMarcacion")
    fun observarPendientes(): Flow<List<AsistenciaEntity>>

    @Query("SELECT COUNT(*) FROM asistencia WHERE isSynced = 0")
    fun observarPendientesSync(): Flow<Int>

    @Query("SELECT COUNT(*) FROM asistencia WHERE isSynced = 0")
    suspend fun contarPendientes(): Int

    @Query("SELECT COUNT(*) FROM asistencia")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(asistencia: AsistenciaEntity)

    @Update
    suspend fun actualizar(asistencia: AsistenciaEntity)

    @Upsert
    suspend fun upsert(asistencia: AsistenciaEntity)

    /**
     * Edición de encuesta. Fuerza `isSynced = 0` siempre: si la asistencia ya
     * se había sincronizado, debe volver a la cola de subida.
     */
    @Query(
        """
        UPDATE asistencia
        SET satisfaccion = :satisfaccion,
            utilidad = :utilidad,
            comentarios = :comentarios,
            isSynced = 0,
            updatedAt = :updatedAt
        WHERE inscripcionId = :inscripcionId
        """
    )
    suspend fun actualizarEncuesta(
        inscripcionId: String,
        satisfaccion: Int?,
        utilidad: Int?,
        comentarios: String?,
        updatedAt: Long
    ): Int

    /** Marca una asistencia como subida y registra la auditoría de sync. */
    @Query(
        """
        UPDATE asistencia
        SET isSynced = 1,
            timestampSync = :timestampSync,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun marcarSincronizada(id: String, timestampSync: Long, updatedAt: Long): Int

    @Query(
        """
        SELECT
            a.id AS asistenciaId,
            a.inscripcionId AS inscripcionId,
            i.eventoId AS eventoId,
            e.titulo AS eventoNombre,
            i.participanteId AS participanteId,
            i.nombreParticipante AS nombreParticipante,
            i.qrToken AS qrToken,
            a.voluntarioId AS voluntarioId,
            u.nombreCompleto AS nombreVoluntario,
            a.timestampMarcacion AS timestampMarcacion,
            a.distanciaEventoMetros AS distanciaEventoMetros,
            a.satisfaccion AS satisfaccion,
            a.utilidad AS utilidad,
            a.comentarios AS comentarios,
            a.isSynced AS isSynced
        FROM asistencia a
        INNER JOIN inscripcion i ON i.id = a.inscripcionId
        INNER JOIN evento e ON e.id = i.eventoId
        INNER JOIN usuario u ON u.id = a.voluntarioId
        WHERE i.eventoId = :eventoId
        ORDER BY a.timestampMarcacion DESC
        """
    )
    fun observarDetallePorEvento(eventoId: String): Flow<List<AsistenciaDetalle>>

    @Query(
        """
        SELECT
            a.id AS asistenciaId,
            a.inscripcionId AS inscripcionId,
            i.eventoId AS eventoId,
            e.titulo AS eventoNombre,
            i.participanteId AS participanteId,
            i.nombreParticipante AS nombreParticipante,
            i.qrToken AS qrToken,
            a.voluntarioId AS voluntarioId,
            u.nombreCompleto AS nombreVoluntario,
            a.timestampMarcacion AS timestampMarcacion,
            a.distanciaEventoMetros AS distanciaEventoMetros,
            a.satisfaccion AS satisfaccion,
            a.utilidad AS utilidad,
            a.comentarios AS comentarios,
            a.isSynced AS isSynced
        FROM asistencia a
        INNER JOIN inscripcion i ON i.id = a.inscripcionId
        INNER JOIN evento e ON e.id = i.eventoId
        INNER JOIN usuario u ON u.id = a.voluntarioId
        WHERE a.isSynced = 0
        ORDER BY a.timestampMarcacion
        """
    )
    fun observarDetallePendientes(): Flow<List<AsistenciaDetalle>>
}
