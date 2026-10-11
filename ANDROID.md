# Corrección vigente4.1 / build5

REQUEST_V5.md sustituye el selector4.0: Mantenimiento muestra directamente las revisiones proyectadas completas. Recorrido: Repository.tasks → Engine.evaluate (vencimientos reales) → Forecast.project (apariciones independientes) → projectedMaintenanceGroups (ID por grupo/fecha/km) → ProjectedPlanPage → ProjectedRevisionDetail. Contador y «Qué incluye» consumen occurrences del mismo grupo, sin resolver el detalle desde una Task representativa.

Desde175.000 sin historial:195.00029,215.00033,235.00031; cifras de pruebas derivadas del catálogo, no constantes productivas. firstKm/firstMonths solo fijan la primera aparición; siguientes usan km/months recurrentes. Proyección condicionada a cumplir revisiones anteriores; no genera trabajos ni avisos. Home, Vencimientos reales, TaskDetail, NotificationEvaluator y WorkManager siguen utilizando únicamente Task reales. Registrar desde una revisión ofrece solo sus apariciones sequence0 que coinciden con el próximo vencimiento real; las siguientes son consulta.

La lista muestra los primeros cuatro grupos globales completos; operaciones propias aisladas con próximo vencimiento real permanecen visibles también fuera de ese horizonte. No se añaden grupos colectivos lejanos con una proyección truncada. Las operaciones reales completas siguen accesibles en Vencimientos reales. Trabajo parcial195.200 produce215.200 exacto para aceite; las restantes permanecen195.000. Fechas se recalculan por operación desde el trabajo real. Abrir una previsión no escribe Visit/Event/Reading.

Sin cambio de Room3, catálogo, applicationId ni firma. Conservar edición, filtros de gastos y copias. Pruebas de pantalla nativa y detalle completo, parcial, actualizaciones reales de APK y evidencias nuevas en docs/TEST_REPORT.md; resultados4.0 archivados como antecedentes.

## Antecedentes conservados donde sean compatibles

# Condiciones técnicas Android

Entrega vigente v4: `versionName=4.0.0`, `versionCode=4`, identidades y firma originales. Room continúa en esquema 3; reglas propias/personalizadas usan JSON compatible. La previsión de hitos permanece en memoria y no entra en notificaciones. Conservar ciclos v3 ya emitidos al actualizar y el recordatorio opcional de odómetro. [REQUEST_V4.md](REQUEST_V4.md) fija el alcance y [docs/TEST_REPORT.md](docs/TEST_REPORT.md) contiene las verificaciones efectivamente ejecutadas.

Conservar proyecto Kotlin/Jetpack Compose/Material 3, Room, DataStore, Coroutines/Flow y WorkManager existentes. Dominio puro con reloj inyectable y repositorios transaccionales. Verificar versiones estables compatibles/JDK/SDK/Gradle y conservar Wrapper/configuración reproducible. Base minSdk26 si ya aplica; no cambiar compatibilidad sin necesidad.

Notificaciones locales del sistema: permiso runtime cuando proceda, canales con estado visible, NotificationManager/PendingIntent seguro y destino correcto. Trabajo persistente WorkManager para calendario por tiempo, sin requerimiento de red. Evaluación al guardar km ejecutada inmediatamente tras commit, con recuperación persistente si proceso se interrumpe. No FCM, servidor, alarmas exactas ni servicio permanente. La app no lee odómetro mediante GPS/OBD: no hay detección de kilómetros nuevos hasta recibir lectura real.

Archivos/Room privados, SAF/Photo Picker para importar/exportar, sin permisos generales de almacenamiento/contactos/localización. Adjuntos copiados de forma controlada, límites/tipos/rutas validados y FileProvider seguro. Respaldo externo versionado de registros/gastos/adjuntos/preferences; migraciones no destructivas, restauración coherente y checksums. Copias internas no sobreviven a desinstalar. No secretos ni telemetría.

Preservar applicationId y clave de firma actuales. Incrementar versionCode y entregar release firmado/apksigner verificado, debug separado y SHA-256. Clave privada fuera de git/APK/logs; no sustituirla por una nueva en silencio. Actualización conserva datos sin desinstalar. Si clave o SDK faltan, identificar requisito concreto, no afirmar APK listo sin artefacto ni resolver limpiando datos.

Codex prepara/compila con Gradle Wrapper, SDK/JDK cuando permitido, scripts reproducibles Windows/Unix y ejecuta pruebas. Emulador/dispositivo si disponible; si no, especificar comprobaciones pendientes, sin afirmar recepción física. Artefactos en dist/ y guía corta de instalar/actualizar/copias. Sin publicación ni contratación.
