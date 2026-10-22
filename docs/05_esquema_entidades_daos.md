# 05 — Esquema completo, DAOs e índices

> **Fase:** 2  \
> **Estado:** ✅ Completada  \
> **Fecha:** 2026-10-22  \
> **Objetivo:** cerrar el esquema Room de la v1 con las seis entidades, sus DAOs, índices, claves foráneas, relaciones y proyecciones, más las pruebas que demuestran que las invariantes del MER se cumplen a nivel de base de datos.

---

## 1. Qué se hizo

| Archivo | Cambio |
|---|---|
| `domain/model/EstadoProyecto.kt` | Nuevo enum `BORRADOR`, `ACTIVO`, `FINALIZADO`. |
| `domain/model/EstadoEvento.kt` | Nuevo enum `PROGRAMADO`, `EN_CURSO`, `FINALIZADO`, `CANCELADO`. |
| `domain/model/TipoInscripcion.kt` | Nuevo enum `PREVIO`, `WALK_IN`. |
| `domain/model/EstadoInscripcion.kt` | Nuevo enum `PENDIENTE`, `ACREDITADO`, `CANCELADO`. |
| `data/local/entity/ProyectoEntity.kt` | Tabla `proyecto`, FK `coordinadorId → usuario.id` (`RESTRICT`), índices `coordinadorId` y `estado`. |
| `data/local/entity/EventoEntity.kt` | Tabla `evento`, `tituloProyecto` desnormalizado, coordenadas, `radioToleranciaMetros = 200`, índice compuesto `(proyectoId, fechaInicio)` e índice `estado`. |
| `data/local/entity/ParticipanteEntity.kt` | Tabla `participante`, `rut` y `qrToken` únicos, índice de `nombreCompleto`. |
| `data/local/entity/InscripcionEntity.kt` | Tabla `inscripcion`, dos FK `RESTRICT`, `nombreParticipante`/`qrToken` desnormalizados, dos índices únicos y dos índices de apoyo. |
| `data/local/entity/AsistenciaEntity.kt` | Tabla `asistencia`, FK `RESTRICT`, `UNIQUE(inscripcionId)`, campos de encuesta y geolocalización opcionales, `isSynced`/`timestampSync`. |
| `data/local/dao/ProyectoDao.kt` | CRUD, `Flow`, `upsert`, `Insert(ABORT)`, `actualizarTitulo`. |
| `data/local/dao/EventoDao.kt` | CRUD, `Flow`, consultas por proyecto/estado/rango de fechas, `actualizarTituloProyecto` y `observarResumenes()`. |
| `data/local/dao/ParticipanteDao.kt` | Búsqueda por `qrToken`, `rut` y nombre, write-through de nombre y QR. |
| `data/local/dao/InscripcionDao.kt` | Búsqueda por `(eventoId, qrToken)` y `(eventoId, participanteId)`, estados, write-through y `observarConAsistenciaPorEvento` (`@Transaction` + `@Relation`). |
| `data/local/dao/AsistenciaDao.kt` | Cola de pendientes, `actualizarEncuesta` (fuerza `isSynced = 0`), `marcarSincronizada`, `observarDetallePorEvento` y `observarDetallePendientes`. |
| `data/local/dao/MetricasDao.kt` | Agregados globales en una fila (`MetricasGlobales`) como `Flow` y `suspend`. |
| `data/local/projection/EventoResumen.kt` | Proyección de evento con contadores de inscritos, asistencias y pendientes de sync. |
| `data/local/projection/AsistenciaDetalle.kt` | Proyección aplanada de asistencia para auditoría. |
| `data/local/projection/MetricasGlobales.kt` | Proyección de KPIs del administrador. |
| `data/local/relation/InscripcionConAsistencia.kt` | Relación 1:1 inscripción → asistencia. |
| `core/database/Converters.kt` | Conversores para los cinco enums, todos como `name` (`TEXT`). |
| `core/database/AppDatabase.kt` | `@Database` con las 6 entidades y 7 DAOs, versión 1. |
| `di/AppContainer.kt` | Expone los siete DAOs. |
| `app/schemas/.../1.json` | Schema v1 regenerado y consolidado. |
| `androidTest/.../EsquemaCompletoDaoTest.kt` | 12 pruebas instrumentadas de CRUD, unicidad, FK, sync, Flow, proyecciones y métricas. |

