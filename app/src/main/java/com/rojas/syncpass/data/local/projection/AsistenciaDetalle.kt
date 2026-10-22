package com.rojas.syncpass.data.local.projection

/**
 * Fila "aplanada" de asistencia para auditoría y listados.
 *
 * Incluye los nombres desnormalizados al momento de la consulta, sin obligar a
 * la UI a resolver relaciones. Es de solo lectura: no se usa para escribir.
 */
data class AsistenciaDetalle(
    val asistenciaId: String,
    val inscripcionId: String,
    val eventoId: String,
    val eventoNombre: String,
    val participanteId: String,
    val nombreParticipante: String,
    val qrToken: String,
    val voluntarioId: String,
    val nombreVoluntario: String,
    val timestampMarcacion: Long,
    val distanciaEventoMetros: Double?,
    val satisfaccion: Int?,
    val utilidad: Int?,
    val comentarios: String?,
    val isSynced: Boolean
)
