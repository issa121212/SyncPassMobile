package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.rojas.syncpass.data.local.entity.ParticipanteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipanteDao {

    @Query("SELECT * FROM participante ORDER BY nombreCompleto")
    fun observarTodos(): Flow<List<ParticipanteEntity>>

    @Query("SELECT * FROM participante WHERE id = :id LIMIT 1")
    fun observarPorId(id: String): Flow<ParticipanteEntity?>

    @Query("SELECT * FROM participante WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): ParticipanteEntity?

    /** Resolución del QR escaneado. `qrToken` es único global. */
    @Query("SELECT * FROM participante WHERE qrToken = :qrToken LIMIT 1")
    suspend fun buscarPorQrToken(qrToken: String): ParticipanteEntity?

    @Query("SELECT * FROM participante WHERE rut = :rut LIMIT 1")
    suspend fun buscarPorRut(rut: String): ParticipanteEntity?

    /**
     * Búsqueda para walk-ins. Recibe el patrón ya armado (por ejemplo
     * `"%rojas%"`) para evitar concatenaciones ambiguas en SQL.
     */
    @Query("SELECT * FROM participante WHERE nombreCompleto LIKE :patron ORDER BY nombreCompleto")
    fun buscarPorNombre(patron: String): Flow<List<ParticipanteEntity>>

    @Query("SELECT COUNT(*) FROM participante")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(participante: ParticipanteEntity)

    @Update
    suspend fun actualizar(participante: ParticipanteEntity)

    @Upsert
    suspend fun upsert(participante: ParticipanteEntity)

    /** Write-through hacia `Inscripcion.nombreParticipante`. */
    @Query(
        """
        UPDATE participante
        SET nombreCompleto = :nombreCompleto,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun actualizarNombre(
        id: String,
        nombreCompleto: String,
        isSynced: Boolean,
        updatedAt: Long
    ): Int

    /** Write-through hacia `Inscripcion.qrToken`. */
    @Query(
        """
        UPDATE participante
        SET qrToken = :qrToken,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun actualizarQrToken(
        id: String,
        qrToken: String,
        isSynced: Boolean,
        updatedAt: Long
    ): Int

    @Query("DELETE FROM participante WHERE id = :id")
    suspend fun eliminarPorId(id: String): Int
}
