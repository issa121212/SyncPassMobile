package com.rojas.syncpass.data.repository

import com.rojas.syncpass.core.time.TimeProvider
import com.rojas.syncpass.data.local.dao.UsuarioDao
import com.rojas.syncpass.data.local.entity.UsuarioEntity
import com.rojas.syncpass.domain.model.RolUsuario
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de usuarios.
 *
 * Además del CRUD básico, encapsula la lección de la Fase 1: `@Upsert` resuelve
 * conflictos por **clave primaria**, así que un `username` repetido con otro
 * `id` no lanza excepción ni inserta la fila. El repositorio valida el username
 * antes de escribir para que el problema no quede silencioso.
 */
class UsuarioRepository(
    private val usuarioDao: UsuarioDao,
    private val timeProvider: TimeProvider
) {

    fun observarUsuarios(): Flow<List<UsuarioEntity>> = usuarioDao.observarTodos()

    suspend fun obtenerPorId(id: String): UsuarioEntity? = usuarioDao.obtenerPorId(id)

    suspend fun obtenerPorUsername(username: String): UsuarioEntity? =
        usuarioDao.obtenerPorUsername(username)

    suspend fun contar(): Int = usuarioDao.contar()

    /**
     * Upsert de datos que vienen del servidor (login) o del seed.
     * Normalmente nace en `isSynced = true` porque la fuente es remota.
     *
     * Valida que el `username` no pertenezca a otro `id`; sin esta validación,
     * el upsert sería un no-op silencioso.
     */
    suspend fun guardarDesdeServidor(usuario: UsuarioEntity): ResultadoOperacion<UsuarioEntity> {
        val existente = usuarioDao.obtenerPorUsername(usuario.username)
        if (existente != null && existente.id != usuario.id) {
            return fallo(ErrorOperacion.USERNAME_DUPLICADO)
        }

        return intentarDevolviendo(valorEnExito = { usuario }) {
            usuarioDao.upsert(usuario)
        }
    }

    /**
     * Crea un usuario local (no provisto por el servidor) con
     * `isSynced = false` y `updatedAt` del reloj inyectado.
     */
    suspend fun crearLocal(
        id: String,
        username: String,
        nombreCompleto: String,
        rol: RolUsuario,
        email: String? = null,
        activo: Boolean = true
    ): ResultadoOperacion<UsuarioEntity> {
        val usernameLimpio = username.trim()
        val nombreLimpio = nombreCompleto.trim()
        if (usernameLimpio.isEmpty() || nombreLimpio.isEmpty()) {
            return fallo(ErrorOperacion.OPERACION_INVALIDA)
        }
        if (usuarioDao.obtenerPorUsername(usernameLimpio) != null) {
            return fallo(ErrorOperacion.USERNAME_DUPLICADO)
        }

        val ahora = timeProvider.ahoraMillis()
        val usuario = UsuarioEntity(
            id = id,
            username = usernameLimpio,
            nombreCompleto = nombreLimpio,
            email = email?.trim()?.ifEmpty { null },
            rol = rol,
            activo = activo,
            createdAt = ahora,
            isSynced = false,
            updatedAt = ahora
        )

        return intentarDevolviendo(valorEnExito = { usuario }) {
            usuarioDao.upsert(usuario)
        }
    }

    /**
     * Marca un usuario como sincronizado. Lo usará el `SyncRepository` de la
     * Fase 9 cuando el servidor confirme la subida.
     */
    suspend fun marcarSincronizado(id: String): ResultadoOperacion<Int> = intentarSuspend {
        val usuario = usuarioDao.obtenerPorId(id)
            ?: throw OperacionRechazada(ErrorOperacion.NO_ENCONTRADO)
        usuarioDao.upsert(usuario.copy(isSynced = true, updatedAt = timeProvider.ahoraMillis()))
        1
    }
}
