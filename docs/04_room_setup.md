# 04 — Infraestructura Room + vertical slice `Usuario`

> **Fase:** 1  \
> **Estado:** ✅ Completada  \
> **Fecha:** 2026-10-22  \
> **Objetivo:** introducir Room 2.7.1 con KSP en el proyecto real y validar el flujo completo con la entidad `Usuario`, sin cambiar todavía la UI ni la sesión.

---

## 1. Qué se hizo

| Archivo | Cambio |
|---|---|
| `gradle/libs.versions.toml` | Versiones `ksp = 2.3.10` y `room = 2.7.1`; librerías de Room y plugin KSP. |
| `build.gradle.kts` (raíz) | `alias(libs.plugins.ksp) apply false`. |
| `app/build.gradle.kts` | Plugin KSP, bloque `ksp { room.schemaLocation }` y dependencias de Room. |
| `app/src/main/AndroidManifest.xml` | `SyncPassApplication`, `INTERNET` y `ACCESS_NETWORK_STATE` explícitos. |
| `domain/model/RolUsuario.kt` | Enum `ADMINISTRADOR`, `COORDINADOR`, `VOLUNTARIO`. |
| `data/local/entity/UsuarioEntity.kt` | Tabla `usuario` con PK `String`, `username` único, `rol` convertido a TEXT e `isSynced`/`updatedAt`. |
| `data/local/dao/UsuarioDao.kt` | Consultas suspend/Flow, `@Upsert` y borrado por id. |
| `core/database/Converters.kt` | Conversión enum ↔ TEXT con `valueOf`, sin ordinales. |
| `core/database/AppDatabase.kt` | `@Database(version = 1, exportSchema = true)`, singleton y builder. |
| `di/AppContainer.kt` | Contenedor manual que expone `database` y `usuarioDao`. |
| `SyncPassApplication.kt` | Inicializa `AppContainer` al arrancar el proceso. |
| `androidTest/.../AppDatabaseTest.kt` | 5 pruebas instrumentadas de CRUD, upsert, unicidad, conversión de rol y DI. |
| `app/schemas/.../1.json` | Schema exportado y versionado. |

---

## 2. Configuración aplicada

### Versiones

```toml
ksp = "2.3.10"
room = "2.7.1"
```

### Dependencias

```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
androidTestImplementation(libs.androidx.room.testing)
```

### Exportación de schema

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

El schema queda en:

```text
app/schemas/com.rojas.syncpass.core.database.AppDatabase/1.json
```

**Por qué:** permite revisar el SQL real, detectar cambios de esquema y preparar migraciones antes de release.

---

## 3. Modelo `Usuario`

```kotlin
@Entity(
    tableName = "usuario",
    indices = [Index(value = ["username"], unique = true)]
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
```

### Tabla generada

```sql
CREATE TABLE IF NOT EXISTS `usuario` (
    `id` TEXT NOT NULL,
    `username` TEXT NOT NULL,
    `nombreCompleto` TEXT NOT NULL,
    `email` TEXT,
    `rol` TEXT NOT NULL,
    `activo` INTEGER NOT NULL,
    `createdAt` INTEGER NOT NULL,
    `isSynced` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_usuario_username`
    ON `usuario` (`username`);
