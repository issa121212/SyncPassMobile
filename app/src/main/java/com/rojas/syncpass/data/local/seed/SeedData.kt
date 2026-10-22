package com.rojas.syncpass.data.local.seed

import com.rojas.syncpass.data.local.entity.AsistenciaEntity
import com.rojas.syncpass.data.local.entity.EventoEntity
import com.rojas.syncpass.data.local.entity.InscripcionEntity
import com.rojas.syncpass.data.local.entity.ParticipanteEntity
import com.rojas.syncpass.data.local.entity.ProyectoEntity
import com.rojas.syncpass.data.local.entity.UsuarioEntity
import com.rojas.syncpass.domain.model.EstadoEvento
import com.rojas.syncpass.domain.model.EstadoInscripcion
import com.rojas.syncpass.domain.model.EstadoProyecto
import com.rojas.syncpass.domain.model.RolUsuario
import com.rojas.syncpass.domain.model.TipoInscripcion

/**
 * Conjunto completo de datos ficticios de la demo.
 *
 * **Todos los datos son inventados.** Los RUT son ficticios y válidos solo como
 * formato; los teléfonos usan el rango de ejemplo; los correos terminan en
 * `@demo.cl`. No debe entrar información real aquí.
 *
 * Los IDs son **deterministas** (`proyecto-1`, `evento-2`, ...) para que el seed
 * sea idempotente: volver a ejecutarlo produce exactamente las mismas claves y
 * el `upsert` no duplica nada.
 *
 * `isSynced = true` en todo el catálogo porque simula datos ya descargados del
 * servidor. Solo las asistencias nacen en `false`, que es el estado real de una
 * marcación en terreno (y una de ellas se deja pendiente a propósito para
 * poder demostrar el flujo de sincronización en la Fase 9).
 */
object SeedData {

    /** Marca temporal fija de referencia (2026-10-22T12:00:00Z) en epoch millis. */
    const val TIMESTAMP_BASE = 1_792_747_200_000L

    private const val HORA = 3_600_000L
    private const val DIA = 24 * HORA

    // Coordenadas ficticias dentro de Santiago de Chile.
    private const val LAT_BASE = -33.4372
    private const val LON_BASE = -70.6506

    // ------------------------------------------------------------------
    // Usuarios
    // ------------------------------------------------------------------

    /** Contraseñas ficticias: la API real valida contra el backend. */
    val usuarios: List<UsuarioEntity> = listOf(
        UsuarioEntity(
            id = "usuario-1",
            username = "admin.demo",
            nombreCompleto = "Amanda Demo Administradora",
            email = "admin@demo.cl",
            rol = RolUsuario.ADMINISTRADOR,
            activo = true,
            createdAt = TIMESTAMP_BASE - 30 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 30 * DIA
        ),
        UsuarioEntity(
            id = "usuario-2",
            username = "coord.demo",
            nombreCompleto = "Carla Demo Coordinadora",
            email = "coord@demo.cl",
            rol = RolUsuario.COORDINADOR,
            activo = true,
            createdAt = TIMESTAMP_BASE - 28 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 28 * DIA
        ),
        UsuarioEntity(
            id = "usuario-3",
            username = "coord.norte",
            nombreCompleto = "Nicolás Demo Coordinador",
            email = "coord.norte@demo.cl",
            rol = RolUsuario.COORDINADOR,
            activo = true,
            createdAt = TIMESTAMP_BASE - 20 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 20 * DIA
        ),
        UsuarioEntity(
            id = "usuario-4",
            username = "voluntario.demo",
            nombreCompleto = "Valentina Demo Voluntaria",
            email = "voluntaria@demo.cl",
            rol = RolUsuario.VOLUNTARIO,
            activo = true,
            createdAt = TIMESTAMP_BASE - 15 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 15 * DIA
        ),
        UsuarioEntity(
            id = "usuario-5",
            username = "voluntario.dos",
            nombreCompleto = "Vicente Demo Voluntario",
            email = "voluntario2@demo.cl",
            rol = RolUsuario.VOLUNTARIO,
            activo = true,
            createdAt = TIMESTAMP_BASE - 10 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 10 * DIA
        )
    )

    // ------------------------------------------------------------------
    // Proyectos
    // ------------------------------------------------------------------

