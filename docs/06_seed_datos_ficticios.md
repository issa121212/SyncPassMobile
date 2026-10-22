# 06 — Seed de datos ficticios

> **Fase:** 3
> **Estado:** completada
> **Objetivo:** poblar la base local con un catálogo **100 % ficticio** que permita
> probar la app sin conexión desde el primer arranque, sin duplicar filas y sin
> pisar el trabajo del usuario.

---

## 1. Archivos

| Archivo | Responsabilidad |
|---|---|
| `data/local/seed/SeedData.kt` | Catálogo estático de filas ficticias. |
| `data/local/seed/SeedDataInitializer.kt` | Siembra idempotente y transaccional. |
| `SyncPassApplication.kt` | Dispara `sembrar()` al arrancar, fuera del hilo principal. |
| `app/src/androidTest/.../RepositoriosTest.kt` | Pruebas de idempotencia e integridad. |

---

## 2. Volumen sembrado

| Tabla | Filas | Notas |
|---|---:|---|
| `usuario` | 5 | 1 administrador, 2 coordinadores, 2 voluntarios. |
| `proyecto` | 3 | 2 activos y 1 en borrador. |
| `evento` | 6 | 1 finalizado, 1 cancelado, el resto programados. |
| `participante` | 10 | 1 sin RUT (prueba el índice único con `NULL`). |
| `inscripcion` | 14 | Incluye canceladas para probar el historial. |
| `asistencia` | 5 | 4 sincronizadas y **1 pendiente** de subir. |

El seed es deliberadamente heterogéneo: hay filas con y sin geolocalización, con
y sin encuesta, y con estados distintos, para que cualquier pantalla tenga casos
reales que mostrar sin escribir datos a mano.

---

## 3. Cómo se logra la idempotencia

No hay bandera de "ya sembrado" ni se borra nada. Se apoya en dos propiedades:

1. **IDs deterministas.** Los registros usan claves legibles y fijas
   (`usuario-1`, `proyecto-1`, `evento-2`, `inscripcion-7`, `asistencia-5`).
   Sembrar dos veces produce exactamente las mismas claves primarias.
2. **Escrituras `upsert`.** Cada inserción usa `@Upsert`, que resuelve por clave
   primaria: si la fila existe, la reemplaza; si no, la inserta.

`SeedDataInitializer.sembrar()` devuelve `false` si el seed ya está aplicado:

```kotlin
val usuarioExiste  = usuarioDao.obtenerPorId("usuario-2") != null
val proyectoExiste = proyectoDao.obtenerPorId("proyecto-1") != null
return usuarioExiste && proyectoExiste
```

**Por qué dos anclas.** Un conteo de filas daría falso positivo si el
coordinador ya creó datos propios. Un solo ancla no distingue un seed parcial de
uno completo. Se consultan dos entidades distintas (usuario y proyecto), lo que
en la práctica equivale a "el catálogo está presente".

**Por qué no se pisa el trabajo del usuario.** Al estar la marca activa, el seed
no vuelve a ejecutarse. Aun si se forzara, el `upsert` de IDs deterministas no
revierte ediciones hechas *in situ* salvo que el usuario hubiese cambiado el
mismo campo del mismo registro. El test `seedNoPisaEdicionesDelUsuario` lo
verifica renombrando `proyecto-1` y volviendo a sembrar.

---

## 4. Orden y atomicidad

El seed corre en **una sola transacción** (`Transaccionador.enTransaccion`), en
este orden exigido por las claves foráneas:

```
usuarios → proyectos → eventos → participantes → inscripciones → asistencias
```

Si algo falla en el medio, la transacción se revierte por completo: nunca queda
un catálogo a medias (por ejemplo, eventos sin su proyecto).

---

## 5. Datos 100 % ficticios

- **RUT:** `11111111-1`, `22222222-2`, … inventados; solo cumplen formato.
- **Teléfonos:** rango ficticio `+5690000000N`.
- **Correos:** terminan en `@demo.cl`.
- **Direcciones/lugares:** "Sede Vecinal Demo Norte", "Biblioteca Demo Central",
  "Plaza Demo Sur", "Parque Demo Oriente".
- **Coordenadas:** puntos ficticios alrededor de Santiago, sin corresponder a un
  lugar real de la organización.
- **Tokens QR:** `SP-DEMO-*` o `QR-EVENTO*`, generados por `QrTokenGenerator`
  o fijados en el catálogo.

No debe incorporarse información real a este archivo. El seed existe únicamente
para demostración y pruebas.

---

## 6. Casos que el seed habilita

| Caso de prueba | Cómo lo habilita el seed |
|---|---|
| Contador offline | `asistencia-5` nace con `isSynced = false`. |
| Promedios con datos faltantes | `asistencia-4` no tiene encuesta (`NULL`). |
| Marcación sin GPS | `asistencia-3` guarda lat/lon `NULL` y sigue siendo válida. |
| Índice único con `NULL` | `participante-6` no tiene RUT. |
| Historial que no se borra | Evento e inscripciones `CANCELADO`. |
| Write-through de títulos | Cada evento copia `tituloProyecto` del proyecto padre. |
| Write-through de nombres | Cada inscripción copia `nombreParticipante`. |

---

## 7. Cómo probarlo

```powershell
# Tests instrumentados (incluye las pruebas de seed)
.\gradlew.bat :app:connectedDebugAndroidTest
```

En el dispositivo, tras instalar y abrir la app:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
# El seed corre al arrancar; verificar que no crece al reiniciar
& $adb shell run-as com.rojas.syncpass ls databases/
```

Prueba manual:

1. Instalar la app y abrirla (primer arranque).
2. Cerrar y volver a abrir varias veces.
3. Confirmar en los tests instrumentados que los conteos no cambian
   (`seedEsIdempotenteYNoDuplica`).

---

## 8. Evidencia

- `RepositoriosTest.seedEsIdempotenteYNoDuplica` — conteos exactos y estables en
  3 ejecuciones consecutivas.
- `RepositoriosTest.seedNoPisaEdicionesDelUsuario` — edición local sobrevive a
  un resiembra.
- `RepositoriosTest.seedDejaUnaAsistenciaPendienteDeSync` — `observarCantidadPendientes() == 1`.
- `RepositoriosTest.seedRespetaClavesForaneasYSusCopiasDesnormalizadas` — copias
  desnormalizadas coherentes con su origen.

Las 43 pruebas instrumentadas de la Fase 3 pasan en `moto g82 5G - 13`.

---

## 9. Deuda técnica

- El `qrToken` del seed **no es criptográfico**. En producción debe ser firmado y
  rotativo con TTL (documentado también en `07_repositorios_y_flows.md`).
- Cuando existan endpoints reales, el seed deberá degradarse a un dataset de
  *desarrollo* activable por `BuildConfig.DEBUG`, no ejecutarse en release.
- `TIMESTAMP_BASE` está fijado a `2026-10-22T12:00:00Z` para que el catálogo sea
  reproducible; en producción las fechas vendrán del servidor.
