package com.rojas.syncpass.domain.model

/**
 * Origen de una inscripción:
 * - [PREVIO]: descargada desde el servidor o creada por un coordinador.
 * - [WALK_IN]: creada en terreno por un voluntario sin inscripción previa.
 */
enum class TipoInscripcion {
    PREVIO,
    WALK_IN
}
