package com.rojas.syncpass.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rojas.syncpass.domain.model.EstadoProyecto

/**
 * Proyecto comunitario que agrupa eventos.
 *
 * [coordinadorId] apunta al usuario responsable. La FK usa `RESTRICT` para no
 * borrar un coordinador que todavía tiene proyectos (se prefiere desactivarlo).
 * [titulo] es la fuente de verdad de la copia desnormalizada
 * `Evento.tituloProyecto`.
 */
@Entity(
    tableName = "proyecto",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["coordinadorId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["coordinadorId"]),
        Index(value = ["estado"])
    ]
)
data class ProyectoEntity(
    @PrimaryKey val id: String,
    val coordinadorId: String,
    val titulo: String,
    val descripcion: String? = null,
    val estado: EstadoProyecto,
    val fechaInicio: Long? = null,
    val fechaFin: Long? = null,
    val isSynced: Boolean,
    val updatedAt: Long
)
