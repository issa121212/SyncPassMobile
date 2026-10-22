package com.rojas.syncpass.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rojas.syncpass.domain.model.EstadoInscripcion
import com.rojas.syncpass.domain.model.TipoInscripcion

/**
 * Vínculo entre un participante y un evento.
 *
 * `nombreParticipante` y `qrToken` están desnormalizados para que el escaneo
 * QR resuelva en una sola tabla indexada, sin JOIN con `participante`.
 * `(eventoId, participanteId)` evita inscribir dos veces a la misma persona y
 * `(eventoId, qrToken)` evita que un QR acredite a dos personas en el evento.
 *
 * El id lo genera el cliente (UUID) para poder crear walk-ins sin red.
 */
@Entity(
    tableName = "inscripcion",
    foreignKeys = [
        ForeignKey(
            entity = EventoEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventoId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ParticipanteEntity::class,
            parentColumns = ["id"],
            childColumns = ["participanteId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["eventoId", "participanteId"], unique = true),
        Index(value = ["eventoId", "qrToken"], unique = true),
        Index(value = ["participanteId"]),
        Index(value = ["eventoId", "estado"])
    ]
)
data class InscripcionEntity(
    @PrimaryKey val id: String,
    val eventoId: String,
    val participanteId: String,
    val nombreParticipante: String,
    val qrToken: String,
    val tipoRegistro: TipoInscripcion,
    val estado: EstadoInscripcion,
    val timestampInscripcion: Long,
    val isSynced: Boolean,
    val updatedAt: Long
)
