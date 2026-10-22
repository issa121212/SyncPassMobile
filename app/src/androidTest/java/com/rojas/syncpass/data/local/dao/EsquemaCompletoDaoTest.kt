package com.rojas.syncpass.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rojas.syncpass.SyncPassApplication
import com.rojas.syncpass.core.database.AppDatabase
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
 * Pruebas del esquema completo de la Fase 2.
 *
 * Se usa una DB en memoria, igual que en Fase 1, para no tocar `syncpass.db`
 * del dispositivo. Cada test siembra sus propias filas ficticias.
 */
@RunWith(AndroidJUnit4::class)
class EsquemaCompletoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var usuarioDao: UsuarioDao
    private lateinit var proyectoDao: ProyectoDao
    private lateinit var eventoDao: EventoDao
    private lateinit var participanteDao: ParticipanteDao
    private lateinit var inscripcionDao: InscripcionDao
    private lateinit var asistenciaDao: AsistenciaDao
    private lateinit var metricasDao: MetricasDao

    @Before
    fun crearDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        usuarioDao = db.usuarioDao()
        proyectoDao = db.proyectoDao()
        eventoDao = db.eventoDao()
        participanteDao = db.participanteDao()
        inscripcionDao = db.inscripcionDao()
        asistenciaDao = db.asistenciaDao()
        metricasDao = db.metricasDao()
    }

    @After
    fun cerrarDb() {
        db.close()
    }

    // ------------------------------------------------------------------
    // CRUD y relaciones
    // ------------------------------------------------------------------

    @Test
    fun crudYRelacionInscripcionConAsistencia() = runBlocking {
        sembrarBase()

        assertEquals("Proyecto Demo", proyectoDao.obtenerPorId("proyecto-1")?.titulo)
        assertEquals(1, proyectoDao.contar())
        assertEquals(1, eventoDao.observarPorProyecto("proyecto-1").first().size)
        assertEquals(1, eventoDao.observarEntre(5_000L, 15_000L).first().size)
        assertEquals(1, eventoDao.observarPorEstado(EstadoEvento.PROGRAMADO).first().size)
        assertEquals("participante-1", participanteDao.buscarPorQrToken("QR-1")?.id)
        assertEquals("participante-1", participanteDao.buscarPorRut("11111111-1")?.id)
        assertEquals(1, participanteDao.buscarPorNombre("%Ficticio%").first().size)

        val inscripcion = inscripcionDao.buscarPorEventoYQrToken("evento-1", "QR-1")
        assertNotNull(inscripcion)
        assertEquals(inscripcion?.id, inscripcionDao
            .buscarPorEventoYParticipante("evento-1", "participante-1")?.id)

        val sinAsistencia = inscripcionDao
            .observarConAsistenciaPorEvento("evento-1")
            .first()
            .single()
        assertNull(sinAsistencia.asistencia)

        asistenciaDao.insertar(asistenciaDemo())

        val conAsistencia = inscripcionDao
            .observarConAsistenciaPorEvento("evento-1")
            .first()
            .single()
        assertEquals("asistencia-1", conAsistencia.asistencia?.id)
        assertEquals(EstadoInscripcion.PENDIENTE, conAsistencia.inscripcion.estado)
    }

    // ------------------------------------------------------------------
    // Invariantes de unicidad
    // ------------------------------------------------------------------

    @Test
    fun inscripcionUnicaPorEventoYParticipante() = runBlocking {
        sembrarBase()

        val duplicada = inscripcionDemo(
            id = "inscripcion-2",
            qrToken = "QR-2"
        )
        val error = runCatching { inscripcionDao.insertar(duplicada) }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertEquals(1, inscripcionDao.contarPorEvento("evento-1"))
    }

    @Test
    fun qrUnicoPorEventoPeroReutilizableEnOtroEvento() = runBlocking {
        sembrarBase()
        eventoDao.insertar(eventoDemo(id = "evento-2", fechaInicio = 20_000L))
        participanteDao.insertar(
            participanteDemo(id = "participante-2", rut = "22222222-2", qrToken = "QR-2")
        )

        // El mismo QR ya está usado en evento-1, pero se intenta en evento-1
        // con otro participante: debe fallar.
        val error = runCatching {
            inscripcionDao.insertar(
                inscripcionDemo(
                    id = "inscripcion-2",
                    participanteId = "participante-2",
                    qrToken = "QR-1"
                )
            )
        }.exceptionOrNull()
        assertTrue(error is SQLiteConstraintException)

        // La unicidad es por evento: el mismo QR sí puede existir en evento-2.
        inscripcionDao.insertar(
            inscripcionDemo(
                id = "inscripcion-3",
                eventoId = "evento-2",
                participanteId = "participante-2",
                qrToken = "QR-1"
            )
        )
        assertNotNull(inscripcionDao.buscarPorEventoYQrToken("evento-2", "QR-1"))
    }

    @Test
    fun participanteQrYRutUnicosConRutOpcional() = runBlocking {
        sembrarBase()

        val qrDuplicado = participanteDemo(
            id = "participante-qr",
            rut = "33333333-3",
            qrToken = "QR-1"
        )
        assertTrue(
            runCatching { participanteDao.insertar(qrDuplicado) }
                .exceptionOrNull() is SQLiteConstraintException
        )

        val rutDuplicado = participanteDemo(
            id = "participante-rut",
            rut = "11111111-1",
            qrToken = "QR-9"
        )
        assertTrue(
            runCatching { participanteDao.insertar(rutDuplicado) }
                .exceptionOrNull() is SQLiteConstraintException
        )

        // SQLite permite múltiples NULL en un índice UNIQUE.
        participanteDao.insertar(
            participanteDemo(id = "participante-null-1", rut = null, qrToken = "QR-N1")
        )
        participanteDao.insertar(
            participanteDemo(id = "participante-null-2", rut = null, qrToken = "QR-N2")
        )
        assertEquals(3, participanteDao.contar())
    }

    @Test
    fun asistenciaUnicaPorInscripcionYConFksObligatorias() = runBlocking {
        sembrarBase()
        asistenciaDao.insertar(asistenciaDemo())

        val duplicada = asistenciaDemo(id = "asistencia-2")
        assertTrue(
            runCatching { asistenciaDao.insertar(duplicada) }
                .exceptionOrNull() is SQLiteConstraintException
        )

        val inscripcionInexistente = asistenciaDemo(
            id = "asistencia-3",
            inscripcionId = "inscripcion-inexistente"
        )
        assertTrue(
            runCatching { asistenciaDao.insertar(inscripcionInexistente) }
                .exceptionOrNull() is SQLiteConstraintException
        )

        val voluntarioInexistente = asistenciaDemo(
            id = "asistencia-4",
            voluntarioId = "usuario-inexistente"
        )
        assertTrue(
            runCatching { asistenciaDao.insertar(voluntarioInexistente) }
                .exceptionOrNull() is SQLiteConstraintException
        )

        assertEquals(1, asistenciaDao.contar())
    }

    // ------------------------------------------------------------------
    // Protección de historial con FK RESTRICT
    // ------------------------------------------------------------------

    @Test
    fun fkRestrictImpideBorrarHistorial() = runBlocking {
        sembrarBase()
        asistenciaDao.insertar(asistenciaDemo())

        val borrados = listOf<suspend () -> Unit>(
            { usuarioDao.eliminarPorId("usuario-coord") },
            { usuarioDao.eliminarPorId("usuario-vol") },
            { proyectoDao.eliminarPorId("proyecto-1") },
            { eventoDao.eliminarPorId("evento-1") },
            { participanteDao.eliminarPorId("participante-1") },
            { inscripcionDao.eliminarPorId("inscripcion-1") }
        )

        borrados.forEach { borrar ->
            val error = runCatching { borrar() }.exceptionOrNull()
            assertTrue("Se esperaba RESTRICT, pero se obtuvo $error", error is SQLiteConstraintException)
        }

        assertEquals(1, proyectoDao.contar())
        assertEquals(1, eventoDao.contar())
        assertEquals(1, inscripcionDao.contar())
        assertEquals(1, asistenciaDao.contar())
    }

    // ------------------------------------------------------------------
    // Sync y encuesta
    // ------------------------------------------------------------------

    @Test
    fun editarEncuestaVuelveAPendienteDeSync() = runBlocking {
        sembrarBase()
        asistenciaDao.insertar(asistenciaDemo(isSynced = true, timestampSync = 9_000L))

        val filas = asistenciaDao.actualizarEncuesta(
            inscripcionId = "inscripcion-1",
            satisfaccion = 5,
            utilidad = 4,
            comentarios = "Muy útil",
            updatedAt = 20_000L
        )

        assertEquals(1, filas)
        val editada = asistenciaDao.obtenerPorInscripcion("inscripcion-1")!!
        assertEquals(5, editada.satisfaccion)
        assertEquals(4, editada.utilidad)
        assertEquals("Muy útil", editada.comentarios)
        assertFalse(editada.isSynced)
        assertEquals(9_000L, editada.timestampSync)
        assertEquals(20_000L, editada.updatedAt)

        asistenciaDao.marcarSincronizada(editada.id, timestampSync = 30_000L, updatedAt = 30_000L)
        val sincronizada = asistenciaDao.obtenerPorInscripcion("inscripcion-1")!!
        assertTrue(sincronizada.isSynced)
        assertEquals(30_000L, sincronizada.timestampSync)
    }

    @Test
    fun flowEmiteAlCambiarEventos() = runBlocking {
        sembrarBase()

        val emisiones = mutableListOf<List<EventoEntity>>()
        val job = launch(Dispatchers.Default) {
            eventoDao.observarPorProyecto("proyecto-1")
                .take(2)
                .toList(emisiones)
        }

        // Dar tiempo a que el Flow registre el observer antes de escribir.
        delay(300L)
        eventoDao.insertar(eventoDemo(id = "evento-2", fechaInicio = 20_000L))
        withTimeout(5_000L) { job.join() }

        assertEquals(1, emisiones[0].size)
        assertEquals(2, emisiones[1].size)
    }

    // ------------------------------------------------------------------
    // Proyecciones y métricas
    // ------------------------------------------------------------------

    @Test
    fun resumenEventoYDetalleAsistencia() = runBlocking {
        sembrarBase()
        participanteDao.insertar(
            participanteDemo(
                id = "participante-2",
                rut = "22222222-2",
                qrToken = "QR-2",
                nombreCompleto = "Segundo Participante"
            )
        )
        inscripcionDao.insertar(
            inscripcionDemo(
                id = "inscripcion-2",
                participanteId = "participante-2",
                qrToken = "QR-2",
                nombreParticipante = "Segundo Participante"
            )
        )
        asistenciaDao.insertar(asistenciaDemo(satisfaccion = 5, utilidad = 4))

        val resumen = eventoDao.observarResumenes().first().single()
        assertEquals("Proyecto Demo", resumen.tituloProyecto)
        assertEquals(2, resumen.totalInscritos)
        assertEquals(1, resumen.totalAsistencias)
        assertEquals(1, resumen.pendientesSync)

        val detalle = asistenciaDao.observarDetallePorEvento("evento-1").first().single()
        assertEquals("asistencia-1", detalle.asistenciaId)
        assertEquals("Taller Demo", detalle.eventoNombre)
        assertEquals("Participante Ficticio", detalle.nombreParticipante)
        assertEquals("Voluntario Ficticio", detalle.nombreVoluntario)
        assertEquals(5, detalle.satisfaccion)
        assertEquals(4, detalle.utilidad)
        assertFalse(detalle.isSynced)

        assertEquals(1, asistenciaDao.observarDetallePendientes().first().size)
        assertEquals(1, asistenciaDao.observarPendientesSync().first())
    }

    @Test
    fun metricasGlobalesReflejanDatosYEncuestas() = runBlocking {
        sembrarBase()
        asistenciaDao.insertar(asistenciaDemo(satisfaccion = 5, utilidad = 4))
        inscripcionDao.actualizarEstado(
            id = "inscripcion-1",
            estado = EstadoInscripcion.ACREDITADO,
            isSynced = false,
            updatedAt = 20_000L
        )

        val metricas = metricasDao.obtenerMetricas()
        assertEquals(1, metricas.totalProyectos)
        assertEquals(1, metricas.totalEventos)
        assertEquals(1, metricas.totalParticipantes)
        assertEquals(1, metricas.totalInscripciones)
        assertEquals(1, metricas.totalAsistencias)
        assertEquals(1, metricas.asistenciasPendientesSync)
        assertEquals(5.0, metricas.promedioSatisfaccion ?: 0.0, 0.001)
        assertEquals(4.0, metricas.promedioUtilidad ?: 0.0, 0.001)
        assertEquals(1.0, metricas.tasaAsistencia, 0.001)

        assertEquals(1, metricasDao.observarMetricas().first().totalAsistencias)
    }

    // ------------------------------------------------------------------
    // Write-through de campos desnormalizados
    // ------------------------------------------------------------------

    @Test
    fun writeThroughDeCamposDesnormalizados() = runBlocking {
        sembrarBase()

        proyectoDao.actualizarTitulo("proyecto-1", "Proyecto Renombrado", false, 40_000L)
        eventoDao.actualizarTituloProyecto("proyecto-1", "Proyecto Renombrado", false, 40_000L)
        assertEquals(
            "Proyecto Renombrado",
            eventoDao.obtenerPorId("evento-1")?.tituloProyecto
        )

        participanteDao.actualizarNombre("participante-1", "Nombre Nuevo", false, 41_000L)
        inscripcionDao.actualizarNombreParticipante(
            "participante-1",
            "Nombre Nuevo",
            false,
            41_000L
        )
        participanteDao.actualizarQrToken("participante-1", "QR-ROTADO", false, 42_000L)
        inscripcionDao.actualizarQrTokenPorParticipante(
            "participante-1",
            "QR-ROTADO",
            false,
            42_000L
        )

        val inscripcion = inscripcionDao.obtenerPorId("inscripcion-1")!!
        assertEquals("Nombre Nuevo", inscripcion.nombreParticipante)
        assertEquals("QR-ROTADO", inscripcion.qrToken)
        assertFalse(inscripcion.isSynced)
        assertEquals(42_000L, inscripcion.updatedAt)
    }

    @Test
    fun applicationExponeTodosLosDaos() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as SyncPassApplication

        assertNotNull(app.container.usuarioDao)
        assertNotNull(app.container.proyectoDao)
        assertNotNull(app.container.eventoDao)
        assertNotNull(app.container.participanteDao)
        assertNotNull(app.container.inscripcionDao)
        assertNotNull(app.container.asistenciaDao)
        assertNotNull(app.container.metricasDao)
    }

    // ------------------------------------------------------------------
    // Datos ficticios de apoyo
    // ------------------------------------------------------------------

    private suspend fun sembrarBase() {
        usuarioDao.upsert(usuarioCoordinador())
        usuarioDao.upsert(usuarioVoluntario())
        proyectoDao.insertar(proyectoDemo())
        eventoDao.insertar(eventoDemo())
        participanteDao.insertar(participanteDemo())
        inscripcionDao.insertar(inscripcionDemo())
    }

    private fun usuarioCoordinador() = UsuarioEntity(
        id = "usuario-coord",
        username = "coord.demo",
        nombreCompleto = "Coordinadora Ficticia",
        email = "coord@demo.cl",
        rol = RolUsuario.COORDINADOR,
        activo = true,
        createdAt = 1_000L,
        isSynced = true,
        updatedAt = 1_000L
    )

    private fun usuarioVoluntario() = UsuarioEntity(
        id = "usuario-vol",
        username = "voluntario.demo",
        nombreCompleto = "Voluntario Ficticio",
        email = "voluntario@demo.cl",
        rol = RolUsuario.VOLUNTARIO,
        activo = true,
        createdAt = 1_000L,
        isSynced = true,
        updatedAt = 1_000L
    )

    private fun proyectoDemo(
        id: String = "proyecto-1",
        titulo: String = "Proyecto Demo"
    ) = ProyectoEntity(
        id = id,
        coordinadorId = "usuario-coord",
        titulo = titulo,
        descripcion = "Proyecto ficticio para pruebas",
        estado = EstadoProyecto.ACTIVO,
        fechaInicio = 1_000L,
        fechaFin = 100_000L,
        isSynced = true,
        updatedAt = 1_000L
    )

    private fun eventoDemo(
        id: String = "evento-1",
        proyectoId: String = "proyecto-1",
        fechaInicio: Long = 10_000L
    ) = EventoEntity(
        id = id,
        proyectoId = proyectoId,
        tituloProyecto = "Proyecto Demo",
        titulo = "Taller Demo",
        descripcion = "Evento ficticio para pruebas",
        lugar = "Sede Demo",
        latitud = -33.4489,
        longitud = -70.6693,
        radioToleranciaMetros = 200,
        fechaInicio = fechaInicio,
        fechaFin = fechaInicio + 3_600_000L,
        estado = EstadoEvento.PROGRAMADO,
        isSynced = true,
        updatedAt = 1_000L
    )

    private fun participanteDemo(
        id: String = "participante-1",
        rut: String? = "11111111-1",
        qrToken: String = "QR-1",
        nombreCompleto: String = "Participante Ficticio"
    ) = ParticipanteEntity(
        id = id,
        rut = rut,
        nombreCompleto = nombreCompleto,
        email = "participante@demo.cl",
        telefono = "+56900000000",
        qrToken = qrToken,
        createdAt = 1_000L,
        isSynced = true,
        updatedAt = 1_000L
    )

    private fun inscripcionDemo(
        id: String = "inscripcion-1",
        eventoId: String = "evento-1",
        participanteId: String = "participante-1",
        qrToken: String = "QR-1",
        nombreParticipante: String = "Participante Ficticio",
        estado: EstadoInscripcion = EstadoInscripcion.PENDIENTE
    ) = InscripcionEntity(
        id = id,
        eventoId = eventoId,
        participanteId = participanteId,
        nombreParticipante = nombreParticipante,
        qrToken = qrToken,
        tipoRegistro = TipoInscripcion.PREVIO,
        estado = estado,
        timestampInscripcion = 5_000L,
        isSynced = true,
        updatedAt = 5_000L
    )

    private fun asistenciaDemo(
        id: String = "asistencia-1",
        inscripcionId: String = "inscripcion-1",
        voluntarioId: String = "usuario-vol",
        satisfaccion: Int? = null,
        utilidad: Int? = null,
        isSynced: Boolean = false,
        timestampSync: Long? = null
    ) = AsistenciaEntity(
        id = id,
        inscripcionId = inscripcionId,
        voluntarioId = voluntarioId,
        timestampMarcacion = 15_000L,
        latitudMarcacion = -33.4489,
        longitudMarcacion = -70.6693,
        precisionGpsMetros = 12.0,
        distanciaEventoMetros = 35.0,
        satisfaccion = satisfaccion,
        utilidad = utilidad,
        comentarios = null,
        isSynced = isSynced,
        timestampSync = timestampSync,
        updatedAt = 15_000L
    )
}