    val proyectos: List<ProyectoEntity> = listOf(
        ProyectoEntity(
            id = "proyecto-1",
            coordinadorId = "usuario-2",
            titulo = "Alfabetización Digital Demo",
            descripcion = "Talleres ficticios de computación básica para vecinos.",
            estado = EstadoProyecto.ACTIVO,
            fechaInicio = TIMESTAMP_BASE - 10 * DIA,
            fechaFin = TIMESTAMP_BASE + 30 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 10 * DIA
        ),
        ProyectoEntity(
            id = "proyecto-2",
            coordinadorId = "usuario-3",
            titulo = "Huerto Comunitario Demo",
            descripcion = "Jornadas ficticias de huerto urbano y compostaje.",
            estado = EstadoProyecto.ACTIVO,
            fechaInicio = TIMESTAMP_BASE - 5 * DIA,
            fechaFin = TIMESTAMP_BASE + 45 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 5 * DIA
        ),
        ProyectoEntity(
            id = "proyecto-3",
            coordinadorId = "usuario-2",
            titulo = "Salud Preventiva Demo",
            descripcion = "Operativos ficticios de toma de presión y glucosa.",
            estado = EstadoProyecto.BORRADOR,
            fechaInicio = TIMESTAMP_BASE + 7 * DIA,
            fechaFin = TIMESTAMP_BASE + 60 * DIA,
            isSynced = true,
            updatedAt = TIMESTAMP_BASE - 2 * DIA
        )
    )

    // ------------------------------------------------------------------
    // Eventos
    // ------------------------------------------------------------------

    val eventos: List<EventoEntity> = listOf(
        evento(
            id = "evento-1",
            proyectoId = "proyecto-1",
            tituloProyecto = "Alfabetización Digital Demo",
            titulo = "Taller de Correo Electrónico",
            descripcion = "Crear cuenta, enviar y adjuntar archivos.",
            lugar = "Sede Vecinal Demo Norte",
            latitud = LAT_BASE,
            longitud = LON_BASE,
            inicio = TIMESTAMP_BASE,
            duracion = 2 * HORA,
            estado = EstadoEvento.FINALIZADO
        ),
        evento(
            id = "evento-2",
            proyectoId = "proyecto-1",
            tituloProyecto = "Alfabetización Digital Demo",
            titulo = "Taller de Trámites en Línea",
            descripcion = "Uso de portales públicos ficticios.",
            lugar = "Biblioteca Demo Central",
            latitud = LAT_BASE + 0.008,
            longitud = LON_BASE - 0.004,
            inicio = TIMESTAMP_BASE + DIA,
            duracion = 3 * HORA,
            estado = EstadoEvento.PROGRAMADO
        ),
        evento(
            id = "evento-3",
            proyectoId = "proyecto-1",
            tituloProyecto = "Alfabetización Digital Demo",
            titulo = "Taller de Videollamadas",
            descripcion = "Configurar cámara y micrófono.",
            lugar = "Sede Vecinal Demo Norte",
            latitud = LAT_BASE,
            longitud = LON_BASE,
            inicio = TIMESTAMP_BASE + 8 * DIA,
            duracion = 2 * HORA,
            estado = EstadoEvento.PROGRAMADO
        ),
        evento(
            id = "evento-4",
            proyectoId = "proyecto-2",
            tituloProyecto = "Huerto Comunitario Demo",
            titulo = "Jornada de Compostaje",
            descripcion = "Armado de compostera comunitaria.",
            lugar = "Plaza Demo Sur",
            latitud = LAT_BASE - 0.012,
            longitud = LON_BASE + 0.006,
            inicio = TIMESTAMP_BASE + 2 * DIA,
            duracion = 4 * HORA,
            estado = EstadoEvento.PROGRAMADO
        ),
        evento(
            id = "evento-5",
            proyectoId = "proyecto-2",
            tituloProyecto = "Huerto Comunitario Demo",
            titulo = "Taller de Siembra de Primavera",
            descripcion = "Siembra ficticia de almácigos.",
            lugar = "Plaza Demo Sur",
            latitud = LAT_BASE - 0.012,
            longitud = LON_BASE + 0.006,
            // Evento cancelado: demuestra que el historial no se borra.
            inicio = TIMESTAMP_BASE + 12 * DIA,
            duracion = 3 * HORA,
            estado = EstadoEvento.CANCELADO
        ),
        evento(
            id = "evento-6",
            proyectoId = "proyecto-2",
            tituloProyecto = "Huerto Comunitario Demo",
            titulo = "Feria de Intercambio de Semillas",
            descripcion = "Actividad ficticia de intercambio.",
            lugar = "Parque Demo Oriente",
            latitud = LAT_BASE + 0.015,
            longitud = LON_BASE + 0.010,
            inicio = TIMESTAMP_BASE + 20 * DIA,
            duracion = 5 * HORA,
            estado = EstadoEvento.PROGRAMADO
        )
    )

    // ------------------------------------------------------------------
    // Participantes
    // ------------------------------------------------------------------

