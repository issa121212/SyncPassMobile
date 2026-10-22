# 07 — Repositorios y Flows

> **Fase:** 3
> **Estado:** completada
> **Objetivo:** exponer la persistencia a la UI mediante repositorios que
> concentran el **write-through**, la validación de dominio y el marcado
> `isSynced = false`, devolviendo `Flow` para las lecturas reactivas.

---

## 1. Archivos

| Archivo | Responsabilidad |
|---|---|
| `data/repository/ResultadoOperacion.kt` | Resultado sellado y catálogo de `ErrorOperacion`. |
| `data/repository/Transaccionador.kt` | Abstracción de transacción, traducción de errores de SQLite y helpers. |
| `data/repository/UsuarioRepository.kt` | CRUD de usuarios y validación de `username`. |
| `data/repository/ProyectoRepository.kt` | Agregado proyecto → evento → inscripción y write-through. |
| `data/repository/ParticipanteRepository.kt` | Participantes, RUT/QR únicos y propagación a inscripciones. |
| `data/repository/AsistenciaRepository.kt` | Cola de sync y encuesta. |
| `core/time/TimeProvider.kt` | Reloj inyectable (`SystemTimeProvider` en producción). |
| `core/id/IdProvider.kt` | Generador de IDs inyectable (`UuidProvider`). |
| `core/id/QrTokenGenerator.kt` | Tokens QR legibles para walk-ins. |
| `di/AppContainer.kt` | Grafo manual de dependencias. |

---

## 2. Contrato común

Todas las operaciones de escritura devuelven `ResultadoOperacion<T>`:

```kotlin
sealed interface ResultadoOperacion<out T> {
    data class Exito<T>(val valor: T) : ResultadoOperacion<T>
    data class Fallo(val error: ErrorOperacion) : ResultadoOperacion<Nothing>
}
```

**Por qué un resultado y no excepciones.** Las validaciones de dominio (username
duplicado, ya inscrito, encuesta fuera de escala) son casos esperados que la UI
debe traducir a un mensaje concreto. Se reservan las excepciones para bugs.

`ErrorOperacion` enumera los fallos: `USERNAME_DUPLICADO`, `RUT_DUPLICADO`,
`QR_DUPLICADO`, `YA_INSCRITO_EN_EVENTO`, `QR_YA_USADO_EN_EVENTO`,
`NO_ENCONTRADO`, `CONFLICTO_DE_UNICIDAD`, `OPERACION_INVALIDA`.

La traducción de `SQLiteConstraintException` a un `ErrorOperacion` se hace en
`traducir(...)`, inspeccionando el mensaje para distinguir el índice violado. Es
una red de seguridad: la UI recibe primero el error de dominio, y solo si hay
una carrera entre la validación y el `INSERT` aparece la traducción de SQLite.

---

## 3. Transacciones

`Transaccionador` abstrae `RoomDatabase.withTransaction`:

```kotlin
interface Transaccionador {
    suspend fun <T> enTransaccion(bloque: suspend () -> T): T
}
```

**Por qué una interfaz.** Los repositorios no dependen de `AppDatabase`
directamente, así que se pueden probar con una implementación trivial y cambiar
de motor de persistencia sin reescribir cada repositorio.

**Por qué no es `fun interface`.** Kotlin no permite métodos abstractos con
parámetros de tipo en una interfaz funcional; por eso es una `interface` normal
con una única implementación (`RoomTransaccionador`).

Dentro de una transacción, un error de dominio se corta con `OperacionRechazada`,
que `intentarSuspend` convierte en `ResultadoOperacion.Fallo`. Así se garantiza
el *rollback* (por ejemplo, si falla la actualización de las inscripciones, no
queda un participante renombrado a medias).

---

## 4. Write-through

Los campos desnormalizados (`Evento.tituloProyecto`,
`Inscripcion.nombreParticipante`, `Inscripcion.qrToken`) son la razón de que las
lecturas no necesiten `JOIN`. Para que no queden inconsistentes, cada vez que
cambia el origen se actualizan las copias **en la misma transacción**:

| Cambio | Copias actualizadas |
|---|---|
| `renombrarProyecto` | `Evento.tituloProyecto` de todos sus eventos |
| `renombrarParticipante` | `Inscripcion.nombreParticipante` de todas sus inscripciones |
| `rotarQr` | `Inscripcion.qrToken` de todas sus inscripciones |

Todas las copias actualizadas se marcan `isSynced = false` con el mismo
`updatedAt`, para que el próximo ciclo de sincronización las suba.

---

## 5. TimeProvider e IdProvider

```kotlin
fun interface TimeProvider { fun ahoraMillis(): Long }
fun interface IdProvider   { fun nuevoId(): String }
```

- **TimeProvider:** todo el modelo guarda epoch millis **UTC**. Centralizar el
  "ahora" permite tests deterministas y evita llamadas dispersas a
  `System.currentTimeMillis()`.
- **IdProvider:** las filas creadas en cliente (walk-ins, CRUD offline, seeds)
  usan UUID de texto. El cliente puede crear filas sin red y el servidor hace
  `upsert` por ID sin colisiones.

Ambos se inyectan desde `AppContainer`; en pruebas se sustituyen por dobles
controlados.

