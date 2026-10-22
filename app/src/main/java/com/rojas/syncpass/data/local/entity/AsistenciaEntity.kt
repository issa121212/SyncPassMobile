package com.rojas.syncpass.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Acreditación de una inscripción en un evento.
 *
 * - `inscripcionId` es `UNIQUE`: máximo una asistencia por inscripción (1:1).
 * - `isSynced` nace en `false` porque la marcación ocurre offline. Vuelve a
 *   `false` si el voluntario edita la encuesta después de sincronizar.
 * - Los campos de ubicación son opcionales: si no hay permiso o fix GPS, se
 *   guarda `null` y la marcación sigue siendo válida.
 * - [timestampSync] es auditoría de la última sincronización exitosa.
 */
@Entity(
    tableName = "asistencia",
    foreignKeys = [
        ForeignKey(
            entity = InscripcionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inscripcionId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["voluntarioId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["inscripcionId"], unique = true),
        Index(value = ["voluntarioId"]),
        // Cola de sincronización y auditoría de marcaciones pendientes.
        Index(value = ["isSynced", "timestampMarcacion"])
    ]
)
data class AsistenciaEntity(
    @PrimaryKey val id: String,
    val inscripcionId: String,
    val voluntarioId: String,
    val timestampMarcacion: Long,
    val latitudMarcacion: Double? = null,
    val longitudMarcacion: Double? = null,
    val precisionGpsMetros: Double? = null,
    val distanciaEventoMetros: Double? = null,
    val satisfaccion: Int? = null,
    val utilidad: Int? = null,
    val comentarios: String? = null,
    val isSynced: Boolean = false,
    val timestampSync: Long? = null,
    val updatedAt: Long
)