    val participantes: List<ParticipanteEntity> = listOf(
        participante("participante-1", "11111111-1", "Ana Demo Fuentes", "ana@demo.cl"),
        participante("participante-2", "22222222-2", "Bruno Demo Rivas", "bruno@demo.cl"),
        participante("participante-3", "33333333-3", "Carmen Demo Soto", "carmen@demo.cl"),
        participante("participante-4", "44444444-4", "Diego Demo Pastén", "diego@demo.cl"),
        participante("participante-5", "55555555-5", "Elena Demo Muñoz", "elena@demo.cl"),
        participante("participante-6", null, "Fabián Demo Vega", null, token = "SP-DEMOSINRUT1"),
        participante("participante-7", "77777777-7", "Gabriela Demo Núñez", "gabriela@demo.cl"),
        participante("participante-8", "88888888-8", "Héctor Demo Lara", "hector@demo.cl"),
        participante("participante-9", "99999999-9", "Irene Demo Castro", "irene@demo.cl"),
        participante("participante-10", "10101010-0", "Javier Demo Pinto", "javier@demo.cl")
    )

    // ------------------------------------------------------------------
    // Inscripciones
    // ------------------------------------------------------------------

    val inscripciones: List<InscripcionEntity> = listOf(
        // evento-1 (finalizado): 5 personas, 3 acreditadas.
        inscripcion("inscripcion-1", "evento-1", "participante-1", "QR-EVENTO1-A", EstadoInscripcion.ACREDITADO),
        inscripcion("inscripcion-2", "evento-1", "participante-2", "QR-EVENTO1-B", EstadoInscripcion.ACREDITADO),
        inscripcion("inscripcion-3", "evento-1", "participante-3", "QR-EVENTO1-C", EstadoInscripcion.ACREDITADO),
        inscripcion("inscripcion-4", "evento-1", "participante-4", "QR-EVENTO1-D", EstadoInscripcion.CANCELADO),
        inscripcion("inscripcion-5", "evento-1", "participante-5", "QR-EVENTO1-E", EstadoInscripcion.PENDIENTE),

        // evento-2 (próximo): 3 inscritos, uno ya acreditado por walk-in temprano.
        inscripcion("inscripcion-6", "evento-2", "participante-1", "QR-EVENTO2-A", EstadoInscripcion.ACREDITADO),
        inscripcion("inscripcion-7", "evento-2", "participante-6", "QR-EVENTO2-B", EstadoInscripcion.PENDIENTE),
        inscripcion("inscripcion-8", "evento-2", "participante-7", "QR-EVENTO2-C", EstadoInscripcion.PENDIENTE),

        // evento-4 (próximo): 3 inscritos.
        inscripcion("inscripcion-9", "evento-4", "participante-8", "QR-EVENTO4-A", EstadoInscripcion.PENDIENTE),
        inscripcion("inscripcion-10", "evento-4", "participante-9", "QR-EVENTO4-B", EstadoInscripcion.PENDIENTE),
        inscripcion("inscripcion-11", "evento-4", "participante-10", "QR-EVENTO4-C", EstadoInscripcion.PENDIENTE),

        // evento-5 (cancelado): inscripción previa que quedó en el historial.
        inscripcion("inscripcion-12", "evento-5", "participante-2", "QR-EVENTO5-A", EstadoInscripcion.CANCELADO),

        // evento-6: 2 inscritos.
        inscripcion("inscripcion-13", "evento-6", "participante-3", "QR-EVENTO6-A", EstadoInscripcion.PENDIENTE),
        inscripcion("inscripcion-14", "evento-6", "participante-5", "QR-EVENTO6-B", EstadoInscripcion.PENDIENTE)
    )

    // ------------------------------------------------------------------
    // Asistencias
    // ------------------------------------------------------------------

