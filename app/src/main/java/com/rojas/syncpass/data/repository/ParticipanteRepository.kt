package com.rojas.syncpass.data.repository

import com.rojas.syncpass.core.id.IdProvider
import com.rojas.syncpass.core.time.TimeProvider
import com.rojas.syncpass.data.local.dao.ParticipanteDao
import com.rojas.syncpass.data.local.entity.ParticipanteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de participantes.
 *
 * El `qrToken` se genera con [IdProvider] cuando no viene del servidor. Los
 * datos son 100 % ficticios en el seed; en producción el token debería ser
 * firmado y rotativo (deuda documentada).
 *
 * `rut` y `qrToken` son únicos en la base. Este repositorio valida antes de
 * escribir para poder devolver un error legible, y deja que la restricción de
 * SQLite actúe como red de seguridad si hay una carrera.
 */
class ParticipanteRepository(
    private val participanteDao: ParticipanteDao,
    private val proyectoRepository: ProyectoRepository,
    private val transaccionador: Transaccionador,
    private val timeProvider: TimeProvider,
    private val idProvider: IdProvider
) {

    fun observarParticipantes(): Flow<List<ParticipanteEntity>> =
        participanteDao.observarTodos()

    fun observarParticipante(id: String): Flow<ParticipanteEntity?> =
        participanteDao.observarPorId(id)

    /**
     * Búsqueda para walk-ins. Recibe el texto crudo y arma el patrón `LIKE`
     * aquí para que la UI no conozca SQL.
     */
    fun buscarPorNombre(texto: String): Flow<List<ParticipanteEntity>> =
        participanteDao.buscarPorNombre("%${texto.trim()}%")

    suspend fun obtenerPorId(id: String): ParticipanteEntity? = participanteDao.obtenerPorId(id)

    suspend fun buscarPorQrToken(qrToken: String): ParticipanteEntity? =
        participanteDao.buscarPorQrToken(qrToken.trim())

    suspend fun buscarPorRut(rut: String): ParticipanteEntity? =
        participanteDao.buscarPorRut(rut.trim())

    /**
     * Crea un participante local (walk-in) con QR generado en cliente.
     * Nace con `isSynced = false`.
     *
     * Se normaliza el RUT vacío a `null`: SQLite permite múltiples `NULL` en el
     * índice único, así que dos personas sin RUT no se bloquean entre sí.
     */
    suspend fun crearParticipante(
        nombreCompleto: String,
        rut: String? = null,
        email: String? = null,
        telefono: String? = null,
        qrToken: String? = null,
        isSynced: Boolean = false
    ): ResultadoOperacion<ParticipanteEntity> {
        val nombreLimpio = nombreCompleto.trim()
        if (nombreLimpio.isEmpty()) return fallo(ErrorOperacion.OPERACION_INVALIDA)

        val rutLimpio = rut?.trim()?.ifEmpty { null }
        val emailLimpio = email?.trim()?.ifEmpty { null }
        val telefonoLimpio = telefono?.trim()?.ifEmpty { null }
        val token = qrToken?.trim()?.ifEmpty { null } ?: idProvider.nuevoId()

        if (rutLimpio != null) {
            val existente = participanteDao.buscarPorRut(rutLimpio)
            if (existente != null) return fallo(ErrorOperacion.RUT_DUPLICADO)
        }
        if (participanteDao.buscarPorQrToken(token) != null) {
            return fallo(ErrorOperacion.QR_DUPLICADO)
        }

        val participante = ParticipanteEntity(
            id = idProvider.nuevoId(),
            rut = rutLimpio,
            nombreCompleto = nombreLimpio,
            email = emailLimpio,
            telefono = telefonoLimpio,
            qrToken = token,
            createdAt = timeProvider.ahoraMillis(),
            isSynced = isSynced,
            updatedAt = timeProvider.ahoraMillis()
        )

        return intentarDevolviendo(valorEnExito = { participante }) {
            participanteDao.insertar(participante)
        }
    }

    /**
     * Renombra un participante y reaplica el nombre en sus inscripciones
     * (write-through) dentro de la misma transacción.
     */
    suspend fun renombrarParticipante(
        id: String,
        nuevoNombre: String
    ): ResultadoOperacion<Int> {
        val nombreLimpio = nuevoNombre.trim()
        if (nombreLimpio.isEmpty()) return fallo(ErrorOperacion.OPERACION_INVALIDA)

        return intentarSuspend {
            transaccionador.enTransaccion {
                val participante = participanteDao.obtenerPorId(id)
                    ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
                val ahora = timeProvider.ahoraMillis()

                participanteDao.actualizarNombre(id, nombreLimpio, isSynced = false, updatedAt = ahora)
                proyectoRepository.propagarDatosDeParticipante(
                    participanteId = id,
                    nombreCompleto = nombreLimpio,
                    qrToken = participante.qrToken,
                    isSynced = false,
                    updatedAt = ahora
                )
            }
        }
    }

    /**
     * Rota el QR del participante y reaplica el token en sus inscripciones.
     * Un QR nuevo no puede chocar con uno existente.
     */
    suspend fun rotarQr(
        id: String,
        nuevoQrToken: String? = null
    ): ResultadoOperacion<String> {
        return intentarSuspend {
            val token = nuevoQrToken?.trim()?.ifEmpty { null } ?: idProvider.nuevoId()
            val enUso = participanteDao.buscarPorQrToken(token)
            if (enUso != null && enUso.id != id) {
                throw OperacionRechazada(ErrorOperacion.QR_DUPLICADO)
            }

            transaccionador.enTransaccion {
                val participante = participanteDao.obtenerPorId(id)
                    ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
                val ahora = timeProvider.ahoraMillis()

                participanteDao.actualizarQrToken(id, token, isSynced = false, updatedAt = ahora)
                proyectoRepository.propagarDatosDeParticipante(
                    participanteId = id,
                    nombreCompleto = participante.nombreCompleto,
                    qrToken = token,
                    isSynced = false,
                    updatedAt = ahora
                )
                token
            }
        }
    }

    /** Actualiza los datos de contacto sin tocar nombre ni QR. */
    suspend fun actualizarContacto(
        id: String,
        email: String?,
        telefono: String?
    ): ResultadoOperacion<Int> = intentarSuspend {
        val participante = participanteDao.obtenerPorId(id)
            ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        participanteDao.actualizar(
            participante.copy(
                email = email?.trim()?.ifEmpty { null },
                telefono = telefono?.trim()?.ifEmpty { null },
                isSynced = false,
                updatedAt = timeProvider.ahoraMillis()
            )
        )
        1
    }
}
