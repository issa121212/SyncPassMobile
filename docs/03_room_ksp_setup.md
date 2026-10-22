# 03 — Room + KSP con AGP 9: spike y configuración

> **Fase:** 0 (spike) / Fase 1 (configuración aplicada)  
> **Fecha:** 2026-10-22  
> **Objetivo:** determinar la combinación estable de KSP, Room 2.7.1 y AGP 9.3.3 antes de crear el esquema real.

---

## 1. Contexto

El proyecto usa **AGP 9.3.3 con Kotlin integrado**. No aplica `org.jetbrains.kotlin.android`; solo el plugin de Compose (`org.jetbrains.kotlin.plugin.compose`). Esto cambia la forma en que KSP registra las fuentes generadas y fue el principal riesgo del build.

El spike se ejecutó en una **copia temporal** para no dejar código de prueba en el proyecto:

```text
C:\Users\admn\AppData\Local\Temp\opencode\SyncPass-ksp-spike
```

---

## 2. Matriz de pruebas

| # | KSP | `android.disallowKotlinSourceSets=false` | Resultado |
|---|---|---|---|
| A | `2.2.10-2.0.2` | No | ❌ Falla en configuración |
| A2 | `2.2.10-2.0.2` | Sí | ✅ Build verde, con warning experimental |
| B | `2.3.10` | No | ✅ Build verde limpio (elegido) |

---

## 3. Candidato A — KSP 2.2.10-2.0.2

Es la versión que declara internamente AGP 9.3.3 en su POM. Sin embargo, al aplicar el plugin:

```text
com.android.builder.errors.EvalIssueException: Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin.
Kotlin source set 'debug' contains: [
  ...\app\build\generated\ksp\debug\kotlin,
  ...\app\build\generated\ksp\debug\java
]
Solution: Use android.sourceSets DSL instead.
To suppress this error, set android.disallowKotlinSourceSets=false in gradle.properties.
```

**Causa:** el plugin KSP 2.2.x agrega las fuentes generadas con `kotlin.sourceSets`, API que AGP 9 bloquea cuando Kotlin es integrado.

### Workaround A2

Agregar en `gradle.properties`:

```properties
android.disallowKotlinSourceSets=false
```

Resultado:

```text
BUILD SUCCESSFUL in 1m 23s
39 actionable tasks: 39 executed
```

Pero aparece:

```text
WARNING: The option setting 'android.disallowKotlinSourceSets=false' is experimental.
The current default is 'true'.
```

Además, en esa corrida se observó:

```text
Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_24 JVM target
```

El workaround funciona, pero depende de una opción experimental y deja un warning de target JVM. Se conserva solo como **fallback**.

---

## 4. Candidato B — KSP 2.3.10 (elegido)

KSP 2.3.10 incluye mejoras de integración con AGP 9/Kotlin integrado (la línea 2.3.6+ dejó de depender de `KotlinBaseApiPlugin`, y 2.3.10 corrigió la resolución de R class con AGP 9). Se probó **sin** `android.disallowKotlinSourceSets`.

**Build inicial:**

```text
BUILD SUCCESSFUL in 1m 26s
39 actionable tasks: 39 executed
Configuration cache entry stored.
```

**Build limpio de verificación:**

```powershell
.\gradlew.bat clean :app:assembleDebug
```

```text
BUILD SUCCESSFUL in 11s
40 actionable tasks: 40 executed
Configuration cache entry stored.
```

**Tests unitarios base:**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

```text
BUILD SUCCESSFUL in 6s
```

### Evidencia de generación

> Las rutas siguientes son relativas a la **copia temporal del spike**. En el proyecto real se generarán en Fase 1.

Código generado por Room/KSP:

```text
app\build\generated\ksp\debug\kotlin\com\rojas\syncpass\spike\SpikeDao_Impl.kt
app\build\generated\ksp\debug\kotlin\com\rojas\syncpass\spike\SpikeDatabase_Impl.kt
```

Schema exportado:

```text
app\schemas\com.rojas.syncpass.spike.SpikeDatabase\1.json
```

APK debug:

```text
app-debug.apk  (~19.5 MB)
```

---

## 5. Configuración aplicada en Fase 1

### `gradle/libs.versions.toml`

```toml
[versions]
ksp = "2.3.10"
room = "2.7.1"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### `build.gradle.kts` raíz

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

### `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}
```

### `AppDatabase` mínimo

```kotlin
@Database(
    entities = [UsuarioEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
}
```

> **No agregar** `android.disallowKotlinSourceSets=false` si se usa KSP 2.3.10. Solo es necesario con el fallback 2.2.10-2.0.2.

### Verificación en el proyecto real

La configuración se aplicó en Fase 1 y se verificó con:

```text
:app:clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
BUILD SUCCESSFUL in 18s

:app:connectedDebugAndroidTest
Starting 6 tests on moto g82 5G - 13
Finished 6 tests on moto g82 5G - 13
BUILD SUCCESSFUL in 10s
```

Room generó `AppDatabase_Impl.kt`, `UsuarioDao_Impl.kt` y `app/schemas/com.rojas.syncpass.core.database.AppDatabase/1.json`. El detalle está en [`04_room_setup.md`](04_room_setup.md).

---

## 6. Troubleshooting

| Síntoma | Causa probable | Solución |
|---|---|---|
| `Using kotlin.sourceSets DSL ... not allowed` | KSP 2.2.x con AGP 9 | Subir a KSP 2.3.10 o usar `android.disallowKotlinSourceSets=false` |
| `Unresolved reference: ksp` en `app/build.gradle.kts` | Plugin no aplicado | Agregar `alias(libs.plugins.ksp)` en `plugins` |
| No se genera el schema JSON | Falta `exportSchema = true` o `room.schemaLocation` | Revisar `@Database` y el bloque `ksp { }` |
| No aparecen `*_Impl.kt` | `kspDebugKotlin` no se ejecutó | Ejecutar `:app:kspDebugKotlin` y revisar `app/build/generated/ksp/` |
| Warning por JDK 25 | Kotlin aún no soporta target 25 | Alinear `jvmTarget`/toolchain en Fase 1 o usar JDK 21 LTS |
| Configuration cache falla con KSP | Versión/plugin incompatible | Probar `--no-configuration-cache`; en KSP 2.3.10 funcionó correctamente |
| Room no encuentra una entidad | Entidad fuera de `entities = [...]` | Agregarla al `@Database` |

---

## 7. Decisión

**Se usa KSP `2.3.10` + Room `2.7.1`** (aplicado en Fase 1).

Motivos:

- Compila sin flags experimentales.
- Genera DAO/DB `_Impl` y schema correctamente.
- Mantiene configuration cache.
- Está alineado con las correcciones de AGP 9 de la línea 2.3.x.

**Fallback documentado:** KSP `2.2.10-2.0.2` + `android.disallowKotlinSourceSets=false`, solo si 2.3.10 presenta un problema específico en Fase 1.

---

## 8. Cómo reproducir el spike

```powershell
# Copia temporal (si aún existe)
cd C:\Users\admn\AppData\Local\Temp\opencode\SyncPass-ksp-spike

# Build limpio
.\gradlew.bat clean :app:assembleDebug

# Tests base
.\gradlew.bat :app:testDebugUnitTest

# Ver generación
Get-ChildItem -Recurse app\build\generated\ksp\debug\kotlin
Get-ChildItem -Recurse app\schemas
```

> El proyecto real no fue modificado en Fase 0. La configuración anterior se aplicó formalmente en Fase 1; ver `04_room_setup.md`.
