package com.rojas.syncpass.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rojas.syncpass.core.database.AppDatabase
import com.rojas.syncpass.core.id.IdProvider
import com.rojas.syncpass.core.time.TimeProvider
import com.rojas.syncpass.data.local.seed.SeedDataInitializer
import com.rojas.syncpass.domain.model.EstadoEvento
import com.rojas.syncpass.domain.model.EstadoInscripcion
import com.rojas.syncpass.domain.model.EstadoProyecto
import com.rojas.syncpass.domain.model.RolUsuario
import com.rojas.syncpass.domain.model.TipoInscripcion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de los repositorios de la Fase 3.
 *
 * Se inyectan un reloj y un generador de IDs controlados para que las
 * aserciones sobre `updatedAt`, `isSynced` e IDs sean deterministas.
 */
@RunWith(AndroidJUnit4::class)
class RepositoriosTest {

    private lateinit var db: AppDatabase
    private lateinit var usuarioRepository: UsuarioRepository
    private lateinit var proyectoRepository: ProyectoRepository
    private lateinit var participanteRepository: ParticipanteRepository
    private lateinit var asistenciaRepository: AsistenciaRepository
    private lateinit var seed: SeedDataInitializer

    private val tiempoFijo = 1_700_000_000_000L
    private val timeProvider = TimeProvider { tiempoFijo }

    /** IDs deterministas y predecibles para poder buscarlos en las aserciones. */
    private val idProvider = object : IdProvider {
        private var contador = 0
        override fun nuevoId(): String = "generado-${++contador}"
    }

