package com.rojas.syncpass.core.database

import androidx.room.TypeConverter
import com.rojas.syncpass.domain.model.EstadoEvento
import com.rojas.syncpass.domain.model.EstadoInscripcion
import com.rojas.syncpass.domain.model.EstadoProyecto
import com.rojas.syncpass.domain.model.RolUsuario
import com.rojas.syncpass.domain.model.TipoInscripcion

/**
 * Todos los enums se guardan como `name` (`TEXT`), nunca como ordinal.
 * Room elige el converter por el tipo de retorno, por eso pueden convivir
 * varios métodos `String -> Enum`.
 */
class Converters {

    @TypeConverter
    fun rolUsuarioAString(rol: RolUsuario): String = rol.name

    @TypeConverter
    fun stringARolUsuario(valor: String): RolUsuario = RolUsuario.valueOf(valor)

    @TypeConverter
    fun estadoProyectoAString(estado: EstadoProyecto): String = estado.name

    @TypeConverter
    fun stringAEstadoProyecto(valor: String): EstadoProyecto = EstadoProyecto.valueOf(valor)

    @TypeConverter
    fun estadoEventoAString(estado: EstadoEvento): String = estado.name

    @TypeConverter
    fun stringAEstadoEvento(valor: String): EstadoEvento = EstadoEvento.valueOf(valor)

    @TypeConverter
    fun tipoInscripcionAString(tipo: TipoInscripcion): String = tipo.name

    @TypeConverter
    fun stringATipoInscripcion(valor: String): TipoInscripcion = TipoInscripcion.valueOf(valor)

    @TypeConverter
    fun estadoInscripcionAString(estado: EstadoInscripcion): String = estado.name

    @TypeConverter
    fun stringAEstadoInscripcion(valor: String): EstadoInscripcion = EstadoInscripcion.valueOf(valor)
}
