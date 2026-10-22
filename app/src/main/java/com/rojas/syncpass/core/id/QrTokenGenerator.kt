package com.rojas.syncpass.core.id

/**
 * Generador de tokens QR legibles para SyncPass.
 *
 * Formato: `SP-<12 caracteres base32>`. Es legible al dictarlo por teléfono y
 * suficientemente largo para no colisionar en el volumen esperado.
 *
 * **No es criptográfico.** En la v1 el QR es un identificador local; un QR
 * firmado y rotativo con TTL es deuda documentada para producción.
 * Se usa [java.security.SecureRandom] para que el token no sea predecible
 * por un tercero que observe uno anterior.
 */
object QrTokenGenerator {

    private const val PREFIJO = "SP-"
    private const val LONGITUD = 12
    private const val ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // sin I, L, O, 0, 1

    private val random = java.security.SecureRandom()

    fun generar(): String {
        val sb = StringBuilder(PREFIJO)
        repeat(LONGITUD) {
            sb.append(ALFABETO[random.nextInt(ALFABETO.length)])
        }
        return sb.toString()
    }
}
