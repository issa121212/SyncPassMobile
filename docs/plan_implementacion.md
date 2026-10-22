# Plan de Implementación — SyncPass

> **Proyecto:** SyncPass — acreditación comunitaria y encuestas en terreno  
> **Contexto:** DSY1105 Duoc UC / Ticdgm  
> **Última actualización:** 2026-10-22  
> **Estado general:** Fases 0, 1, 2 y 3 completadas. Fase 4 pendiente.  
> **Stack:** Kotlin, Jetpack Compose, Material Design 3, Room 2.7.1, KSP, corrutinas + Flow, AGP 9.3.3, Gradle 9.5.0, `compileSdk 37`, `minSdk 24`.

---

## 1. Decisiones confirmadas

| Tema | Decisión |
|---|---|
| Flags de sincronización | `isSynced` + `updatedAt` en **todas** las entidades mutables: `Proyecto`, `Evento`, `Participante`, `Inscripcion`, `Asistencia`. |
| Sesión | DataStore Preferences, con gracia offline de **8 horas** después del `exp` del JWT. |
| Navegación | Auth gate por estado en `MainActivity`; `Navigation Compose` para los dashboards desde la Fase 6. |
| QR | CameraX + ML Kit **bundled** (offline), con entrada manual de token como fallback. |

Estas decisiones se consideran cerradas para la v1. Cualquier cambio debe registrarse en este documento y en el doc de la fase correspondiente.

---

## 2. Alcance

### Incluido

- Room `syncpass.db`, versión 1, con schema exportado y versionado.
- Entidades, DAOs, índices, claves foráneas, conversores y proyecciones.
- Repositorios con `Flow`, seeds ficticios idempotentes y `AppContainer` manual.
- Lógica offline-first de acreditación, walk-ins, geolocalización y encuesta.
- Sesión persistente y enrutamiento por rol.
- Dashboards Voluntario, Coordinador y Administrador.
- Sincronización con WorkManager y API simulada mientras no existan endpoints reales.
- Documentación por fase y pruebas automatizadas/manuales.

### Fuera de alcance v1

- Endpoints reales de sincronización y resolución avanzada de conflictos multi-dispositivo.
- Firma criptográfica de QR, refresh tokens, AWS Cognito real y cifrado de DB con SQLCipher.
- Datos personales reales. Todo el seed es ficticio.
- Notificaciones push, analítica y exportación a PDF/Excel.
- Borrado físico de historial con asistencias; se usan estados (`CANCELADO`, `FINALIZADO`).

---

## 3. Fases

| Fase | Nombre | Estado | Documento |
|---|---|---|---|
| 0 | Baseline y spike KSP/AGP | ✅ Completada | `00_diagnostico_y_baseline.md`, `03_room_ksp_setup.md` |
| 1 | Infraestructura Room + slice `Usuario` | ✅ Completada | `03_room_ksp_setup.md`, `04_room_setup.md` |
| 2 | Esquema completo + DAOs | ✅ Completada | `05_esquema_entidades_daos.md` |
| 3 | Repositorios + seeds | ✅ Completada | `06_seed_datos_ficticios.md`, `07_repositorios_y_flows.md` |
| 4 | Lógica offline asistencia/encuesta | ⏳ Siguiente | `08_logica_asistencia_offline.md` |
| 5 | Sesión y roles persistentes | Pendiente | `09_sesion_roles.md` |
| 6 | Dashboard Voluntario + QR | Pendiente | `10_dashboard_voluntario.md` |
| 7 | Dashboard Coordinador | Pendiente | `11_dashboard_coordinador.md` |
| 8 | Dashboard Administrador | Pendiente | `12_dashboard_administrador.md` |
| 9 | Sincronización WorkManager | Pendiente | `13_sincronizacion_workmanager.md` |
| 10 | QA, accesibilidad y cierre | Pendiente | `14_pruebas_y_qa.md`, `15_migraciones.md`, `16_manual_terreno.md` |

> Los nombres de documentos de fases futuras pueden ajustarse al crearse, manteniendo la numeración y el índice de `docs/README.md`.

---

## 4. Detalle por fase

### Fase 0 — Baseline y spike KSP/AGP

**Objetivo:** validar que Room + KSP funcionan con AGP 9 y Kotlin integrado antes de diseñar tablas.

**Resultado:** build base verde; KSP 2.3.10 + Room 2.7.1 validado en copia temporal; documentación inicial creada.

**Criterio de aceptación:** cumplido. Ver `docs/03_room_ksp_setup.md`.

---

### Fase 1 — Infraestructura Room + vertical slice `Usuario`

**Estado:** ✅ Completada. Build, schema y 6 tests instrumentados verdes. Ver `docs/04_room_setup.md`.