    /**
     * 5 asistencias: 4 ya sincronizadas y **1 pendiente** (`asistencia-5`),
     * para poder mostrar el contador offline y probar el worker en la Fase 9.
     * Solo 4 de las 5 llevan encuesta respondida, para ejercitar los promedios
     * con valores `NULL`.
     */
    val asistencias: List<AsistenciaEntity> = listOf(
        AsistenciaEntity(
            id = "asistencia-1",
            inscripcionId = "inscripcion-1",
            voluntarioId = "usuario-4",
            timestampMarcacion = TIMESTAMP_BASE,
            latitudMarcacion = LAT_BASE + 0.0001,
            longitudMarcacion = LON_BASE + 0.0001,
            precisionGpsMetros = 8.0,
            distanciaEventoMetros = 14.5,
            satisfaccion = 5,
            utilidad = 5,
            comentarios = "Muy claro, aprendí a adjuntar archivos.",
            isSynced = true,
            timestampSync = TIMESTAMP_BASE + HORA,
            updatedAt = TIMESTAMP_BASE + HORA
        ),
        AsistenciaEntity(
            id = "asistencia-2",
            inscripcionId = "inscripcion-2",
            voluntarioId = "usuario-4",
            timestampMarcacion = TIMESTAMP_BASE + 10 * 60_000L,
            latitudMarcacion = LAT_BASE,
            longitudMarcacion = LON_BASE,
            precisionGpsMetros = 22.3,
            distanciaEventoMetros = 3.2,
            satisfaccion = 4,
            utilidad = 5,
            comentarios = "Buen ritmo, faltó más práctica.",
            isSynced = true,
            timestampSync = TIMESTAMP_BASE + HORA,
            updatedAt = TIMESTAMP_BASE + HORA
        ),
        AsistenciaEntity(
            id = "asistencia-3",
            inscripcionId = "inscripcion-3",
            voluntarioId = "usuario-5",
            timestampMarcacion = TIMESTAMP_BASE + 20 * 60_000L,
            // Sin permiso de ubicación: se guarda null y la marcación es válida.
            latitudMarcacion = null,
            longitudMarcacion = null,
            precisionGpsMetros = null,
            distanciaEventoMetros = null,
            satisfaccion = 5,
            utilidad = 4,
            comentarios = null,
            isSynced = true,
            timestampSync = TIMESTAMP_BASE + HORA,
            updatedAt = TIMESTAMP_BASE + HORA
        ),
        AsistenciaEntity(
            id = "asistencia-4",
            inscripcionId = "inscripcion-6",
            voluntarioId = "usuario-4",
            timestampMarcacion = TIMESTAMP_BASE + DIA + 5 * 60_000L,
            latitudMarcacion = LAT_BASE + 0.008,
            longitudMarcacion = LON_BASE - 0.004,
            precisionGpsMetros = 6.5,
            distanciaEventoMetros = 2.0,
            // Encuesta sin responder todavía: promedios deben ignorar estos NULL.
            satisfaccion = null,
            utilidad = null,
            comentarios = null,
            isSynced = true,
            timestampSync = TIMESTAMP_BASE + DIA + HORA,
            updatedAt = TIMESTAMP_BASE + DIA + HORA
        ),
        AsistenciaEntity(
            id = "asistencia-5",
            inscripcionId = "inscripcion-9",
            voluntarioId = "usuario-5",
            timestampMarcacion = TIMESTAMP_BASE + 2 * DIA,
            latitudMarcacion = LAT_BASE - 0.012,
            longitudMarcacion = LON_BASE + 0.006,
            precisionGpsMetros = 11.8,
            distanciaEventoMetros = 8.9,
            satisfaccion = 4,
            utilidad = 4,
            comentarios = "Jornada entretenida, faltaron herramientas.",
            // Pendiente de subir: demuestra la cola de sync.
            isSynced = false,
            timestampSync = null,
            updatedAt = TIMESTAMP_BASE + 2 * DIA
        )
    )

    // ------------------------------------------------------------------
    // Constructores de apoyo (mantienen legibles las listas de arriba)
    // ------------------------------------------------------------------

    private fun evento(
        id: String,
        proyectoId: String,
        tituloProyecto: String,
        titulo: String,
        descripcion: String,
        lugar: String,
        latitud: Double,
        longitud: Double,
        inicio: Long,
        duracion: Long,
        estado: EstadoEvento
    ) = EventoEntity(
        id = id,
        proyectoId = proyectoId,
        tituloProyecto = tituloProyecto,
        titulo = titulo,
        descripcion = descripcion,
        lugar = lugar,
        latitud = latitud,
        longitud = longitud,
        radioToleranciaMetros = 200,
        fechaInicio = inicio,
        fechaFin = inicio + duracion,
        estado = estado,
        isSynced = true,
        updatedAt = TIMESTAMP_BASE - DIA
    )

    private fun participante(
        id: String,
        rut: String?,
        nombre: String,
        email: String?,
        token: String = "SP-DEMO-${id.uppercase()}"
    ) = ParticipanteEntity(
        id = id,
        rut = rut,
        nombreCompleto = nombre,
        email = email,
        telefono = "+5690000000${id.filter { it.isDigit() }.takeLast(1).ifEmpty { "0" }}",
        qrToken = token,
        createdAt = TIMESTAMP_BASE - 7 * DIA,
        isSynced = true,
        updatedAt = TIMESTAMP_BASE - 7 * DIA
    )

    private fun inscripcion(
        id: String,
        eventoId: String,
        participanteId: String,
        qrToken: String,
        estado: EstadoInscripcion,
        tipoRegistro: TipoInscripcion = TipoInscripcion.PREVIO
    ) = InscripcionEntity(
        id = id,
        eventoId = eventoId,
        participanteId = participanteId,
        // Copia desnormalizada: debe coincidir con Participante.nombreCompleto.
        nombreParticipante = participantes.first { it.id == participanteId }.nombreCompleto,
        qrToken = qrToken,
        tipoRegistro = tipoRegistro,
        estado = estado,
        timestampInscripcion = TIMESTAMP_BASE - 3 * DIA,
        isSynced = true,
        updatedAt = TIMESTAMP_BASE - 3 * DIA
    )
}