    @Before
    fun crearDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val transaccionador = RoomTransaccionador(db)
        usuarioRepository = UsuarioRepository(db.usuarioDao(), timeProvider)
        proyectoRepository = ProyectoRepository(
            proyectoDao = db.proyectoDao(),
            eventoDao = db.eventoDao(),
            inscripcionDao = db.inscripcionDao(),
            participanteDao = db.participanteDao(),
            asistenciaDao = db.asistenciaDao(),
            transaccionador = transaccionador,
            timeProvider = timeProvider,
            idProvider = idProvider
        )
        participanteRepository = ParticipanteRepository(
            participanteDao = db.participanteDao(),
            proyectoRepository = proyectoRepository,
            transaccionador = transaccionador,
            timeProvider = timeProvider,
            idProvider = idProvider
        )
        asistenciaRepository = AsistenciaRepository(
            asistenciaDao = db.asistenciaDao(),
            inscripcionDao = db.inscripcionDao(),
            timeProvider = timeProvider
        )
        seed = SeedDataInitializer(db, transaccionador)
    }

    @After
    fun cerrarDb() {
        db.close()
    }

    // ------------------------------------------------------------------
    // Seed idempotente
    // ------------------------------------------------------------------

    @Test
    fun seedEsIdempotenteYNoDuplica() = runBlocking {
        assertTrue(seed.sembrar())
        val conteos = conteosActuales()
        assertEquals(5, conteos.usuarios)
        assertEquals(3, conteos.proyectos)
        assertEquals(6, conteos.eventos)
        assertEquals(10, conteos.participantes)
        assertEquals(14, conteos.inscripciones)
        assertEquals(5, conteos.asistencias)

        // Segunda y tercera ejecución: no cambian los conteos.
        assertFalse(seed.sembrar())
        assertFalse(seed.sembrar())
        assertEquals(conteos, conteosActuales())
    }

    @Test
    fun seedNoPisaEdicionesDelUsuario() = runBlocking {
        seed.sembrar()
        proyectoRepository.renombrarProyecto("proyecto-1", "Título editado en terreno")

        // El seed vuelve a correr (por ejemplo, al reiniciar el proceso).
        seed = SeedDataInitializer(db, RoomTransaccionador(db))
        assertFalse(seed.sembrar())

        val proyecto = db.proyectoDao().obtenerPorId("proyecto-1")!!
        assertEquals("Título editado en terreno", proyecto.titulo)
    }

    @Test
    fun seedDejaUnaAsistenciaPendienteDeSync() = runBlocking {
        seed.sembrar()
        assertEquals(1, asistenciaRepository.observarCantidadPendientes().first())
    }

    @Test
    fun seedRespetaClavesForaneasYSusCopiasDesnormalizadas() = runBlocking {
        seed.sembrar()

        // Cada evento hereda el título de su proyecto.
        val proyecto1 = db.proyectoDao().obtenerPorId("proyecto-1")!!
        val eventosProyecto1 = db.eventoDao().observarPorProyecto("proyecto-1").first()
        assertEquals(3, eventosProyecto1.size)
        assertTrue(eventosProyecto1.all { it.tituloProyecto == proyecto1.titulo })

        // Cada inscripción copia el nombre del participante.
        val inscripciones = db.inscripcionDao().observarPorEvento("evento-1").first()
        inscripciones.forEach { inscripcion ->
            val participante = db.participanteDao().obtenerPorId(inscripcion.participanteId)!!
            assertEquals(participante.nombreCompleto, inscripcion.nombreParticipante)
        }
    }

    // ------------------------------------------------------------------
    // Validación de username (hallazgo de la Fase 1)
    // ------------------------------------------------------------------

    @Test
    fun usernameDuplicadoDevuelveErrorLegible() = runBlocking {
        seed.sembrar()

        val resultado = usuarioRepository.crearLocal(
            id = "usuario-nuevo",
            username = "coord.demo", // ya existe
            nombreCompleto = "Otra Persona",
            rol = RolUsuario.COORDINADOR
        )

        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertEquals(
            ErrorOperacion.USERNAME_DUPLICADO,
            (resultado as ResultadoOperacion.Fallo).error
        )
        // Y no se creó la fila fantasma.
        assertNull(db.usuarioDao().obtenerPorId("usuario-nuevo"))
        assertEquals(5, usuarioRepository.contar())
    }

    @Test
    fun guardarDesdeServidorConUsernameDeOtroIdFalla() = runBlocking {
        seed.sembrar()

        val resultado = usuarioRepository.guardarDesdeServidor(
            com.rojas.syncpass.data.local.entity.UsuarioEntity(
                id = "otro-id",
                username = "admin.demo", // pertenece a usuario-1
                nombreCompleto = "Impostor",
                rol = RolUsuario.ADMINISTRADOR,
                createdAt = tiempoFijo,
                isSynced = true,
                updatedAt = tiempoFijo
            )
        )

        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertNull(db.usuarioDao().obtenerPorId("otro-id"))
    }

    @Test
    fun usuarioLocalNacePendienteDeSync() = runBlocking {
        val resultado = usuarioRepository.crearLocal(
            id = "usuario-local",
            username = "nuevo.demo",
            nombreCompleto = "Nuevo Usuario Demo",
            rol = RolUsuario.VOLUNTARIO
        )

        val usuario = (resultado as ResultadoOperacion.Exito).valor
        assertFalse(usuario.isSynced)
        assertEquals(tiempoFijo, usuario.updatedAt)
        assertEquals(tiempoFijo, usuario.createdAt)
    }

    // ------------------------------------------------------------------
    // Write-through
    // ------------------------------------------------------------------

    @Test
    fun renombrarProyectoPropagaTituloAEventos() = runBlocking {
        seed.sembrar()

        val resultado = proyectoRepository.renombrarProyecto("proyecto-1", "Alfabetización Digital 2.0")
        assertTrue(resultado is ResultadoOperacion.Exito)

        val eventos = db.eventoDao().observarPorProyecto("proyecto-1").first()
        assertTrue(eventos.all { it.tituloProyecto == "Alfabetización Digital 2.0" })
        // Write-through marca los eventos como pendientes.
        assertTrue(eventos.all { !it.isSynced })
        assertTrue(eventos.all { it.updatedAt == tiempoFijo })

        // Los eventos de otros proyectos no se tocan.
        val otros = db.eventoDao().observarPorProyecto("proyecto-2").first()
        assertTrue(otros.all { it.tituloProyecto == "Huerto Comunitario Demo" })
    }

    @Test
    fun renombrarParticipantePropagaNombreAInscripciones() = runBlocking {
        seed.sembrar()

        val resultado = participanteRepository.renombrarParticipante(
            "participante-1",
            "Ana Demo Fuentes Actualizada"
        )
        assertTrue(resultado is ResultadoOperacion.Exito)

        val inscripciones = db.inscripcionDao().observarPorParticipante("participante-1").first()
        assertEquals(2, inscripciones.size)
        assertTrue(inscripciones.all { it.nombreParticipante == "Ana Demo Fuentes Actualizada" })
        assertTrue(inscripciones.all { !it.isSynced })
    }

    @Test
    fun rotarQrPropagaTokenAInscripciones() = runBlocking {
        seed.sembrar()

        val resultado = participanteRepository.rotarQr("participante-1", "SP-NUEVO-TOKEN-1")
        assertEquals(
            "SP-NUEVO-TOKEN-1",
            (resultado as ResultadoOperacion.Exito).valor
        )

        val inscripciones = db.inscripcionDao().observarPorParticipante("participante-1").first()
        assertTrue(inscripciones.all { it.qrToken == "SP-NUEVO-TOKEN-1" })
        // El QR viejo ya no resuelve en el evento.
        assertNull(db.inscripcionDao().buscarPorEventoYQrToken("evento-1", "QR-EVENTO1-A"))
        assertNotNull(db.inscripcionDao().buscarPorEventoYQrToken("evento-1", "SP-NUEVO-TOKEN-1"))
    }

    @Test
    fun rotarQrConTokenYaUsadoFalla() = runBlocking {
        seed.sembrar()

        val resultado = participanteRepository.rotarQr(
            id = "participante-1",
            nuevoQrToken = "QR-EVENTO1-B" // pertenece al participante-2
        )

        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertEquals("SP-DEMO-PARTICIPANTE-1", db.participanteDao().obtenerPorId("participante-1")?.qrToken)
    }

    // ------------------------------------------------------------------
    // Creación y validaciones
    // ------------------------------------------------------------------

    @Test
    fun crearProyectoYEventoHeredaTitulo() = runBlocking {
        seed.sembrar()

        val proyecto = (proyectoRepository.crearProyecto(
            titulo = "  Proyecto Nuevo Demo  ",
            coordinadorId = "usuario-2",
            estado = EstadoProyecto.ACTIVO
        ) as ResultadoOperacion.Exito).valor
        assertEquals("Proyecto Nuevo Demo", proyecto.titulo) // se recorta
        assertFalse(proyecto.isSynced)

        val evento = (proyectoRepository.crearEvento(
            proyectoId = proyecto.id,
            titulo = "Taller Nuevo",
            lugar = "Sede Demo",
            latitud = -33.44,
            longitud = -70.65,
            fechaInicio = tiempoFijo,
            fechaFin = tiempoFijo + 3_600_000L
        ) as ResultadoOperacion.Exito).valor

        assertEquals(proyecto.titulo, evento.tituloProyecto)
        assertFalse(evento.isSynced)
        assertEquals(200, evento.radioToleranciaMetros)
    }

    @Test
    fun crearProyectoConTituloVacioFalla() = runBlocking {
        val resultado = proyectoRepository.crearProyecto("   ", "usuario-2")
        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertEquals(
            ErrorOperacion.OPERACION_INVALIDA,
            (resultado as ResultadoOperacion.Fallo).error
        )
    }

    @Test
    fun crearEventoConProyectoInexistenteFalla() = runBlocking {
        val resultado = proyectoRepository.crearEvento(
            proyectoId = "proyecto-inexistente",
            titulo = "Taller",
            lugar = "Sede",
            latitud = -33.44,
            longitud = -70.65,
            fechaInicio = tiempoFijo,
            fechaFin = tiempoFijo + 1_000L
        )
        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertEquals(
            ErrorOperacion.NO_ENCONTRADO,
            (resultado as ResultadoOperacion.Fallo).error
        )
    }

    @Test
    fun eventoConFechaFinAnteriorAlInicioFalla() = runBlocking {
        seed.sembrar()
        val resultado = proyectoRepository.crearEvento(
            proyectoId = "proyecto-1",
            titulo = "Taller",
            lugar = "Sede",
            latitud = -33.44,
            longitud = -70.65,
            fechaInicio = tiempoFijo,
            fechaFin = tiempoFijo - 1_000L
        )
        assertTrue(resultado is ResultadoOperacion.Fallo)
    }

    @Test
    fun crearParticipanteConRutDuplicadoFalla() = runBlocking {
        seed.sembrar()

        val resultado = participanteRepository.crearParticipante(
            nombreCompleto = "Otra Persona Demo",
            rut = "11111111-1" // ya existe
        )

        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertEquals(
            ErrorOperacion.RUT_DUPLICADO,
            (resultado as ResultadoOperacion.Fallo).error
        )
        assertEquals(10, db.participanteDao().contar())
    }

    @Test
    fun crearDosParticipantesSinRutEsValido() = runBlocking {
        seed.sembrar()

        val primero = participanteRepository.crearParticipante("Sin Rut Demo Uno", rut = null)
        val segundo = participanteRepository.crearParticipante("Sin Rut Demo Dos", rut = "   ")

        assertTrue(primero is ResultadoOperacion.Exito)
        assertTrue(segundo is ResultadoOperacion.Exito)
        assertNull((primero as ResultadoOperacion.Exito).valor.rut)
        assertNull((segundo as ResultadoOperacion.Exito).valor.rut)
    }

    @Test
    fun crearParticipanteGeneraQrUnicoYLegible() = runBlocking {
        val resultado = participanteRepository.crearParticipante("Walk-in Demo")
        val participante = (resultado as ResultadoOperacion.Exito).valor

        // El IdProvider de prueba devuelve "generado-N": el QR usa ese token.
        assertEquals("generado-1", participante.qrToken)
        assertFalse(participante.isSynced)
    }

    @Test
    fun inscribirParticipanteDuplicadoFalla() = runBlocking {
        seed.sembrar()

        val resultado = proyectoRepository.inscribirParticipante(
            eventoId = "evento-1",
            participanteId = "participante-1", // ya inscrito en evento-1
            tipoRegistro = TipoInscripcion.WALK_IN
        )

        assertTrue(resultado is ResultadoOperacion.Fallo)
        assertEquals(
            ErrorOperacion.YA_INSCRITO_EN_EVENTO,
            (resultado as ResultadoOperacion.Fallo).error
        )
    }

    @Test
    fun inscribirWalkInCopiaNombreYQr() = runBlocking {
        seed.sembrar()

        val resultado = proyectoRepository.inscribirParticipante(
            eventoId = "evento-3",
            participanteId = "participante-4",
            tipoRegistro = TipoInscripcion.WALK_IN
        )

        val inscripcion = (resultado as ResultadoOperacion.Exito).valor
        val participante = db.participanteDao().obtenerPorId("participante-4")!!
        assertEquals(participante.nombreCompleto, inscripcion.nombreParticipante)
        assertEquals(participante.qrToken, inscripcion.qrToken)
        assertEquals(TipoInscripcion.WALK_IN, inscripcion.tipoRegistro)
        assertEquals(EstadoInscripcion.PENDIENTE, inscripcion.estado)
        assertFalse(inscripcion.isSynced)
    }

    // ------------------------------------------------------------------
    // Estados y encuesta
    // ------------------------------------------------------------------

    @Test
    fun cambiarEstadosNoBorraHistorial() = runBlocking {
        seed.sembrar()

        assertTrue(
            proyectoRepository.cambiarEstadoProyecto("proyecto-1", EstadoProyecto.FINALIZADO)
                is ResultadoOperacion.Exito
        )
        assertEquals(
            EstadoProyecto.FINALIZADO,
            db.proyectoDao().obtenerPorId("proyecto-1")?.estado
        )

        assertTrue(
            proyectoRepository.cambiarEstadoEvento("evento-2", EstadoEvento.CANCELADO)
                is ResultadoOperacion.Exito
        )
        assertEquals(
            EstadoEvento.CANCELADO,
            db.eventoDao().obtenerPorId("evento-2")?.estado
        )

        // Las inscripciones siguen ahí.
        assertEquals(3, db.inscripcionDao().contarPorEvento("evento-2"))
    }

    @Test
    fun responderEncuestaValidaEscalaYSoloSobreAsistenciaExistente() = runBlocking {
        seed.sembrar()

        // asistencia-1 existe y ya estaba sincronizada.
        val resultado = asistenciaRepository.responderEncuesta(
            inscripcionId = "inscripcion-1",
            satisfaccion = 3,
            utilidad = 2,
            comentarios = "  Comentario editado  "
        )
        assertTrue(resultado is ResultadoOperacion.Exito)

        val asistencia = db.asistenciaDao().obtenerPorInscripcion("inscripcion-1")!!
        assertEquals(3, asistencia.satisfaccion)
        assertEquals(2, asistencia.utilidad)
        assertEquals("Comentario editado", asistencia.comentarios) // se recorta
        // Editar tras sincronizar deja la fila pendiente.
        assertFalse(asistencia.isSynced)
        assertEquals(tiempoFijo, asistencia.updatedAt)

        // Fuera de escala.
        val fueraDeEscala = asistenciaRepository.responderEncuesta("inscripcion-1", 6, 0)
        assertTrue(fueraDeEscala is ResultadoOperacion.Fallo)

        // Inscripción sin asistencia (inscripcion-3 ya está acreditada, así que
        // se usa inscripcion-5, que sigue PENDIENTE y no tiene asistencia).
        val sinAsistencia = asistenciaRepository.responderEncuesta("inscripcion-5", 5, 5)
        assertTrue(sinAsistencia is ResultadoOperacion.Fallo)
        assertEquals(
            ErrorOperacion.NO_ENCONTRADO,
            (sinAsistencia as ResultadoOperacion.Fallo).error
        )
    }

    @Test
    fun encuestaNoSeAceptaSobreInscripcionCancelada() = runBlocking {
        seed.sembrar()

        // inscripcion-4 pertenece a evento-1 y está CANCELADO, sin asistencia.
        val resultado = asistenciaRepository.responderEncuesta("inscripcion-4", 5, 5)
        assertTrue(resultado is ResultadoOperacion.Fallo)
    }

    @Test
    fun cancelarInscripcionConservaFila() = runBlocking {
        seed.sembrar()

        assertTrue(
            proyectoRepository.cancelarInscripcion("inscripcion-7") is ResultadoOperacion.Exito
        )
        val inscripcion = db.inscripcionDao().obtenerPorId("inscripcion-7")!!
        assertEquals(EstadoInscripcion.CANCELADO, inscripcion.estado)
        assertFalse(inscripcion.isSynced)
        assertEquals(3, db.inscripcionDao().contarPorEvento("evento-2"))
    }

    @Test
    fun marcarSincronizadaRegistraAuditoria() = runBlocking {
        seed.sembrar()

        assertTrue(
            asistenciaRepository.marcarSincronizada("asistencia-5") is ResultadoOperacion.Exito
        )
        val asistencia = db.asistenciaDao().obtenerPorId("asistencia-5")!!
        assertTrue(asistencia.isSynced)
        assertEquals(tiempoFijo, asistencia.timestampSync)
        assertEquals(0, asistenciaRepository.observarCantidadPendientes().first())
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    private data class Conteos(
        val usuarios: Int,
        val proyectos: Int,
        val eventos: Int,
        val participantes: Int,
        val inscripciones: Int,
        val asistencias: Int
    )

    private suspend fun conteosActuales() = Conteos(
        usuarios = db.usuarioDao().contar(),
        proyectos = db.proyectoDao().contar(),
        eventos = db.eventoDao().contar(),
        participantes = db.participanteDao().contar(),
        inscripciones = db.inscripcionDao().contar(),
        asistencias = db.asistenciaDao().contar()
    )
}
