package com.rojas.syncpass.data.local.projection

import com.rojas.syncpass.domain.model.EstadoEvento

/**
 * Vista agregada de un evento para listados de coordinador/administrador.
 *
 * Evita cargar todas las inscripciones y asistencias en memoria: SQLite calcula
 * los contadores. `pendientesSync` cuenta asistencias del evento que aún no se
 * suben, útil para el indicador offline.
 */
data class EventoResumen(
    val id: String,
    val proyectoId: String,
    val tituloProyecto: String,
    val titulo: String,
    val lugar: String,
    val fechaInicio: Long,
    val fechaFin: Long,
    val estado: EstadoEvento,
    val totalInscritos: Int,
    val totalAsistencias: Int,
    val pendientesSync: Int
)
