package com.rojas.syncpass.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.rojas.syncpass.data.local.projection.MetricasGlobales
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricasDao {

    /**
     * Una sola fila con los agregados globales.
     *
     * Se ancla en `inscripcion` porque una consulta con agregados y sin
     * `GROUP BY` siempre devuelve exactamente una fila, incluso con tablas
     * vacías. Room usa las tablas del `FROM` para invalidar el `Flow`, por eso
     * no se usa un `SELECT` sin `FROM`.
     *
     * `tasaAsistencia` excluye inscripciones canceladas del denominador.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM proyecto) AS totalProyectos,
            (SELECT COUNT(*) FROM evento) AS totalEventos,
            (SELECT COUNT(*) FROM participante) AS totalParticipantes,
            (SELECT COUNT(*) FROM inscripcion) AS totalInscripciones,
            (SELECT COUNT(*) FROM asistencia) AS totalAsistencias,
            (SELECT COUNT(*) FROM asistencia WHERE isSynced = 0) AS asistenciasPendientesSync,
            (
                SELECT AVG(satisfaccion) FROM asistencia
                WHERE satisfaccion IS NOT NULL
            ) AS promedioSatisfaccion,
            (
                SELECT AVG(utilidad) FROM asistencia
                WHERE utilidad IS NOT NULL
            ) AS promedioUtilidad,
            CASE
                WHEN COUNT(*) = 0 THEN 0.0
                ELSE 1.0 * SUM(CASE WHEN estado = 'ACREDITADO' THEN 1 ELSE 0 END) / COUNT(*)
            END AS tasaAsistencia
        FROM inscripcion
        WHERE estado != 'CANCELADO'
        """
    )
    fun observarMetricas(): Flow<MetricasGlobales>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM proyecto) AS totalProyectos,
            (SELECT COUNT(*) FROM evento) AS totalEventos,
            (SELECT COUNT(*) FROM participante) AS totalParticipantes,
            (SELECT COUNT(*) FROM inscripcion) AS totalInscripciones,
            (SELECT COUNT(*) FROM asistencia) AS totalAsistencias,
            (SELECT COUNT(*) FROM asistencia WHERE isSynced = 0) AS asistenciasPendientesSync,
            (
                SELECT AVG(satisfaccion) FROM asistencia
                WHERE satisfaccion IS NOT NULL
            ) AS promedioSatisfaccion,
            (
                SELECT AVG(utilidad) FROM asistencia
                WHERE utilidad IS NOT NULL
            ) AS promedioUtilidad,
            CASE
                WHEN COUNT(*) = 0 THEN 0.0
                ELSE 1.0 * SUM(CASE WHEN estado = 'ACREDITADO' THEN 1 ELSE 0 END) / COUNT(*)
            END AS tasaAsistencia
        FROM inscripcion
        WHERE estado != 'CANCELADO'
        """
    )
    suspend fun obtenerMetricas(): MetricasGlobales
}
