package com.rojas.syncpass.domain.model

/**
 * Estado de una inscripción. `ACREDITADO` se mantiene en la misma transacción
 * que crea la asistencia; `CANCELADO` no se puede acreditar.
 */
enum class EstadoInscripcion {
    PENDIENTE,
    ACREDITADO,
    CANCELADO
}
