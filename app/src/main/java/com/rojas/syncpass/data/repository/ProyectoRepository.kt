package com.rojas.syncpass.data.repository

import com.rojas.syncpass.core.id.IdProvider
import com.rojas.syncpass.core.time.TimeProvider
import com.rojas.syncpass.data.local.dao.AsistenciaDao
import com.rojas.syncpass.data.local.dao.EventoDao
import com.rojas.syncpass.data.local.dao.InscripcionDao
import com.rojas.syncpass.data.local.dao.ParticipanteDao
import com.rojas.syncpass.data.local.dao.ProyectoDao
import com.rojas.syncpass.data.local.entity.EventoEntity
import com.rojas.syncpass.data.local.entity.InscripcionEntity
import com.rojas.syncpass.data.local.entity.ProyectoEntity
import com.rojas.syncpass.data.local.projection.EventoResumen
import com.rojas.syncpass.domain.model.EstadoEvento
import com.rojas.syncpass.domain.model.EstadoInscripcion
import com.rojas.syncpass.domain.model.EstadoProyecto
import com.rojas.syncpass.domain.model.TipoInscripcion
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio del agregado **proyecto → evento → inscripción**.
 *
 * Responsabilidades:
 * 1. CRUD local del coordinador, siempre con `isSynced = false` y `updatedAt`
 *    puesto por el repositorio (nunca por la UI).
 * 2. **Write-through** de los campos desnormalizados: `Evento.tituloProyecto`,
 *    `Inscripcion.nombreParticipante` e `Inscripcion.qrToken`. Las dos
 *    escrituras van en la misma transacción.
 * 3. Validar lo que la base de datos no puede expresar sola antes de insertar
 *    (duplicados de inscripción), devolviendo un error legible en vez de dejar
 *    que la UI reciba una excepción de SQLite.
 */