**Entregables**
- `libs.versions.toml`: Room 2.7.1 y KSP 2.3.10.
- Plugin KSP en raíz y en `:app`; argumento `room.schemaLocation`.
- `UsuarioEntity`, `RolUsuario`, `Converters`, `UsuarioDao`, `AppDatabase`.
- `SyncPassApplication` y `AppContainer`.
- Manifest: `android:name`, `INTERNET` y `ACCESS_NETWORK_STATE` explícitos.
- Test instrumentado de apertura de DB + insert/read.
- `docs/04_room_setup.md`.

**Criterio de aceptación**
- `assembleDebug` verde.
- Schema en `app/schemas/.../1.json`.
- Test de DB pasa.
- Login actual sigue funcionando.

---

### Fase 2 — Esquema completo, DAOs e índices

**Estado:** ✅ Completada. Las 6 tablas y 7 DAOs compilan sin warnings de Room, el schema v1 quedó consolidado y 18 tests instrumentados pasan. Ver `docs/05_esquema_entidades_daos.md`.

**Entregables**
- `ProyectoEntity`, `EventoEntity`, `ParticipanteEntity`, `InscripcionEntity`, `AsistenciaEntity`.
- DAOs con consultas `suspend` y `Flow`.
- Relaciones/proyecciones: `InscripcionConAsistencia`, `AsistenciaDetalle`, `EventoResumen`, `MetricasGlobales`.
- Índices y FK con `RESTRICT` para proteger historial.
- Tests de CRUD, FK, unicidad y emisión de `Flow`.

**Criterio de aceptación**
- Room compila sin warnings de índices faltantes.
- No se puede duplicar asistencia, inscripción por evento ni QR por evento.
- Las FK impiden borrar historial.
- Los `Flow` emiten al cambiar datos.

---

### Fase 3 — Repositorios, seeds y datos ficticios

**Estado:** ✅ Completada. 5 repositorios, providers inyectables y seed idempotente; 43 tests instrumentados verdes. Ver `docs/06_seed_datos_ficticios.md` y `docs/07_repositorios_y_flows.md`.

**Entregables**
- Repositorios por agregado.
- `TimeProvider` e `IdProvider` inyectables.
- `SeedDataInitializer` idempotente con datos 100 % ficticios.
- `docs/06_seed_datos_ficticios.md` y `docs/07_repositorios_y_flows.md`.

**Criterio de aceptación**
- El seed no duplica datos al reiniciar. ✅
- Los repositorios exponen `Flow` y no bloquean el hilo principal. ✅
- La app muestra datos sin conexión. ✅

---

### Fase 4 — Lógica offline-first de asistencia y encuesta

**Entregables**
- `RegistrarAsistenciaUseCase`, `ResponderEncuestaUseCase`, `BuscarInscripcionUseCase`, `ObservarPendientesSyncUseCase`.
- `GeoUtils` (Haversine).
- Resultado sellado `ResultadoAcreditacion`.
- Transacción atómica para walk-in + asistencia.
- Tests unitarios e instrumentados.

**Criterio de aceptación**
- Doble marcación bloqueada.
- Walk-in atómico.
- Encuesta editada vuelve a `isSynced = false`.
- Geofence genera advertencia, no bloqueo silencioso.

---

### Fase 5 — Sesión persistente y contexto por rol

**Entregables**
- `SessionRepository` con DataStore.
- `RoleMapper` desde claims del JWT.
- Upsert de `UsuarioEntity` al iniciar sesión.
- `MainActivity` observa la sesión; `RoleRoot` por rol.
- Política de gracia offline de 8 h.
- `docs/09_sesion_roles.md`.

**Criterio de aceptación**
- La sesión sobrevive a reinicio de app.
- `superadmin` → `ADMINISTRADOR`; rol desconocido → `VOLUNTARIO`.
- Sin red, dentro de la gracia, se puede marcar.
- Logout limpia la sesión y conserva pendientes de sync.

---

### Fase 6 — Dashboard Voluntario y escaneo QR

**Entregables**
- Eventos del día/asignados.
- CameraX + ML Kit bundled; entrada manual fallback.
- Búsqueda por nombre/RUT para walk-ins.
- Marcación con ubicación opcional, geofence y encuesta.
- Indicador offline y contador de pendientes.
- Mínimo 48 dp en todos los controles.

**Criterio de aceptación**
- Escaneo + marcación percibidos en < 5 s.
- Funciona en modo avión.
- Doble escaneo no duplica.
- Permisos denegados no bloquean el flujo.

---

### Fase 7 — Dashboard Coordinador

**Entregables**
- CRUD local de proyectos/eventos/inscripciones.
- Detalle de evento con inscritos y asistencias.
- Walk-ins y cancelaciones con confirmación.
- Resultados de encuesta por evento.

**Criterio de aceptación**
- La UI reacciona por `Flow`.
- `Evento.tituloProyecto` se mantiene sincronizado.
- No se cancela historial sin confirmación.
- Formularios con validación y 48 dp.

---

### Fase 8 — Dashboard Administrador