```

**Decisiones:**

- `id` es `String` para usar el `sub` del JWT o un UUID generado en cliente.
- `rol` se guarda como `TEXT` (`ADMINISTRADOR`, `COORDINADOR`, `VOLUNTARIO`), nunca como ordinal.
- `username` es único porque será la credencial visible del usuario.
- `createdAt`/`updatedAt` en epoch millis UTC, coherentes con el resto del modelo.
- `isSynced` se incluye porque `Usuario` también es mutable; nace en `true` si viene del servidor/login y en `false` si se crea localmente.
- `activo` permite deshabilitar usuarios sin borrar historial.

---

## 4. `AppDatabase` y ciclo de vida

```kotlin
@Database(
    entities = [UsuarioEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao

    companion object {
        fun getInstance(context: Context): AppDatabase { /* singleton */ }
    }
}
```

- Singleton con `@Volatile` + `synchronized` para una sola instancia por proceso.
- `context.applicationContext` evita retener Activities.
- **Debug:** `fallbackToDestructiveMigration(dropAllTables = true)` para iterar el esquema entre fases.
- **Release:** no hay fallback destructivo; los cambios requieren `Migration` explícita.

> El esquema v1 todavía es un vertical slice. La Fase 2 consolidará todas las entidades. Mientras no exista una release, se puede desinstalar la app o borrar sus datos (`adb uninstall` / `pm clear`) y regenerar el schema v1; después de la entrega, cada cambio requiere migración. El fallback destructivo de debug cubre cambios de versión, no un cambio de hash con la misma versión.

---

## 5. `SyncPassApplication` y `AppContainer`

```kotlin
class SyncPassApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

```kotlin
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val usuarioDao: UsuarioDao = database.usuarioDao()
}
```

**Por qué:** la app no usaba `Application` ni inyección de dependencias. El contenedor manual mantiene el grafo explícito y evita introducir Hilt en esta etapa. Los ViewModels de fases posteriores recibirán el contenedor por factory.

---

## 6. Manifest

Se declararon explícitamente:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Y el `Application`:

```xml
<application
    android:name=".SyncPassApplication"
    ... >
```

**Por qué:** `INTERNET` llegaba de forma transitiva desde una dependencia; depender de eso es frágil. `ACCESS_NETWORK_STATE` será necesario para la sincronización de la Fase 9. La cámara y la ubicación se agregarán en la Fase 6.

---

## 7. DAO y hallazgo sobre `@Upsert`

Métodos principales:

```kotlin
@Query("SELECT * FROM usuario WHERE id = :id LIMIT 1")
suspend fun obtenerPorId(id: String): UsuarioEntity?

@Query("SELECT * FROM usuario WHERE username = :username LIMIT 1")
suspend fun obtenerPorUsername(username: String): UsuarioEntity?

@Query("SELECT * FROM usuario ORDER BY nombreCompleto")
fun observarTodos(): Flow<List<UsuarioEntity>>

@Upsert
suspend fun upsert(usuario: UsuarioEntity)
```

### Hallazgo

En Room 2.7.1, `@Upsert` resuelve conflictos por **clave primaria**. Si se intenta insertar otro `id` con un `username` ya existente:

- El índice `UNIQUE` impide la fila duplicada.
- Room **no propaga** una `SQLiteConstraintException` en ese caso; la operación queda sin insertar.

Por eso el test no asume una excepción. Valida el invariante real:

- `contar() == 1`.
- El `username` sigue perteneciendo al `id` original.
- El `id` nuevo no existe.

**Consecuencia para el dominio:** al crear usuarios locales (walk-ins o CRUD del coordinador), el repositorio/use case debe validar el `username` antes de llamar a `upsert`. Esto se implementará en la Fase 3. La base de datos sigue protegiendo la unicidad.

---

## 8. Pruebas

Archivo: `app/src/androidTest/java/com/rojas/syncpass/core/database/AppDatabaseTest.kt`

Se usa `Room.inMemoryDatabaseBuilder(...)` para no tocar la DB real del dispositivo.

| Test | Qué valida |
|---|---|
| `upsertYLecturaDeUsuario` | Inserción, lectura por id/username, conteo y emisión de `Flow`. |
| `upsertActualizaSinDuplicar` | Actualización por PK sin crear una segunda fila. |
| `usernameDuplicadoNoCreaFila` | Unicidad de `username`, sin fila fantasma. |
| `applicationInicializaContenedorConDao` | `SyncPassApplication` crea `AppContainer` y expone `UsuarioDao`. |
| `rolYFlagSyncSePersistenYSeRecuperan` | Conversión `RolUsuario` ↔ TEXT y persistencia de `isSynced`. |

### Comandos

```powershell
# Compilación limpia: app + APK de tests + tests JVM
.\gradlew.bat clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest

# Ejecutar tests instrumentados en dispositivo/emulador
.\gradlew.bat :app:connectedDebugAndroidTest

# Análisis estático
.\gradlew.bat :app:lintDebug
```

### Evidencia

```text
:app:clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
BUILD SUCCESSFUL in 18s
78 actionable tasks: 78 executed

:app:connectedDebugAndroidTest
Starting 6 tests on moto g82 5G - 13
Finished 6 tests on moto g82 5G - 13
BUILD SUCCESSFUL in 10s

:app:lintDebug
BUILD SUCCESSFUL in 15s
```

`lintDebug` no reportó errores. Solo warnings de versiones más nuevas disponibles (incluida Room 2.8.5, que no se adopta porque la versión solicitada es 2.7.1) y un `RedundantLabel` preexistente en la Activity; no afectan la Fase 1.

Resultado instrumentado:

```text
tests="6" failures="0" errors="0" skipped="0"
```

Los 6 tests incluyen `ExampleInstrumentedTest` + los 5 de `AppDatabaseTest`.

---

## 9. Verificación manual

1. Instalar el APK debug:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

2. Abrir la app:

   ```powershell
   adb shell am start -n com.rojas.syncpass/.MainActivity
   ```

3. Verificar que la pantalla de login sigue apareciendo y que no hay crash por la nueva `Application`.

4. Inspeccionar permisos/componentes:

   ```powershell
   adb shell dumpsys package com.rojas.syncpass | Select-String "INTERNET|ACCESS_NETWORK_STATE"
   ```

5. Para inspeccionar la DB real se necesita que alguna consulta la abra. En esta fase la app aún no lee `usuario`; el test instrumentado es la verificación efectiva. Desde la Fase 3 se podrá usar Database Inspector.

> **Fase 2:** con el esquema completo verifiqué con `run-as com.rojas.syncpass ls -la /data/user/0/com.rojas.syncpass` que `databases/` **todavía no existe** al abrir la app: Room es perezoso y crea el archivo en la primera consulta real. La validación del esquema en la Fase 2 se hace con los tests instrumentados, no con el archivo en disco.

**Resultado del smoke test (2026-10-22):** instalación `Success`, proceso activo (`pidof` devolvió PID), `mFocusedApp=com.rojas.syncpass/.MainActivity` y sin `FATAL EXCEPTION` en logcat. El login sigue arrancando con la nueva `Application`.

> La DB real se llama `syncpass.db` y se abrirá de forma perezosa en la primera consulta.

---

## 10. Por qué estas decisiones

| Decisión | Motivo |
|---|---|
| KSP 2.3.10 + Room 2.7.1 | Validado en Fase 0; sin flags experimentales. |
| `AppDatabase` singleton | Evitar múltiples pools/conexiones y costos de apertura. |
| `@TypeConverters` con `.name` | Un cambio de orden del enum no corrompe filas. |
| `fallbackToDestructiveMigration` solo debug | Iteración rápida sin comprometer release. |
| PK `String` | Compatibilidad con JWT/UUID y sincronización futura. |
| `username` único | Evita duplicados de identidad local. |
| `isSynced` + `updatedAt` en `Usuario` | Coherencia con la decisión de marcar todas las entidades mutables. |
| `@Upsert` | Inserción/actualización simple por PK para el login futuro. |
| `Application` + `AppContainer` | Punto único de composición para DAOs/repositorios. |
| Permisos explícitos | Independencia de permisos transitivos. |

---

## 11. Pendientes y próximos pasos

1. ~~**Fase 2:** agregar `Proyecto`, `Evento`, `Participante`, `Inscripcion` y `Asistencia`; consolidar el schema v1 antes de release.~~ ✅ Completado en `docs/05_esquema_entidades_daos.md`; el schema v1 quedó consolidado.
2. **Fase 3:** repositorios, seeds ficticios y validación de `username` antes de `upsert`.
3. **Fase 5:** persistir sesión y mapear roles del JWT a `RolUsuario`.
4. **Fase 6:** permisos de cámara/ubicación y flujo QR.
5. **Release:** eliminar el fallback destructivo y agregar migraciones explícitas.

---

## 12. Cómo revisar esta fase

```powershell
# 1. Configuración
Select-String -Path gradle\libs.versions.toml -Pattern 'ksp|room'
Select-String -Path app\build.gradle.kts -Pattern 'ksp|room'

# 2. Código
Get-Content app\src\main\java\com\rojas\syncpass\core\database\AppDatabase.kt
Get-Content app\src\main\java\com\rojas\syncpass\data\local\entity\UsuarioEntity.kt

# 3. Schema
Get-Content app\schemas\com.rojas.syncpass.core.database.AppDatabase\1.json

# 4. Tests
.\gradlew.bat :app:connectedDebugAndroidTest
```
