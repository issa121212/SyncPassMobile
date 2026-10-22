package com.rojas.syncpass.domain.model

/**
 * Roles funcionales de SyncPass. Se persisten como nombre (TEXT) para evitar
 * que un cambio de orden rompa los datos existentes.
 */
enum class RolUsuario {
    ADMINISTRADOR,
    COORDINADOR,
    VOLUNTARIO
}
