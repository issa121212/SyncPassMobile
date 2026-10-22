package com.rojas.syncpass.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rojas.syncpass.domain.model.RolUsuario

/**
 * Usuario local de SyncPass.
 *
 * El [id] proviene del claim `sub` del JWT para que el servidor y el cliente
 * compartan la misma identidad sin depender de un autoincremental local.
 *
 * [isSynced] se mantiene por coherencia con el resto de entidades mutables:
 * un usuario descargado del servidor o del seed nace en `true`; un usuario
 * creado localmente debe nacer en `false` hasta sincronizarse.
 */
@Entity(
    tableName = "usuario",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class UsuarioEntity(
    @PrimaryKey val id: String,
    val username: String,
    val nombreCompleto: String,
    val email: String? = null,
    val rol: RolUsuario,
    val activo: Boolean = true,
    val createdAt: Long,
    val isSynced: Boolean,
    val updatedAt: Long
)
