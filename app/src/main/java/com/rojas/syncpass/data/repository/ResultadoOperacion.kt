package com.rojas.syncpass.data.repository

/**
 * Fallos esperables del dominio, expresados como un resultado en vez de
 * excepciones. Los repositorios devuelven `ResultadoOperacion` cuando el
 * llamador debe reaccionar al motivo (validación, conflicto, FK).
 */
sealed interface ResultadoOperacion<out T> {

    data class Exito<T>(val valor: T) : ResultadoOperacion<T>

    data class Fallo(val error: ErrorOperacion) : ResultadoOperacion<Nothing>
}

/**
 * Catálogo de errores de operación. La UI traduce [mensaje] a un texto legible;
 * el tipo permite decidir el flujo (por ejemplo, pedir otro username).
 */
enum class ErrorOperacion(val mensaje: String) {
    USERNAME_DUPLICADO("Ese nombre de usuario ya existe."),
    RUT_DUPLICADO("Ese RUT ya está registrado."),
    QR_DUPLICADO("Ese código QR ya está asignado."),
    YA_INSCRITO_EN_EVENTO("El participante ya está inscrito en este evento."),
    QR_YA_USADO_EN_EVENTO("El QR ya acredita a otra persona en este evento."),
    NO_ENCONTRADO("No se encontró el registro solicitado."),
    CONFLICTO_DE_UNICIDAD("El registro choca con un dato existente."),
    OPERACION_INVALIDA("La operación no es válida con el estado actual.")
}

inline fun <T, R> ResultadoOperacion<T>.map(transformar: (T) -> R): ResultadoOperacion<R> =
    when (this) {
        is ResultadoOperacion.Exito -> ResultadoOperacion.Exito(transformar(valor))
        is ResultadoOperacion.Fallo -> this
    }
