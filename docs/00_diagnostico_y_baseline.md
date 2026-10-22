# 00 — Diagnóstico y baseline

> **Fase:** 0  
> **Fecha:** 2026-10-22  
> **Objetivo:** registrar el estado real del proyecto antes de introducir Room, validar el build base y dejar evidencia reproducible.

---

## 1. Entorno de compilación

| Componente | Versión / valor |
|---|---|
| Sistema operativo | Windows 11 10.0 amd64 |
| JDK | Oracle JDK `25.0.2` (`C:\Program Files\Java\jdk-25.0.2`) |
| Gradle | `9.5.0` (wrapper) |
| AGP | `9.3.3` |
| Kotlin (plugin Compose) | `2.2.10` |
| `compileSdk` | `37` |
| `targetSdk` | `37` |
| `minSdk` | `24` |
| Compose BOM | `2026.02.01` |
| Room | `2.7.1` (a incorporar) |
| KSP | `2.3.10` (validado en spike) |
| Android SDK | `C:\Users\admn\AppData\Local\Android\Sdk` |

> El proyecto usa **Kotlin integrado en AGP 9**: no aplica `org.jetbrains.kotlin.android`; solo el plugin de Compose. Esto condiciona la integración de KSP.

---

## 2. Estado del proyecto al iniciar

| Área | Situación |
|---|---|
| Login | `LoginScreen` + `LoginViewModel` + `AuthRepository` con Retrofit/Gson. Guarda `access_token` en `LoginUiState`. |
| Navegación | `MainActivity` usa `rememberSaveable` para el token: `Login` si es `null`, `Home` si existe. |
| Home | `HomeViewModel` decodifica el JWT con `JwtDecoder` y expone `SessionUser`; `HomeScreen` muestra claims y logout. |
| Persistencia | No existe Room, DataStore, DAOs ni entidades locales. |
| DI | `LoginViewModel` crea `AuthRepository` por defecto; no hay `Application` ni contenedor. |
| Manifest | No declara `CAMERA` ni ubicación. `INTERNET` aparece en el manifest fusionado por una dependencia, pero no está declarado explícitamente. |
| Tests | Solo `ExampleUnitTest` y `ExampleInstrumentedTest` de plantilla. |
| Docs | Solo `docs/prompt_room.md`. |

### Hallazgos relevantes

1. **El token vive solo en memoria.** Un cierre de proceso obliga a volver a iniciar sesión, lo que es incompatible con el trabajo en terreno sin red. Se resolverá en Fase 5 con DataStore.
2. **No hay contexto de usuario persistido.** El JWT se decodifica en cada `HomeViewModel`; no existe `UsuarioEntity` local.
3. **Permisos implícitos.** `INTERNET` llega por fusión de una dependencia. Se declarará explícitamente en Fase 1, junto con `ACCESS_NETWORK_STATE`; cámara y ubicación se agregan en Fase 6.
4. **AGP 9 + KSP.** El proyecto no tiene KSP configurado. La validación era el principal riesgo técnico de la fase.
5. **JDK 25.** Kotlin advirtió en una de las pruebas: “Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_24 JVM target”. No bloqueó el build, pero se debe alinear el target o usar JDK 21 LTS en CI/Fase 1.
6. **Configuration cache activo.** `org.gradle.configuration-cache=true`; los builds exitosos del spike lo mantuvieron activo.

---

## 3. Baseline del build

**Comando ejecutado en el proyecto real:**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```

**Resultado:**

```text
BUILD SUCCESSFUL in 37s
38 actionable tasks: 38 executed
Configuration cache entry stored.
```

Artefacto generado: `app/build/outputs/apk/debug/app-debug.apk`.

**Conclusión:** el proyecto compila sin cambios antes de introducir Room/KSP. El baseline queda congelado como referencia.

---

## 4. Spike KSP/AGP

Se realizó en una copia temporal para no contaminar el proyecto real:

```text
C:\Users\admn\AppData\Local\Temp\opencode\SyncPass-ksp-spike
```

Se agregaron temporalmente:

- Plugin KSP.
- Room `2.7.1` (`room-runtime`, `room-ktx`, `room-compiler`).
- `SpikeEntity`, `SpikeDao`, `SpikeDatabase`.
- `room.schemaLocation`.

**Resultado resumido:** KSP `2.3.10` + Room `2.7.1` compila limpio, genera `SpikeDao_Impl.kt`, `SpikeDatabase_Impl.kt` y `app/schemas/.../1.json`.

El detalle completo, los errores del candidato descartado y la configuración recomendada están en [`03_room_ksp_setup.md`](03_room_ksp_setup.md).

---

## 5. Evidencia del spike

> Evidencia obtenida en la **copia temporal** `SyncPass-ksp-spike`; el proyecto real aún no tiene Room/KSP.

| Evidencia | Resultado |
|---|---|
| `clean :app:assembleDebug` con KSP 2.3.10 | `BUILD SUCCESSFUL in 11s` |
| `:app:testDebugUnitTest` | `BUILD SUCCESSFUL in 6s` |
| Código generado | `SpikeDao_Impl.kt`, `SpikeDatabase_Impl.kt` |
| Schema exportado | `app/schemas/com.rojas.syncpass.spike.SpikeDatabase/1.json` |
| APK debug | `app-debug.apk` (~19.5 MB) |
| Configuration cache | Reutilizado correctamente |

---

## 6. Decisiones derivadas

| Decisión | Motivo |
|---|---|
| Usar KSP `2.3.10` | Compila sin flag experimental y corrige la integración con Kotlin integrado de AGP 9. |
| Mantener `2.2.10-2.0.2` como fallback | Es la versión que declara AGP 9.3.3; requiere `android.disallowKotlinSourceSets=false`. |
| No modificar el proyecto real en Fase 0 | El spike se hizo en copia temporal; la configuración se aplicará formalmente en Fase 1. |
| Declarar permisos explícitos | Evitar dependencia de permisos transitivos de librerías. |
| Alinear target JVM en Fase 1 | Evitar el fallback a JVM 24 por JDK 25. |

---

## 7. Próximos pasos (Fase 1) — completados

> Fase 1 ejecutada el 2026-10-22. Resultado y evidencia en [`04_room_setup.md`](04_room_setup.md).

1. Aplicar KSP `2.3.10` y Room `2.7.1` al proyecto real.
2. Crear `AppDatabase` con `UsuarioEntity` como vertical slice.
3. Configurar `room.schemaLocation` y verificar `1.json`.
4. Crear `SyncPassApplication` + `AppContainer`.
5. Declarar `INTERNET` y `ACCESS_NETWORK_STATE` en el manifest.
6. Ejecutar test instrumentado de apertura/CRUD de la DB.
7. Documentar en `04_room_setup.md`.

---

## 8. Cómo reproducir este diagnóstico

```powershell
# Versiones
java -version
.\gradlew.bat --version

# Baseline
.\gradlew.bat :app:assembleDebug

# Spike (en la copia temporal)
cd C:\Users\admn\AppData\Local\Temp\opencode\SyncPass-ksp-spike
.\gradlew.bat clean :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

> La copia temporal puede eliminarse sin afectar el proyecto. La configuración exacta queda documentada en `03_room_ksp_setup.md`.
