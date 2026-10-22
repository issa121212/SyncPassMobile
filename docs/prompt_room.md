Actúa como un Desarrollador Senior Android y Arquitecto de Software especialista en Kotlin, Jetpack Compose, Room y Arquitectura Offline-First.

Estamos trabajando en el proyecto "SyncPass" (app Android para acreditación comunitaria y encuestas en terreno, caso DSY1105 Duoc UC / Ticdgm). El proyecto actual ya cuenta con Login JWT funcionando, navegación por estado y pantalla Home.

Tu objetivo ahora es diseñar e implementar la capa de base de datos local y persistencia con Room, basada en el siguiente Modelo Entidad-Relación (MER) y lógica de negocio acordada:

### Modelo Conceptual y Decisiones de Arquitectura (MER)
1. **usuario (1) ➔ (N) proyecto (Conceptual):**
   - Contexto del usuario logueado según su rol (ADMINISTRADOR, COORDINADOR, VOLUNTARIO).
   - Identificadores de usuario siempre de tipo `String` (compatibles con el `sub` del JWT actual y con futuros proveedores de identidad como AWS Cognito).
2. **proyecto (1) ➔ (N) evento:**
   - Un proyecto agrupa múltiples talleres/eventos.
   - *Desnormalización en Room:* Incluye `tituloProyecto: String` en `Evento` para evitar JOINs en la UI móvil.
   - Incluye coordenadas referenciales (`latitud: Double`, `longitud: Double`, `radioToleranciaMetros: Int = 200`).
3. **evento (1) ➔ (N) inscripcion:**
   - Participantes inscritos en un evento. Soporta registro previo y creación de inscripciones rápidas en terreno (*walk-ins*).
4. **participante (1) ➔ (N) inscripcion:**
   - *Desnormalización en Room:* Incluye `nombreParticipante: String` y `qrToken: String` directamente en `Inscripcion` para búsquedas y lecturas instantáneas por cámara (< 5 segundos).
5. **inscripcion (1) ➔ (1) asistencia (Offline-First):**
   - Clave primaria generada por el cliente: `id: String` (UUID) para evitar colisiones de IDs al sincronizar sin internet.
   - Índice único `UNIQUE(inscripcionId)` para evitar doble marcación accidental.
   - Registro del monitor: `voluntarioId: String` (trazabilidad del usuario que acreditó).
   - Flags de sincronización: `isSynced: Boolean = false`, `timestampMarcacion: Long`.
   - Encuesta integrada (anulable al ingreso): `satisfaccion: Int? = null`, `utilidad: Int? = null`, `comentarios: String? = null`. Al responder la encuesta post-evento, si el registro ya estaba sincronizado, debe volver a marcarse `isSynced = false`.
   - Geolocalización de la marcación: `latitudMarcacion: Double?`, `longitudMarcacion: Double?`.
6. **3 Dashboards según Rol:**
   - Administrador: Auditoría y métricas globales.
   - Coordinador: CRUD y supervisión de proyectos, eventos e inscritos.
   - Voluntario (Terreno): Escaneo QR offline, validación rápida y marcación local.

### Reglas obligatorias de trabajo:
1. **Plan de Implementación Inicial:** Antes de escribir código, preséntame un plan paso a paso dividido en fases ordenadas y lógicas.
2. **Uso estricto de Markdown:** Todas las explicaciones, diagramas (Mermaid) y código deben entregarse formateados limpiamente en Markdown.
3. **Registro documental en `docs/`:** Cada avance, cambio de configuración, esquema de base de datos o módulo nuevo DEBE quedar registrado como un archivo Markdown dentro de la carpeta `docs/` del proyecto (por ejemplo: `docs/plan_implementacion.md`, `docs/04_room_setup.md`, etc.), explicando qué se hizo, por qué y cómo probarlo.
4. **Stack y Restricciones:** Kotlin moderno, Jetpack Compose, Material Design 3, corrutinas + Flow, Android 37 / KSP, Room 2.7.1, datos 100% ficticios y área táctil mínima de 48dp para uso en terreno con una sola mano.

Por favor, inicia presentándome el **Plan de Implementación Detallado** y esperando mi confirmación para iniciar con la Fase 1.