class ProyectoRepository(
    private val proyectoDao: ProyectoDao,
    private val eventoDao: EventoDao,
    private val inscripcionDao: InscripcionDao,
    private val participanteDao: ParticipanteDao,
    private val asistenciaDao: AsistenciaDao,
    private val transaccionador: Transaccionador,
    private val timeProvider: TimeProvider,
    private val idProvider: IdProvider
) {

    // ------------------------------------------------------------------
    // Lecturas reactivas
    // ------------------------------------------------------------------

    fun observarProyectos(): Flow<List<ProyectoEntity>> = proyectoDao.observarTodos()

    fun observarProyectosDeCoordinador(coordinadorId: String): Flow<List<ProyectoEntity>> =
        proyectoDao.observarPorCoordinador(coordinadorId)

    fun observarProyecto(id: String): Flow<ProyectoEntity?> = proyectoDao.observarPorId(id)

    fun observarEventosDeProyecto(proyectoId: String): Flow<List<EventoEntity>> =
        eventoDao.observarPorProyecto(proyectoId)

    fun observarEventosEntre(desde: Long, hasta: Long): Flow<List<EventoEntity>> =
        eventoDao.observarEntre(desde, hasta)

    fun observarResumenesDeEventos(): Flow<List<EventoResumen>> = eventoDao.observarResumenes()

    fun observarInscripcionesDeEvento(eventoId: String): Flow<List<InscripcionEntity>> =
        inscripcionDao.observarPorEvento(eventoId)

    /** Contador global de marcaciones pendientes de subir (indicador offline). */
    fun observarPendientesDeSync(): Flow<Int> = asistenciaDao.observarPendientesSync()

    // ------------------------------------------------------------------
    // Escrituras
    // ------------------------------------------------------------------

    /**
     * Crea un proyecto nuevo con id UUID y `isSynced = false`.
     * El título es obligatorio; el resto es opcional.
     */
    suspend fun crearProyecto(
        titulo: String,
        coordinadorId: String,
        descripcion: String? = null,
        estado: EstadoProyecto = EstadoProyecto.BORRADOR,
        fechaInicio: Long? = null,
        fechaFin: Long? = null
    ): ResultadoOperacion<ProyectoEntity> {
        val tituloLimpio = titulo.trim()
        if (tituloLimpio.isEmpty()) return fallo(ErrorOperacion.OPERACION_INVALIDA)

        val proyecto = ProyectoEntity(
            id = idProvider.nuevoId(),
            coordinadorId = coordinadorId,
            titulo = tituloLimpio,
            descripcion = descripcion?.trim()?.ifEmpty { null },
            estado = estado,
            fechaInicio = fechaInicio,
            fechaFin = fechaFin,
            isSynced = false,
            updatedAt = timeProvider.ahoraMillis()
        )

        return intentarDevolviendo(valorEnExito = { proyecto }) {
            proyectoDao.insertar(proyecto)
        }
    }

    /**
     * Renombra un proyecto y propaga el nuevo título a `Evento.tituloProyecto`
     * de todos sus eventos, en una sola transacción.
     */
    suspend fun renombrarProyecto(id: String, nuevoTitulo: String): ResultadoOperacion<Int> {
        val tituloLimpio = nuevoTitulo.trim()
        if (tituloLimpio.isEmpty()) return fallo(ErrorOperacion.OPERACION_INVALIDA)

        return intentarSuspend {
            transaccionador.enTransaccion {
                if (proyectoDao.obtenerPorId(id) == null) {
                    throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
                }
                val ahora = timeProvider.ahoraMillis()
                proyectoDao.actualizarTitulo(id, tituloLimpio, isSynced = false, updatedAt = ahora)
                eventoDao.actualizarTituloProyecto(id, tituloLimpio, isSynced = false, updatedAt = ahora)
            }
        }
    }

    /**
     * Crea un evento heredando `tituloProyecto` del proyecto padre, para que la
     * copia desnormalizada nazca consistente. Falla si el proyecto no existe.
     */
    suspend fun crearEvento(
        proyectoId: String,
        titulo: String,
        lugar: String,
        latitud: Double,
        longitud: Double,
        fechaInicio: Long,
        fechaFin: Long,
        descripcion: String? = null,
        radioToleranciaMetros: Int = 200,
        estado: EstadoEvento = EstadoEvento.PROGRAMADO
    ): ResultadoOperacion<EventoEntity> {
        val tituloLimpio = titulo.trim()
        val lugarLimpio = lugar.trim()
        if (tituloLimpio.isEmpty() || lugarLimpio.isEmpty() || fechaFin < fechaInicio) {
            return fallo(ErrorOperacion.OPERACION_INVALIDA)
        }

        return intentarSuspend {
            val proyecto = proyectoDao.obtenerPorId(proyectoId)
                ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)

            val ahora = timeProvider.ahoraMillis()
            EventoEntity(
                id = idProvider.nuevoId(),
                proyectoId = proyectoId,
                tituloProyecto = proyecto.titulo,
                titulo = tituloLimpio,
                descripcion = descripcion?.trim()?.ifEmpty { null },
                lugar = lugarLimpio,
                latitud = latitud,
                longitud = longitud,
                radioToleranciaMetros = radioToleranciaMetros,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                estado = estado,
                isSynced = false,
                updatedAt = ahora
            ).also { eventoDao.insertar(it) }
        }
    }

    /**
     * Inscribe a un participante en un evento copiando su nombre y QR.
     *
     * Valida antes de insertar porque la UI necesita un mensaje concreto
     * ("ya está inscrito") y no una `SQLiteConstraintException`.
     */
    suspend fun inscribirParticipante(
        eventoId: String,
        participanteId: String,
        tipoRegistro: TipoInscripcion = TipoInscripcion.PREVIO
    ): ResultadoOperacion<InscripcionEntity> = intentarSuspend {
        val participante = participanteDao.obtenerPorId(participanteId)
            ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        if (eventoDao.obtenerPorId(eventoId) == null) {
            throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        }
        if (inscripcionDao.buscarPorEventoYParticipante(eventoId, participanteId) != null) {
            throw OperacionRechazada(ErrorOperacion.YA_INSCRITO_EN_EVENTO)
        }
        // Mismo QR ya acreditando a otra persona en este evento.
        val qrEnUso = inscripcionDao.buscarPorEventoYQrToken(eventoId, participante.qrToken)
        if (qrEnUso != null && qrEnUso.participanteId != participanteId) {
            throw OperacionRechazada(ErrorOperacion.QR_YA_USADO_EN_EVENTO)
        }

        val ahora = timeProvider.ahoraMillis()
        InscripcionEntity(
            id = idProvider.nuevoId(),
            eventoId = eventoId,
            participanteId = participanteId,
            nombreParticipante = participante.nombreCompleto,
            qrToken = participante.qrToken,
            tipoRegistro = tipoRegistro,
            estado = EstadoInscripcion.PENDIENTE,
            timestampInscripcion = ahora,
            isSynced = false,
            updatedAt = ahora
        ).also { inscripcionDao.insertar(it) }
    }

    /**
     * Reaplica el nombre y el QR del participante sobre todas sus
     * inscripciones. Lo llama [ParticipanteRepository] tras editar al
     * participante, dentro de la misma transacción.
     */
    suspend fun propagarDatosDeParticipante(
        participanteId: String,
        nombreCompleto: String,
        qrToken: String,
        isSynced: Boolean,
        updatedAt: Long
    ): Int {
        val porNombre = inscripcionDao.actualizarNombreParticipante(
            participanteId = participanteId,
            nombreParticipante = nombreCompleto,
            isSynced = isSynced,
            updatedAt = updatedAt
        )
        inscripcionDao.actualizarQrTokenPorParticipante(
            participanteId = participanteId,
            qrToken = qrToken,
            isSynced = isSynced,
            updatedAt = updatedAt
        )
        return porNombre
    }

    /** Cambia el estado de un proyecto sin borrar historial. */
    suspend fun cambiarEstadoProyecto(
        id: String,
        estado: EstadoProyecto
    ): ResultadoOperacion<Int> = intentarSuspend {
        val proyecto = proyectoDao.obtenerPorId(id)
            ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        proyectoDao.actualizar(
            proyecto.copy(estado = estado, isSynced = false, updatedAt = timeProvider.ahoraMillis())
        )
        1
    }

    /** Cambia el estado de un evento sin borrar historial. */
    suspend fun cambiarEstadoEvento(
        id: String,
        estado: EstadoEvento
    ): ResultadoOperacion<Int> = intentarSuspend {
        val evento = eventoDao.obtenerPorId(id)
            ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        eventoDao.actualizar(
            evento.copy(estado = estado, isSynced = false, updatedAt = timeProvider.ahoraMillis())
        )
        1
    }

    /** Cancela una inscripción: conserva la fila y su asistencia para auditoría. */
    suspend fun cancelarInscripcion(inscripcionId: String): ResultadoOperacion<Int> =
        intentarSuspend {
            val filas = inscripcionDao.actualizarEstado(
                id = inscripcionId,
                estado = EstadoInscripcion.CANCELADO,
                isSynced = false,
                updatedAt = timeProvider.ahoraMillis()
            )
            if (filas == 0) throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
            filas
        }
}
