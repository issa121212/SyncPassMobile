package com.rojas.syncpass.core.id

import java.util.UUID

/**
 * Generador de identificadores inyectable.
 *
 * Las filas creadas en cliente (walk-ins, CRUD offline, seeds) usan UUID, no
 * autoincrementales: así el cliente puede crear filas sin red y el servidor
 * puede hacer upsert por ID sin colisiones.
 */
fun interface IdProvider {
    fun nuevoId(): String
}

/** Implementación de producción: UUID v4 en texto. */
object UuidProvider : IdProvider {
    override fun nuevoId(): String = UUID.randomUUID().toString()
}
