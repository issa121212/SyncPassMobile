package com.rojas.syncpass.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.rojas.syncpass.core.database.AppDatabase

/**
 * Abstrae `RoomDatabase.withTransaction`.
 *
 * Los repositorios no dependen de `AppDatabase` directamente: así se pueden
 * testear con una implementación trivial y el día de mañana cambiar el motor
 * de persistencia no obliga a reescribir cada repositorio.
 *
 * No puede ser `fun interface` porque Kotlin no permite métodos abstractos con
 * parámetros de tipo en una interfaz funcional.
 */
interface Transaccionador {
    suspend fun <T> enTransaccion(bloque: suspend () -> T): T
}

/** Implementación sobre Room: `withTransaction` real. */
class RoomTransaccionador(private val database: AppDatabase) : Transaccionador {
    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T =
        database.withTransaction(bloque)
}

/**
 * Error interno para cortar una transacción con un motivo de dominio.
 * Nunca escapa del repositorio: [intentarSuspend] lo convierte en
 * `ResultadoOperacion.Fallo`.
 */
internal class OperacionRechazada(val error: ErrorOperacion) : Exception(error.mensaje)

/**
 * Ejecuta una operación de escritura y traduce las excepciones esperables a
 * [ResultadoOperacion].
 *
 * - `OperacionRechazada` → el error de dominio ya decidido por el repositorio.
 * - `SQLiteConstraintException` → conflicto de unicidad o de FK; se revisa el
 *   mensaje para distinguir RUT/QR de otras violaciones.
 * - Cualquier otra excepción se propaga: no se deben silenciar bugs.
 */
internal suspend fun <T> intentarSuspend(bloque: suspend () -> T): ResultadoOperacion<T> =
    try {
        ResultadoOperacion.Exito(bloque())
    } catch (e: OperacionRechazada) {
        ResultadoOperacion.Fallo(e.error)
    } catch (e: SQLiteConstraintException) {
        ResultadoOperacion.Fallo(traducir(e))
    }

/** Traduce el mensaje crudo de SQLite al error de dominio correspondiente. */
internal fun traducir(error: SQLiteConstraintException): ErrorOperacion {
    val mensaje = error.message.orEmpty()
    return when {
        mensaje.contains("participante.rut", ignoreCase = true) ->
            ErrorOperacion.RUT_DUPLICADO
        mensaje.contains("participante.qrToken", ignoreCase = true) ->
            ErrorOperacion.QR_DUPLICADO
        mensaje.contains("index_inscripcion_eventoId_qrToken", ignoreCase = true) ->
            ErrorOperacion.QR_YA_USADO_EN_EVENTO
        mensaje.contains("index_inscripcion_eventoId_participanteId", ignoreCase = true) ->
            ErrorOperacion.YA_INSCRITO_EN_EVENTO
        mensaje.contains("usuario.username", ignoreCase = true) ->
            ErrorOperacion.USERNAME_DUPLICADO
        mensaje.contains("FOREIGN KEY", ignoreCase = true) ->
            ErrorOperacion.NO_ENCONTRADO
        else -> ErrorOperacion.CONFLICTO_DE_UNICIDAD
    }
}

internal fun <T> fallo(error: ErrorOperacion): ResultadoOperacion<T> =
    ResultadoOperacion.Fallo(error)

/**
 * Ejecuta una operación que debe devolver un valor propio (no el resultado de
 * la escritura). Si la escritura falla, se propaga el fallo; si tiene éxito, se
 * devuelve [valorEnExito].
 *
 * Existe porque `ResultadoOperacion.Exito(unit)` inferiría `Unit` y rompería
 * los tipos de retorno declarados en los repositorios.
 */
internal suspend fun <T> intentarDevolviendo(
    valorEnExito: () -> T,
    bloque: suspend () -> Unit
): ResultadoOperacion<T> = when (val resultado = intentarSuspend(bloque)) {
    is ResultadoOperacion.Exito -> ResultadoOperacion.Exito(valorEnExito())
    is ResultadoOperacion.Fallo -> resultado
}
