package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.rojas.syncpass.data.local.entity.EventoEntity
import com.rojas.syncpass.data.local.projection.EventoResumen
import com.rojas.syncpass.domain.model.EstadoEvento
import kotlinx.coroutines.flow.Flow

@Dao
interface EventoDao {

    @Query("SELECT * FROM evento ORDER BY fechaInicio")
    fun observarTodos(): Flow<List<EventoEntity>>

    @Query("SELECT * FROM evento WHERE proyectoId = :proyectoId ORDER BY fechaInicio")
    fun observarPorProyecto(proyectoId: String): Flow<List<EventoEntity>>

    @Query("SELECT * FROM evento WHERE estado = :estado ORDER BY fechaInicio")
    fun observarPorEstado(estado: EstadoEvento): Flow<List<EventoEntity>>

    /**
     * Eventos cuyo inicio cae en `[desde, hasta)`. La UI de terreno la usa con
     * el rango del día en hora local convertido a epoch millis.
     */
    @Query(
        """
        SELECT * FROM evento
        WHERE fechaInicio >= :desde AND fechaInicio < :hasta
        ORDER BY fechaInicio
        """
    )
    fun observarEntre(desde: Long, hasta: Long): Flow<List<EventoEntity>>

    @Query("SELECT * FROM evento WHERE id = :id LIMIT 1")
    fun observarPorId(id: String): Flow<EventoEntity?>

    @Query("SELECT * FROM evento WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: String): EventoEntity?

    @Query("SELECT COUNT(*) FROM evento")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(evento: EventoEntity)

    @Update
    suspend fun actualizar(evento: EventoEntity)

    @Upsert
    suspend fun upsert(evento: EventoEntity)

    /**
     * Write-through de `Proyecto.titulo` hacia la copia desnormalizada.
     * Se ejecuta junto con `ProyectoDao.actualizarTitulo` en una transacción
     * del repositorio (Fase 3) para que no queden copias inconsistentes.
     */
    @Query(
        """
        UPDATE evento
        SET tituloProyecto = :tituloProyecto,
            isSynced = :isSynced,
            updatedAt = :updatedAt
        WHERE proyectoId = :proyectoId
        """
    )
    suspend fun actualizarTituloProyecto(
        proyectoId: String,
        tituloProyecto: String,
        isSynced: Boolean,
        updatedAt: Long
    ): Int

    @Query(
        """
        SELECT
            e.id AS id,
            e.proyectoId AS proyectoId,
            e.tituloProyecto AS tituloProyecto,
            e.titulo AS titulo,
            e.lugar AS lugar,
            e.fechaInicio AS fechaInicio,
            e.fechaFin AS fechaFin,
            e.estado AS estado,
            (SELECT COUNT(*) FROM inscripcion i WHERE i.eventoId = e.id) AS totalInscritos,
            (
                SELECT COUNT(*)
                FROM inscripcion i
                INNER JOIN asistencia a ON a.inscripcionId = i.id
                WHERE i.eventoId = e.id
            ) AS totalAsistencias,
            (
                SELECT COUNT(*)
                FROM inscripcion i
                INNER JOIN asistencia a ON a.inscripcionId = i.id
                WHERE i.eventoId = e.id AND a.isSynced = 0
            ) AS pendientesSync
        FROM evento e
        ORDER BY e.fechaInicio
        """
    )
    fun observarResumenes(): Flow<List<EventoResumen>>

    @Query("DELETE FROM evento WHERE id = :id")
    suspend fun eliminarPorId(id: String): Int
}