---

## 6. Validación de `username` (lección de la Fase 1)

`@Upsert` resuelve conflictos por **clave primaria**, no por índice único. Un
`username` repetido con otro `id` no lanza excepción: el upsert es un no-op
silencioso. Por eso `UsuarioRepository` valida antes:

```kotlin
suspend fun guardarDesdeServidor(usuario: UsuarioEntity): ResultadoOperacion<UsuarioEntity> {
    val existente = usuarioDao.obtenerPorUsername(usuario.username)
    if (existente != null && existente.id != usuario.id) {
        return fallo(ErrorOperacion.USERNAME_DUPLICADO)
    }
    ...
}
```

El mismo patrón se aplica en `ParticipanteRepository` para `rut` y `qrToken`
(validación previa + restricción de SQLite como respaldo).

**RUT nulo.** Un RUT vacío se normaliza a `null`. SQLite permite múltiples `NULL`
en un índice único, así que dos personas sin RUT no se bloquean entre sí.

---

## 7. Repositorios

### 7.1 `ProyectoRepository`

Agregado proyecto → evento → inscripción. Expone lecturas reactivas
(`observarProyectos`, `observarEventosDeProyecto`, `observarResumenesDeEventos`,
`observarInscripcionesDeEvento`, `observarPendientesDeSync`) y escrituras de CRUD
con validación: `crearProyecto`, `crearEvento` (hereda `tituloProyecto`),
`inscribirParticipante` (valida duplicados y QR ya usado), `cambiarEstadoProyecto`,
`cambiarEstadoEvento`, `cancelarInscripcion`, `renombrarProyecto`.

### 7.2 `ParticipanteRepository`

`crearParticipante` (QR generado si no se provee), `renombrarParticipante`,
`rotarQr`, `actualizarContacto`, más búsquedas (`buscarPorNombre` con `LIKE`
armado en el repositorio, `buscarPorQrToken`, `buscarPorRut`).

### 7.3 `AsistenciaRepository`

Cubre la **encuesta** y la **cola de sincronización**:
`observarPendientesDeSync`, `observarCantidadPendientes` (contador offline),
`observarAsistenciasDeEvento`, `responderEncuesta`, `marcarSincronizada`,
`estaPendienteDeSync`.

`responderEncuesta` valida que `satisfaccion`/`utilidad` estén en `1..5` (lo que
SQLite no puede expresar), exige que exista la asistencia y rechaza inscripciones
canceladas. La acreditación transaccional completa (walk-in + geofence) llega en
la Fase 4 y reutilizará estos métodos.

**Regla clave:** editar la encuesta **siempre** devuelve la fila a
`isSynced = false`, incluso si ya se había subido (`actualizarEncuesta` fuerza el
flag). Esto permite re-sincronizar una encuesta corregida.

### 7.4 `UsuarioRepository`

`observarUsuarios`, `obtenerPorId`, `obtenerPorUsername`, `contar`,
`guardarDesdeServidor` (para el login), `crearLocal` y `marcarSincronizado` (lo
usará el worker de la Fase 9).

---

## 8. Hilos

- Las lecturas exponen `Flow`; no hay consultas en el hilo principal.
- Las escrituras son `suspend`; Room las ejecuta en su propio dispatcher.
- El seed se dispara desde `applicationScope` (`SupervisorJob + Dispatchers.IO`),
  así que el arranque de la UI no espera a que se siembren los datos.

---

## 9. Cómo probarlo

```powershell
# Compilar
.\gradlew.bat :app:assembleDebug

# Tests instrumentados de repositorios
.\gradlew.bat :app:connectedDebugAndroidTest
```

Casos cubiertos en `RepositoriosTest` (43 pruebas instrumentadas en total,
incluyendo las fases previas):

- Seed idempotente, no pisa ediciones, respeta FK y copias desnormalizadas.
- `username` duplicado devuelve `USERNAME_DUPLICADO` y no crea fila fantasma.
- `guardarDesdeServidor` con username de otro `id` falla.
- Write-through de título de proyecto, nombre y QR de participante.
- Validaciones de creación (título vacío, fechas, FK, RUT duplicado, QR duplicado).
- Dos participantes sin RUT conviven.
- Walk-in copia nombre y QR.
- Cambios de estado no borran historial.
- Encuesta: escala válida, sin asistencia, inscripción cancelada, re-pendiente.
- `marcarSincronizada` registra auditoría y baja el contador de pendientes.

---

## 10. Evidencia

- `.\gradlew.bat clean :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest` → **BUILD SUCCESSFUL** (78 tareas).
- `.\gradlew.bat :app:connectedDebugAndroidTest` → **43/43 tests verdes** en `moto g82 5G - 13`.

---

## 11. Deuda técnica

- `QrTokenGenerator` no es criptográfico (usa `SecureRandom`, pero sin firma ni
  TTL). En producción el QR debe ser firmado y rotativo.
- La traducción de errores de SQLite depende del texto del mensaje; es frágil
  ante cambios de versión de SQLite y debe acompañarse siempre de la validación
  previa en el repositorio.
- Los repositorios aún no exponen casos de uso; la capa de `UseCase` llega en la
  Fase 4 para la acreditación y en la Fase 5 para la sesión.
