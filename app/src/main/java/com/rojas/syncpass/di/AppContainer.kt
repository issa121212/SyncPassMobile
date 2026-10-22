package com.rojas.syncpass.di

import android.content.Context
import com.rojas.syncpass.core.database.AppDatabase
import com.rojas.syncpass.core.id.IdProvider
import com.rojas.syncpass.core.id.UuidProvider
import com.rojas.syncpass.core.time.SystemTimeProvider
import com.rojas.syncpass.core.time.TimeProvider
import com.rojas.syncpass.data.local.dao.AsistenciaDao
import com.rojas.syncpass.data.local.dao.EventoDao
import com.rojas.syncpass.data.local.dao.InscripcionDao
import com.rojas.syncpass.data.local.dao.MetricasDao
import com.rojas.syncpass.data.local.dao.ParticipanteDao
import com.rojas.syncpass.data.local.dao.ProyectoDao
import com.rojas.syncpass.data.local.dao.UsuarioDao
import com.rojas.syncpass.data.local.seed.SeedDataInitializer
import com.rojas.syncpass.data.repository.AsistenciaRepository
import com.rojas.syncpass.data.repository.ParticipanteRepository
import com.rojas.syncpass.data.repository.ProyectoRepository
import com.rojas.syncpass.data.repository.RoomTransaccionador
import com.rojas.syncpass.data.repository.Transaccionador
import com.rojas.syncpass.data.repository.UsuarioRepository

/**
 * Contenedor manual de dependencias. Evita introducir Hilt en una app pequeña
 * y mantiene el grafo de datos explícito.
 *
 * Orden de construcción: infraestructura (DB, providers, transaccionador) →
 * DAOs → repositorios → seed. `ProyectoRepository` se crea antes que
 * `ParticipanteRepository` porque este último lo usa para el write-through.
 */
class AppContainer(context: Context) {

    // Infraestructura
    val database: AppDatabase = AppDatabase.getInstance(context)
    val timeProvider: TimeProvider = SystemTimeProvider
    val idProvider: IdProvider = UuidProvider
    val transaccionador: Transaccionador = RoomTransaccionador(database)

    // DAOs
    val usuarioDao: UsuarioDao = database.usuarioDao()
    val proyectoDao: ProyectoDao = database.proyectoDao()
    val eventoDao: EventoDao = database.eventoDao()
    val participanteDao: ParticipanteDao = database.participanteDao()
    val inscripcionDao: InscripcionDao = database.inscripcionDao()
    val asistenciaDao: AsistenciaDao = database.asistenciaDao()
    val metricasDao: MetricasDao = database.metricasDao()

    // Repositorios
    val usuarioRepository = UsuarioRepository(usuarioDao, timeProvider)

    val proyectoRepository = ProyectoRepository(
        proyectoDao = proyectoDao,
        eventoDao = eventoDao,
        inscripcionDao = inscripcionDao,
        participanteDao = participanteDao,
        asistenciaDao = asistenciaDao,
        transaccionador = transaccionador,
        timeProvider = timeProvider,
        idProvider = idProvider
    )

    val participanteRepository = ParticipanteRepository(
        participanteDao = participanteDao,
        proyectoRepository = proyectoRepository,
        transaccionador = transaccionador,
        timeProvider = timeProvider,
        idProvider = idProvider
    )

    val asistenciaRepository = AsistenciaRepository(
        asistenciaDao = asistenciaDao,
        inscripcionDao = inscripcionDao,
        timeProvider = timeProvider
    )

    // Seed de datos ficticios (idempotente)
    val seedDataInitializer = SeedDataInitializer(database, transaccionador)
}
