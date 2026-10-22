package com.rojas.syncpass.domain.model

/**
 * Ciclo de vida de un proyecto. Se persiste como nombre (`TEXT`) para que
 * reordenar el enum no altere los datos ya guardados.
 */
enum class EstadoProyecto {
    BORRADOR,
    ACTIVO,
    FINALIZADO
}
