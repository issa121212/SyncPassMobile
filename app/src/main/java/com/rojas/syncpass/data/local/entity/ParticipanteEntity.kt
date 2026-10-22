package com.rojas.syncpass.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persona que se inscribe a eventos.
 *
 * [rut] es opcional y único cuando existe: SQLite permite múltiples `NULL` en
 * un índice `UNIQUE`, así que los walk-ins sin RUT no chocan entre sí.
 * [qrToken] es único globalmente y es la llave que escanea el voluntario.
 */
@Entity(
    tableName = "participante",
    indices = [
        Index(value = ["rut"], unique = true),
        Index(value = ["qrToken"], unique = true),
        Index(value = ["nombreCompleto"])
    ]
)
data class ParticipanteEntity(
    @PrimaryKey val id: String,
    val rut: String? = null,
    val nombreCompleto: String,
    val email: String? = null,
    val telefono: String? = null,
    val qrToken: String,
    val createdAt: Long,
    val isSynced: Boolean,
    val updatedAt: Long
)
