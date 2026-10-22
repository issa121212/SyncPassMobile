package com.rojas.syncpass.domain.model

/**
 * Estado operativo de un evento/taller. `CANCELADO` conserva el historial de
 * inscripciones y asistencias; no se borra la fila.
 */
enum class EstadoEvento {
    PROGRAMADO,
    EN_CURSO,
    FINALIZADO,
    CANCELADO
}
