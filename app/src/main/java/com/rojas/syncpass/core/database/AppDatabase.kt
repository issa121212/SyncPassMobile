package com.rojas.syncpass.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rojas.syncpass.BuildConfig
import com.rojas.syncpass.data.local.dao.AsistenciaDao
import com.rojas.syncpass.data.local.dao.EventoDao
import com.rojas.syncpass.data.local.dao.InscripcionDao
import com.rojas.syncpass.data.local.dao.MetricasDao
import com.rojas.syncpass.data.local.dao.ParticipanteDao
import com.rojas.syncpass.data.local.dao.ProyectoDao
import com.rojas.syncpass.data.local.dao.UsuarioDao
import com.rojas.syncpass.data.local.entity.AsistenciaEntity
import com.rojas.syncpass.data.local.entity.EventoEntity
import com.rojas.syncpass.data.local.entity.InscripcionEntity
import com.rojas.syncpass.data.local.entity.ParticipanteEntity
import com.rojas.syncpass.data.local.entity.ProyectoEntity
import com.rojas.syncpass.data.local.entity.UsuarioEntity

@Database(
    entities = [
        UsuarioEntity::class,
        ProyectoEntity::class,
        EventoEntity::class,
        ParticipanteEntity::class,
        InscripcionEntity::class,
        AsistenciaEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao

    abstract fun proyectoDao(): ProyectoDao

    abstract fun eventoDao(): EventoDao

    abstract fun participanteDao(): ParticipanteDao

    abstract fun inscripcionDao(): InscripcionDao

    abstract fun asistenciaDao(): AsistenciaDao

    abstract fun metricasDao(): MetricasDao

    companion object {
        private const val NOMBRE_DB = "syncpass.db"

        @Volatile
        private var instancia: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: construir(context.applicationContext).also { instancia = it }
            }

        private fun construir(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                NOMBRE_DB
            )

            // Durante el desarrollo el esquema puede cambiar entre fases.
            // En release las migraciones deben ser explícitas: sin fallback destructivo.
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration(dropAllTables = true)
            }

            return builder.build()
        }
    }
}
