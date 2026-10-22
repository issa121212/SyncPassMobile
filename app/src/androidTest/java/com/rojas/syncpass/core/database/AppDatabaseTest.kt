package com.rojas.syncpass.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rojas.syncpass.SyncPassApplication
import com.rojas.syncpass.data.local.entity.UsuarioEntity
import com.rojas.syncpass.domain.model.RolUsuario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun crearDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun cerrarDb() {
        db.close()
    }

    @Test
    fun upsertYLecturaDeUsuario() = runBlocking {
        val usuario = usuarioDemo()
        db.usuarioDao().upsert(usuario)

        assertEquals(usuario, db.usuarioDao().obtenerPorId(usuario.id))
        assertEquals(usuario, db.usuarioDao().obtenerPorUsername(usuario.username))
        assertEquals(1, db.usuarioDao().contar())
        assertEquals(listOf(usuario), db.usuarioDao().observarTodos().first())
    }

    @Test
    fun upsertActualizaSinDuplicar() = runBlocking {
        val original = usuarioDemo()
        db.usuarioDao().upsert(original)
        db.usuarioDao().upsert(
            original.copy(
                nombreCompleto = "Nombre actualizado",
                updatedAt = 2L
            )
        )

        val actualizado = db.usuarioDao().obtenerPorId(original.id)
        assertEquals("Nombre actualizado", actualizado?.nombreCompleto)
        assertEquals(1, db.usuarioDao().contar())
    }

    @Test
    fun usernameDuplicadoNoCreaFila() = runBlocking {
        db.usuarioDao().upsert(usuarioDemo())
        val duplicado = usuarioDemo().copy(
            id = "usuario-2",
            nombreCompleto = "Otra Persona"
        )

        db.usuarioDao().upsert(duplicado)

        // El índice UNIQUE impide duplicar la fila. @Upsert resuelve por PK,
        // así que un username repetido con otro id no debe crear una fila nueva.
        assertEquals(1, db.usuarioDao().contar())
        assertEquals("usuario-1", db.usuarioDao().obtenerPorUsername("voluntario.demo")?.id)
        assertNull(db.usuarioDao().obtenerPorId("usuario-2"))
    }

    @Test
    fun applicationInicializaContenedorConDao() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as SyncPassApplication

        assertNotNull(app.container.usuarioDao)
    }

    @Test
    fun rolYFlagSyncSePersistenYSeRecuperan() = runBlocking {
        val usuario = usuarioDemo().copy(
            rol = RolUsuario.COORDINADOR,
            isSynced = false
        )
        db.usuarioDao().upsert(usuario)

        val recuperado = db.usuarioDao().obtenerPorId(usuario.id)
        assertEquals(RolUsuario.COORDINADOR, recuperado?.rol)
        assertFalse(recuperado?.isSynced ?: true)
    }

    private fun usuarioDemo() = UsuarioEntity(
        id = "usuario-1",
        username = "voluntario.demo",
        nombreCompleto = "Voluntaria Ficticia",
        email = "voluntaria@demo.cl",
        rol = RolUsuario.VOLUNTARIO,
        activo = true,
        createdAt = 1L,
        isSynced = true,
        updatedAt = 1L
    )
}