**Entregables**
- Métricas globales y tasa de asistencia.
- Promedios de satisfacción/utilidad.
- Auditoría de últimas marcaciones.
- Filtros por proyecto, evento, fecha y estado de sync.
- Gráficos simples con Compose.

**Criterio de aceptación**
- Métricas verificadas contra SQL.
- Auditoría de solo lectura.
- Estados vacío/carga/error correctos.

---

### Fase 9 — Sincronización WorkManager

**Entregables**
- Contrato de API documentado.
- `SyncRemoteDataSource` fake en debug.
- `SyncRepository` + `SyncWorker`.
- Orden de push: proyectos → eventos → participantes → inscripciones → asistencias.
- Idempotencia por UUID de cliente; backoff exponencial; trabajo único.
- Sync manual y automático al recuperar red.

**Criterio de aceptación**
- Modo avión deja pendientes.
- Al recuperar red se sincroniza y marca `isSynced = true`.
- Encuesta posterior vuelve a pendiente.
- Fallo parcial no marca lo no enviado.
- Reintento sin duplicados en servidor simulado.

---

### Fase 10 — QA, accesibilidad y cierre

**Entregables**
- `lintDebug`, `assembleRelease`, suite completa.
- Auditoría 48 dp, TalkBack, fuente 200 %, tema oscuro y una mano.
- `EXPLAIN QUERY PLAN` de la consulta QR y prueba de estrés.
- Política de migraciones y `MigrationTestHelper`.
- Documentación final e índice.

---

## 5. Estrategia de pruebas

| Tipo | Alcance | Herramientas |
|---|---|---|
| Unitarias JVM | RoleMapper, Haversine, validaciones, use cases con fakes | JUnit, `kotlinx-coroutines-test` |
| Instrumentadas | Room: schema, DAOs, FK, índices únicos, transacciones, Flow | `Room.inMemoryDatabaseBuilder`, AndroidJUnit4 |
| UI Compose | Estados, mensajes, 48 dp, permisos denegados | Compose UI Test |
| Manuales | Modo avión, doble escaneo, encuesta post-sync, rotación, muerte de proceso | Dispositivo/emulador |
| Sync | Worker, reintentos, idempotencia, fallo parcial | `TestListenableWorkerBuilder` + fake API |

**Comandos**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
```

**Definición de terminado por fase**

- Build verde.
- Tests de la fase pasando.
- Documento en `docs/` con qué se hizo, por qué y cómo probarlo.
- Sin warnings nuevos de Room.
- Si hay UI: mínimo 48 dp y estados vacío/carga/error.

---

## 6. Riesgos abiertos

| Riesgo | Mitigación |
|---|---|
| KSP/AGP 9 | KSP 2.3.10 validado; fallback 2.2.10-2.0.2 + flag experimental documentado. |
| JWT de 1 h sin refresh | Gracia offline 8 h + aviso; refresh token como deuda. |
| JDK 25 vs target Kotlin | Alinear `jvmTarget`/toolchain en Fase 1 o usar JDK 21 LTS en CI. |
| Roles reales desconocidos | `RoleMapper` tolerante + mínimo privilegio. |
| Backend de sync inexistente | Fake API + contrato documentado. |
| Doble marcación por carrera | Índice único + transacción + captura de constraint. |
| QR compartido por captura | Documentar; futuro QR firmado/rotativo. |
| Datos reales por error | Seed ficticio y checklist de privacidad. |

---

## 7. Registro de cambios

| Fecha | Cambio |
|---|---|
| 2026-10-22 | Plan aprobado al 100 %. Fase 0 ejecutada: baseline verde, spike KSP/AGP validado, documentación inicial creada. |
| 2026-10-22 | Fase 1 ejecutada: KSP 2.3.10 + Room 2.7.1 aplicados, `AppDatabase` con `Usuario`, schema v1 exportado y 6 tests instrumentados verdes. |
| 2026-10-22 | Backend actualizado: `API_BASE_URL` pasa a `https://app.claudioviajando.com/` en `.env` y en el fallback de `app/build.gradle.kts`; preview de sesión alineado. `BuildConfig.API_BASE_URL` verificado tras `assembleDebug`. |
| 2026-10-22 | Fase 2 ejecutada: 6 entidades, 5 enums nuevos, 7 DAOs, 4 proyecciones/relaciones, converters completos y `AppContainer` ampliado. Schema v1 consolidado (`identityHash = 5bf9e15a23f1b4b81272f91bc8f76f51`, 6 tablas / 13 índices). 18 tests instrumentados verdes. Ver `05_esquema_entidades_daos.md`. |
| 2026-10-22 | Fase 2 ejecutada: 6 entidades, 5 enums nuevos, 7 DAOs, 4 proyecciones/relaciones, converters completos y `AppContainer` ampliado. Schema v1 consolidado (`identityHash = 5bf9e15a23f1b4b81272f91bc8f76f51`, 6 tablas / 13 índices). 18 tests instrumentados verdes. |
