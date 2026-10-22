package com.rojas.syncpass.core.time

/**
 * Fuente de tiempo inyectable.
 *
 * Existe por dos motivos:
 * 1. Los repositorios escriben `updatedAt`/`createdAt`; con un provider se pueden
 *    testear sin depender del reloj real.
 * 2. Todo el modelo guarda epoch millis **UTC**. Centralizar el "ahora" evita
 *    que cada capa llame a `System.currentTimeMillis()` por su cuenta.
 */
fun interface TimeProvider {
    fun ahoraMillis(): Long
}

/** Implementación de producción: reloj del sistema. */
object SystemTimeProvider : TimeProvider {
    override fun ahoraMillis(): Long = System.currentTimeMillis()
}
