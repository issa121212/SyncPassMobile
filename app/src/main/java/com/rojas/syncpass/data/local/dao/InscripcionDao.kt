package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.rojas.syncpass.data.local.entity.InscripcionEntity
import com.rojas.syncpass.data.local.relation.InscripcionConAsistencia
import com.rojas.syncpass.domain.model.EstadoInscripcion
import kotlinx.coroutines.flow.Flow

@Dao
interface InscripcionDao {

    @Query("SELECT * FROM inscripcion WHERE eventoId = :eventoId ORDER BY nombreParticipante")
    fun observarPorEvento(eventoId: String): Flow<List<InscripcionEntity>>

    @Query("SELECT * FROM inscripcion WHERE participanteId = :participanteId ORDER BY timestampInscripcion")
    fun observarPorParticipante(participanteId: String): Flow<List<InscripcionEntity>>

    @Query("SELECT * FROM inscripcion WHERE id = :id LIMIT 1")
    fun observarPorId(id: String): Flow<InscripcionEntity?>

    @Query("SELECT * FROM inscripcion WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): InscripcionEntity?

    /** Resolución del escaneo QR: una sola tabla indexada. */
    @Query("SELECT * FROM inscripcion WHERE eventoId = :eventoId AND qrToken = :qrToken LIMIT 1")
    suspend fun buscarPorEventoYQrToken(eventoId: String, qrToken: String): InscripcionEntity?

    @Query("SELECT * FROM inscripcion WHERE eventoId = :eventoId AND participanteId = :participanteId LIMIT 1")
    suspend fun buscarPorEventoYParticipante(
        eventoId: String,
        participanteId: String
    ): InscripcionEntity?

    @Query("SELECT COUNT(*) FROM inscripcion WHERE eventoId = :eventoId")
    suspend fun contarPorEvento(eventoId: String): Int

    @Query("SELECT COUNT(*) FROM inscripcion")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(inscripcion: InscripcionEntity)

    @Update
    suspend fun actualizar(inscripcion: InscripcionEntity)

    @Upsert
    suspend fun upsert(inscripcion: InscripcionEntity)

    @Query(
        """
        UPDATE inscripcion
        SET estado = :estado,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun actualizarEstado(
        id: String,
        estado: EstadoInscripcion,
        isSynced: Boolean,
        updatedAt: Long
    ): Int

    /** Write-through hacia `Inscripcion.nombreParticipante`. */
    @Query(
        """
        UPDATE inscripcion
        SET nombreParticipante = :nombreParticipante,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE participanteId = :participanteId
        """
    )
    suspend fun actualizarNombreParticipante(
        participanteId: String,
        nombreParticipante: String,
        isSynced: Boolean,
        updatedAt: Long
    ): Int

    /** Write-through hacia `Inscripcion.qrToken`. */
    @Query(
        """
        UPDATE inscripcion
        SET qrToken = :qrToken,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE participanteId = :participanteId
        """
    )
    suspend fun actualizarQrTokenPorParticipante(
        participanteId: String,
        qrToken: String,
        isSynced: Boolean,
        updatedAt: Long
    ): Int

    /** @Transaction es obligatorio en relaciones con @Relation. */
    @Transaction
    @Query("SELECT * FROM inscripcion WHERE eventoId = :eventoId ORDER BY nombreParticipante")
    fun observarConAsistenciaPorEvento(eventoId: String): Flow<List<InscripcionConAsistencia>>

    @Query("DELETE FROM inscripcion WHERE id = :id")
    suspend fun eliminarPorId(id: String): Int
}
