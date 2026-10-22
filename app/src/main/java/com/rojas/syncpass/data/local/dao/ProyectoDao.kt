package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.rojas.syncpass.data.local.entity.ProyectoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProyectoDao {

    @Query("SELECT * FROM proyecto ORDER BY titulo")
    fun observarTodos(): Flow<List<ProyectoEntity>>

    @Query("SELECT * FROM proyecto WHERE coordinadorId = :coordinadorId ORDER BY titulo")
    fun observarPorCoordinador(coordinadorId: String): Flow<List<ProyectoEntity>>

    @Query("SELECT * FROM proyecto WHERE id = :id LIMIT 1")
    fun observarPorId(id: String): Flow<ProyectoEntity?>

    @Query("SELECT * FROM proyecto WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): ProyectoEntity?

    @Query("SELECT COUNT(*) FROM proyecto")
    suspend fun contar(): Int

    /**
     * Inserción de un proyecto nuevo creado en cliente (UUID).
     * Falla con `SQLiteConstraintException` si se repite la PK o una FK.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(proyecto: ProyectoEntity)

    @Update
    suspend fun actualizar(proyecto: ProyectoEntity)

    /** Inserción/actualización idempotente para seeds y descargas de sync. */
    @Upsert
    suspend fun upsert(proyecto: ProyectoEntity)

    @Query(
        """
        UPDATE proyecto
        SET titulo = :titulo,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun actualizarTitulo(id: String, titulo: String, isSynced: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM proyecto WHERE id = :id")
    suspend fun eliminarPorId(id: String): Int
}
