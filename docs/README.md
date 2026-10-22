# Documentación — SyncPass

> **Proyecto:** SyncPass — acreditación comunitaria y encuestas en terreno  
> **Stack:** Kotlin · Jetpack Compose · Material 3 · Room 2.7.1 · KSP · Flow · WorkManager  
> **Estado:** Fases 0, 1, 2 y 3 completadas. Fase 4 pendiente.

---

## Índice

| Documento | Contenido | Estado |
|---|---|---|
| [`plan_implementacion.md`](plan_implementacion.md) | Plan completo, decisiones confirmadas y fases | ✅ |
| [`00_diagnostico_y_baseline.md`](00_diagnostico_y_baseline.md) | Estado inicial, entorno y baseline del build | ✅ |
| [`01_arquitectura_offline_first.md`](01_arquitectura_offline_first.md) | Capas, flujos, DI, sesión, sync y UX | ✅ |
| [`02_mer_decisiones.md`](02_mer_decisiones.md) | MER, esquema físico, invariantes y migraciones | ✅ |
| [`03_room_ksp_setup.md`](03_room_ksp_setup.md) | Spike KSP/AGP, configuración y troubleshooting | ✅ |
| [`prompt_room.md`](prompt_room.md) | Enunciado original del requerimiento | Referencia |
| [`04_room_setup.md`](04_room_setup.md) | `AppDatabase`, provider, schema export y tests | ✅ Fase 1 |
| `05_esquema_entidades_daos.md` | DAOs, índices, relaciones y proyecciones | ✅ Fase 2 |
| `06_seed_datos_ficticios.md` | Dataset demo idempotente | ✅ Fase 3 |
| `07_repositorios_y_flows.md` | Contratos de repositorios | ✅ Fase 3 |
| `08_logica_asistencia_offline.md` | Transacciones, geofence y encuesta | ⏳ Fase 4 |
| `09_sesion_roles.md` | DataStore, JWT y role mapper | ⏳ Fase 5 |
| `10_dashboard_voluntario.md` | QR, marcación y encuesta | ⏳ Fase 6 |
| `11_dashboard_coordinador.md` | CRUD y supervisión | ⏳ Fase 7 |
| `12_dashboard_administrador.md` | Métricas y auditoría | ⏳ Fase 8 |
| `13_sincronizacion_workmanager.md` | Worker, reintentos e idempotencia | ⏳ Fase 9 |
| `14_pruebas_y_qa.md` | Matriz de pruebas y evidencia | ⏳ Fase 10 |
| `15_migraciones.md` | Política y tests de migración | ⏳ Fase 10 |
| `16_manual_terreno.md` | Guion de prueba en terreno | ⏳ Fase 10 |

---

## Convención de cada documento

Cada avance debe registrar:

1. **Objetivo** de la fase.
2. **Qué se hizo** (archivos y cambios).
3. **Por qué** se tomó cada decisión.
4. **Cómo probarlo** (comandos y pasos manuales).
5. **Evidencia** (logs, capturas, resultados).
6. **Pendientes/deuda técnica**.

---

## Comandos rápidos

```powershell
# Compilar
.\gradlew.bat :app:assembleDebug

# Tests JVM
.\gradlew.bat :app:testDebugUnitTest

# Tests instrumentados (requiere emulador/dispositivo)
.\gradlew.bat :app:connectedDebugAndroidTest

# Lint
.\gradlew.bat :app:lintDebug

# Build limpio
.\gradlew.bat clean :app:assembleDebug
```

---

## Backend / entorno

- **API actual:** `https://app.claudioviajando.com/` (reemplaza al host anterior de Linode).
- La URL se resuelve en este orden: `.env` → variable de entorno `API_BASE_URL` → fallback de `app/build.gradle.kts`.
- `RetrofitClient` consume `AppConfig.apiBaseUrl`, así que no hay URLs hardcodeadas en la capa de red.
- Para cambiar de entorno sin tocar código, editar `.env` y reconstruir (`.\gradlew.bat :app:assembleDebug`).

---

## Estado actual

- **Fase 0:** completada.
  - Baseline `assembleDebug`: verde.
  - Spike KSP/AGP: KSP `2.3.10` + Room `2.7.1` validado.
  - Documentación inicial creada.
- **Fase 1:** completada.
  - KSP `2.3.10` + Room `2.7.1` aplicados al proyecto real.
  - `AppDatabase` v1 con `UsuarioEntity`, DAO, converters, `Application` y `AppContainer`.
  - Schema exportado en `app/schemas/.../1.json`.
  - 6 tests instrumentados verdes en dispositivo.
- **Fase 2:** completada.
  - Esquema completo: `usuario`, `proyecto`, `evento`, `participante`, `inscripcion`, `asistencia`.
  - 7 DAOs con `Flow`/`suspend`, 4 proyecciones/relaciones y converters para 5 enums.
  - FK `RESTRICT` e índices únicos que protegen historial y evitan duplicados.
  - Schema v1 consolidado (`identityHash = 5bf9e15a23f1b4b81272f91bc8f76f51`).
  - 18 tests instrumentados verdes en dispositivo.
- **Fase 3:** completada.
  - 5 repositorios (`Usuario`, `Proyecto`, `Participante`, `Asistencia` + helpers) con `Flow` y `ResultadoOperacion`.
  - `TimeProvider`/`IdProvider` inyectables, `Transaccionador` sobre `withTransaction` y `QrTokenGenerator`.
  - Write-through de `Evento.tituloProyecto`, `Inscripcion.nombreParticipante` y `Inscripcion.qrToken` en una sola transacción.
  - Seed idempotente con 5 usuarios, 3 proyectos, 6 eventos, 10 participantes, 14 inscripciones y 5 asistencias (1 pendiente de sync).
  - Validación de `username`/`rut`/`qrToken` antes del `upsert`.
  - 43 tests instrumentados verdes en dispositivo.
- **Siguiente:** Fase 4 — lógica offline de asistencia (walk-in atómico, geofence, encuesta).

> El spike de Fase 0 se ejecutó en una copia temporal. Las fases 1 y 2 ya están aplicadas en el proyecto real.
