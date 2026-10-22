package com.rojas.syncpass.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rojas.syncpass.domain.model.EstadoEvento

/**
 * Evento/taller de un proyecto.
 *
 * [tituloProyecto] está desnormalizado para listar eventos sin JOIN. Se
 * mantiene consistente cuando cambia `Proyecto.titulo` (write-through desde el
 * repositorio).
 *
 * Las coordenadas y [radioToleranciaMetros] permiten calcular el geofence en
 * terreno. El radio por defecto es 200 m; se guarda por evento para poder
 * ajustarlo sin cambiar el código.
 */
@Entity(
    tableName = "evento",
    foreignKeys = [
        ForeignKey(
            entity = ProyectoEntity::class,
            parentColumns = ["id"],
            childColumns = ["proyectoId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        // Índice compuesto: cubre la FK y las consultas "eventos de un proyecto
        // ordenados por fecha" / "eventos del día".
        Index(value = ["proyectoId", "fechaInicio"]),
        Index(value = ["estado"])
    ]
)
data class EventoEntity(
    @PrimaryKey val id: String,
    val proyectoId: String,
    val tituloProyecto: String,
    val titulo: String,
    val descripcion: String? = null,
    val lugar: String,
    val latitud: Double,
    val longitud: Double,
    val radioToleranciaMetros: Int = 200,
    val fechaInicio: Long,
    val fechaFin: Long,
    val estado: EstadoEvento,
    val isSynced: Boolean,
    val updatedAt: Long
)