---

## 2. Diagrama del esquema físico

```text
usuario ──< proyecto ──< evento ──< inscripcion ──1:1── asistencia
   │                                    │                  │
   │                                    └──< participante  │
   └──────────────────< asistencia ─────────────────────────┘
              (voluntarioId)                    (coordinadorId)
```

Todas las FK usan `onDelete = RESTRICT` y `onUpdate = RESTRICT`: la base de datos impide borrar historial. Para "eliminar" algo se cambia su estado (`CANCELADO`, `FINALIZADO`, `activo = false`).

---

## 3. Tablas generadas (SQL real)

Extraído de `app/schemas/com.rojas.syncpass.core.database.AppDatabase/1.json`.

### `proyecto`

```sql
CREATE TABLE IF NOT EXISTS `proyecto` (
    `id` TEXT NOT NULL,
    `coordinadorId` TEXT NOT NULL,
    `titulo` TEXT NOT NULL,
    `descripcion` TEXT,
    `estado` TEXT NOT NULL,
    `fechaInicio` INTEGER,
    `fechaFin` INTEGER,
    `isSynced` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`coordinadorId`) REFERENCES `usuario`(`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS `index_proyecto_coordinadorId` ON `proyecto` (`coordinadorId`);
CREATE INDEX IF NOT EXISTS `index_proyecto_estado` ON `proyecto` (`estado`);
```

### `evento`

```sql
CREATE TABLE IF NOT EXISTS `evento` (
    `id` TEXT NOT NULL,
    `proyectoId` TEXT NOT NULL,
    `tituloProyecto` TEXT NOT NULL,
    `titulo` TEXT NOT NULL,
    `descripcion` TEXT,
    `lugar` TEXT NOT NULL,
    `latitud` REAL NOT NULL,
    `longitud` REAL NOT NULL,
    `radioToleranciaMetros` INTEGER NOT NULL,
    `fechaInicio` INTEGER NOT NULL,
    `fechaFin` INTEGER NOT NULL,
    `estado` TEXT NOT NULL,
    `isSynced` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`proyectoId`) REFERENCES `proyecto`(`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS `index_evento_proyectoId_fechaInicio` ON `evento` (`proyectoId`, `fechaInicio`);
CREATE INDEX IF NOT EXISTS `index_evento_estado` ON `evento` (`estado`);
```

### `participante`

```sql
CREATE TABLE IF NOT EXISTS `participante` (
    `id` TEXT NOT NULL,
    `rut` TEXT,
    `nombreCompleto` TEXT NOT NULL,
    `email` TEXT,
    `telefono` TEXT,
    `qrToken` TEXT NOT NULL,
    `createdAt` INTEGER NOT NULL,
    `isSynced` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`)
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_participante_rut` ON `participante` (`rut`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_participante_qrToken` ON `participante` (`qrToken`);
CREATE INDEX IF NOT EXISTS `index_participante_nombreCompleto` ON `participante` (`nombreCompleto`);
```

### `inscripcion`

```sql
CREATE TABLE IF NOT EXISTS `inscripcion` (
    `id` TEXT NOT NULL,
    `eventoId` TEXT NOT NULL,
    `participanteId` TEXT NOT NULL,
    `nombreParticipante` TEXT NOT NULL,
    `qrToken` TEXT NOT NULL,
    `tipoRegistro` TEXT NOT NULL,
    `estado` TEXT NOT NULL,
    `timestampInscripcion` INTEGER NOT NULL,
    `isSynced` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`eventoId`) REFERENCES `evento`(`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    FOREIGN KEY(`participanteId`) REFERENCES `participante`(`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_inscripcion_eventoId_participanteId`
    ON `inscripcion` (`eventoId`, `participanteId`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_inscripcion_eventoId_qrToken`
    ON `inscripcion` (`eventoId`, `qrToken`);
CREATE INDEX IF NOT EXISTS `index_inscripcion_participanteId` ON `inscripcion` (`participanteId`);
CREATE INDEX IF NOT EXISTS `index_inscripcion_eventoId_estado` ON `inscripcion` (`eventoId`, `estado`);
```

### `asistencia`

```sql
CREATE TABLE IF NOT EXISTS `asistencia` (
    `id` TEXT NOT NULL,
    `inscripcionId` TEXT NOT NULL,
    `voluntarioId` TEXT NOT NULL,
    `timestampMarcacion` INTEGER NOT NULL,
    `latitudMarcacion` REAL,
    `longitudMarcacion` REAL,
    `precisionGpsMetros` REAL,
    `distanciaEventoMetros` REAL,
    `satisfaccion` INTEGER,
    `utilidad` INTEGER,
    `comentarios` TEXT,
    `isSynced` INTEGER NOT NULL,
    `timestampSync` INTEGER,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`inscripcionId`) REFERENCES `inscripcion`(`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    FOREIGN KEY(`voluntarioId`) REFERENCES `usuario`(`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_asistencia_inscripcionId` ON `asistencia` (`inscripcionId`);
CREATE INDEX IF NOT EXISTS `index_asistencia_voluntarioId` ON `asistencia` (`voluntarioId`);
CREATE INDEX IF NOT EXISTS `index_asistencia_isSynced_timestampMarcacion`
    ON `asistencia` (`isSynced`, `timestampMarcacion`);
```

**Resultado:** 6 tablas y 13 índices. `identityHash = 5bf9e15a23f1b4b81272f91bc8f76f51`.

---

## 4. Por qué estas decisiones

| Decisión | Motivo |
|---|---|
| Índice compuesto `(proyectoId, fechaInicio)` | Cubre la FK y la consulta "eventos de un proyecto ordenados por fecha" con un solo índice; evita el warning de índice faltante de Room. |
| `UNIQUE(eventoId, qrToken)` en lugar de `UNIQUE(qrToken)` | El QR debe acreditar a una sola persona **por evento**, pero un mismo participante puede reutilizar su QR en varios eventos. Probado en `qrUnicoPorEventoPeroReutilizableEnOtroEvento`. |
| `rut` único y `null` permitido | SQLite acepta múltiples `NULL` en un índice `UNIQUE`, así que los walk-ins sin RUT no se bloquean entre sí. |
| `UNIQUE(inscripcionId)` en `asistencia` | Materializa el 1:1 del MER en la base y hace imposible la doble marcación incluso con carreras. |
| `@Insert(onConflict = ABORT)` + `@Upsert` separados | `ABORT` propaga la violación de unicidad para que el dominio decida; `Upsert` resuelve por PK para seeds y sync. |
| `@Transaction` en `observarConAsistenciaPorEvento` | Room lo exige en consultas con `@Relation`; sin él la compilación falla (o el resultado es inconsistente). |
| `MetricasDao` anclado en `FROM inscripcion` | Una consulta sin `FROM` no permite a Room invalidar el `Flow`; con `FROM inscripcion WHERE estado != 'CANCELADO'` la fila agregada existe incluso con tablas vacías y el `Flow` reacciona a los cambios. |
| `@Query` con patrón `LIKE` prearmado | La búsqueda por nombre recibe `"%texto%"` desde el repositorio; evita concatenaciones ambiguas dentro de SQL. |
| `actualizarEncuesta` fuerza `isSynced = 0` | Cumple el requisito "editar una encuesta ya sincronizada la vuelve a marcar como pendiente", sin lógica condicional. |
| `MetricasGlobales.tasaAsistencia` excluye cancelados | Un evento cancelado no debe castigar la tasa de asistencia. |

---

## 5. Invariantes verificadas

| # | Invariante del MER | Cómo se garantiza | Test |
|---|---|---|---|
| 1 | Una asistencia por inscripción | `UNIQUE(inscripcionId)` + FK | `asistenciaUnicaPorInscripcionYConFksObligatorias` |
| 2 | Una inscripción por `(evento, participante)` | Índice único compuesto | `inscripcionUnicaPorEventoYParticipante` |
| 3 | Un QR no acredita a dos en el mismo evento | Índice único `(eventoId, qrToken)` | `qrUnicoPorEventoPeroReutilizableEnOtroEvento` |
| 4 | `Participante.qrToken` único global | Índice único | `participanteQrYRutUnicosConRutOpcional` |
| 5 | `Participante.rut` único cuando no es `null` | Índice único + `NULL` múltiple | `participanteQrYRutUnicosConRutOpcional` |
| 6 | `Asistencia` exige inscripción y voluntario existentes | Dos FK `RESTRICT` | `asistenciaUnicaPorInscripcionYConFksObligatorias` |
| 7 | No se borra historial | `RESTRICT` en las 6 FK | `fkRestrictImpideBorrarHistorial` |
| 8 | Encuesta editada vuelve a pendiente | `actualizarEncuesta` con `isSynced = 0` | `editarEncuestaVuelveAPendienteDeSync` |
| 9 | Campos desnormalizados consistentes | `UPDATE` de write-through en cada DAO | `writeThroughDeCamposDesnormalizados` |

Los invariantes 9 y 10 de `02_mer_decisiones.md` (rango 1..5 de `satisfaccion`/`utilidad`, y toda escritura local deja `isSynced = false` + `updatedAt`) se validan en dominio durante la Fase 4; SQLite no admite `CHECK` vía Room.

---

## 6. Pruebas

Archivo: `app/src/androidTest/java/com/rojas/syncpass/data/local/dao/EsquemaCompletoDaoTest.kt`

| Test | Qué valida |
|---|---|
| `crudYRelacionInscripcionConAsistencia` | CRUD completo, consultas por proyecto/estado/rango, búsquedas por QR/RUT/nombre y la relación 1:1 (primero sin asistencia, luego con ella). |
| `inscripcionUnicaPorEventoYParticipante` | Duplicar la pareja evento+participante lanza `SQLiteConstraintException`. |
| `qrUnicoPorEventoPeroReutilizableEnOtroEvento` | El QR duplicado en el mismo evento falla; el mismo QR en otro evento se acepta. |
| `participanteQrYRutUnicosConRutOpcional` | QR y RUT duplicados fallan; dos participantes con `rut = null` conviven. |
| `asistenciaUnicaPorInscripcionYConFksObligatorias` | Duplicado de asistencia, inscripción inexistente y voluntario inexistente fallan. |
| `fkRestrictImpideBorrarHistorial` | Borrar usuario, proyecto, evento, participante o inscripción con historial falla por `RESTRICT`. |
| `editarEncuestaVuelveAPendienteDeSync` | La encuesta se guarda, `isSynced` pasa a `false` y `marcarSincronizada` lo devuelve a `true`. |
| `flowEmiteAlCambiarEventos` | `Flow` emite la lista inicial y una segunda emisión al insertar un evento. |
| `resumenEventoYDetalleAsistencia` | `EventoResumen` cuenta inscritos/asistencias/pendientes y `AsistenciaDetalle` aplana nombres. |
| `metricasGlobalesReflejanDatosYEncuestas` | KPIs, promedios y tasa de asistencia correctos en `Flow` y `suspend`. |
| `writeThroughDeCamposDesnormalizados` | Renombrar proyecto/participante y rotar QR actualiza las copias. |
| `applicationExponeTodosLosDaos` | `AppContainer` expone los siete DAOs. |

Se mantienen los 5 tests de `AppDatabaseTest` de la Fase 1, más `ExampleInstrumentedTest`: **18 tests**.

### Comandos

```powershell
# Build limpio + APK de tests + tests JVM
.\gradlew.bat clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest

# Tests instrumentados (requiere dispositivo/emulador)
.\gradlew.bat :app:connectedDebugAndroidTest

# Análisis estático
.\gradlew.bat :app:lintDebug
```

### Evidencia

```text
clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
BUILD SUCCESSFUL in 21s
78 actionable tasks: 78 executed

Starting 18 tests on moto g82 5G - 13
Finished 18 tests on moto g82 5G - 13
BUILD SUCCESSFUL in 12s

:app:lintDebug
BUILD SUCCESSFUL in 16s
```

```text
tests="18" failures="0" errors="0" skipped="0"
```

`lintDebug` no reportó errores. Los warnings siguen siendo los preexistentes (versiones más nuevas disponibles, incluida Room 2.8.5, y `RedundantLabel` en la Activity). Room no mostró warnings de índices faltantes.

---

## 7. Verificación manual

1. Regenerar el schema desde cero:

   ```powershell
   .\gradlew.bat :app:kspDebugKotlin
   Get-Content app\schemas\com.rojas.syncpass.core.database.AppDatabase\1.json
   ```

2. Desinstalar/limpiar datos antes de probar, porque el hash del esquema v1 cambió:

   ```powershell
   adb uninstall com.rojas.syncpass
   # o, con la app instalada:
   adb shell pm clear com.rojas.syncpass
   ```

3. Instalar y abrir la app; el login debe seguir funcionando.

4. **Hallazgo:** `databases/` todavía no existe al abrir la app (`run-as com.rojas.syncpass ls -la /data/user/0/com.rojas.syncpass` solo muestra `cache`, `code_cache` y `files`). Room abre el archivo de forma perezosa, en la primera consulta real, que llega en la Fase 3 con los repositorios. La validación del esquema en esta fase es la instrumentada.

**Smoke test (2026-10-22):** instalación `Success`, proceso activo (`pidof` → PID), `ResumedActivity: com.rojas.syncpass/.MainActivity`, sin `FATAL EXCEPTION` en el buffer de crashes.

---

## 8. Deuda técnica y pendientes

1. **Fase 3:** repositorios que envuelvan el write-through en `withTransaction`, `TimeProvider`/`IdProvider` inyectables, seeds ficticios idempotentes y validación de `username` previa al `upsert`.
2. **Fase 4:** validación en dominio de `satisfaccion`/`utilidad` en `1..5` y del flujo transaccional de acreditación (walk-in + asistencia + cambio de estado).
3. **Fase 9:** la sincronización debe respetar el orden proyectos → eventos → participantes → inscripciones → asistencias por las FK.
4. **Antes de release:** el esquema v1 queda congelado en este punto; cualquier cambio posterior requiere `Migration` explícita y eliminar el fallback destructivo de debug.

---

## 9. Cómo revisar esta fase

```powershell
# 1. Entidades y DAOs
Get-ChildItem app\src\main\java\com\rojas\syncpass\data\local\entity
Get-ChildItem app\src\main\java\com\rojas\syncpass\data\local\dao

# 2. Proyecciones y relaciones
Get-ChildItem app\src\main\java\com\rojas\syncpass\data\local\projection
Get-ChildItem app\src\main\java\com\rojas\syncpass\data\local\relation

# 3. Schema consolidado
Get-Content app\schemas\com.rojas.syncpass.core.database.AppDatabase\1.json

# 4. Tests
.\gradlew.bat :app:connectedDebugAndroidTest

# 5. Lint
.\gradlew.bat :app:lintDebug
```
