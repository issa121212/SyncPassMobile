package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rojas.syncpass.data.local.entity.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuario WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): UsuarioEntity?

    @Query("SELECT * FROM usuario WHERE username = :username LIMIT 1")
    suspend fun obtenerPorUsername(username: String): UsuarioEntity?

    @Query("SELECT * FROM usuario ORDER BY nombreCompleto")
    fun observarTodos(): Flow<List<UsuarioEntity>>

    @Query("SELECT COUNT(*) FROM usuario")
    suspend fun contar(): Int

    /**
     * Inserta o actualiza por PK.
     *
     * El índice UNIQUE de `username` sigue protegiendo la tabla, pero un
     * conflicto de username con otro `id` no se propaga como excepción: la fila
     * nueva no se inserta. El dominio debe validar el username antes de crear
     * usuarios locales (walk-ins/CRUD).
     */
    @Upsert
    suspend fun upsert(usuario: UsuarioEntity)

    @Query("DELETE FROM usuario WHERE id = :id")
    suspend fun eliminarPorId(id: String)
}
