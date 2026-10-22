package com.rojas.syncpass.data.local.projection

/**
 * Métricas globales del dashboard de administrador.
 *
 * `tasaAsistencia` se calcula sobre inscripciones no canceladas:
 * acreditadas / vigentes. Los promedios ignoran encuestas sin responder.
 */
data class MetricasGlobales(
    val totalProyectos: Int,
    val totalEventos: Int,
    val totalParticipantes: Int,
    val totalInscripciones: Int,
    val totalAsistencias: Int,
    val asistenciasPendientesSync: Int,
    val promedioSatisfaccion: Double?,
    val promedioUtilidad: Double?,
    val tasaAsistencia: Double
)
