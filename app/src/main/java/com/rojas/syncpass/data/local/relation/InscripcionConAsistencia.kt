package com.rojas.syncpass.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.rojas.syncpass.data.local.entity.AsistenciaEntity
import com.rojas.syncpass.data.local.entity.InscripcionEntity

/**
 * Inscripción con su asistencia opcional (relación 1:1).
 *
 * La FK `UNIQUE(inscripcionId)` garantiza que la lista de asistencias tenga
 * como máximo un elemento; por eso se expone como `AsistenciaEntity?` y no
 * como lista.
 */
data class InscripcionConAsistencia(
    @Embedded val inscripcion: InscripcionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "inscripcionId"
    )
    val asistencia: AsistenciaEntity?
)
