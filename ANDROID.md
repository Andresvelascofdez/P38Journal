# Condiciones técnicas Android

Conservar proyecto Kotlin/Jetpack Compose/Material 3, Room, DataStore, Coroutines/Flow y WorkManager existentes. Dominio puro con reloj inyectable y repositorios transaccionales. Verificar versiones estables compatibles/JDK/SDK/Gradle y conservar Wrapper/configuración reproducible. Base minSdk26 si ya aplica; no cambiar compatibilidad sin necesidad.

Notificaciones locales del sistema: permiso runtime cuando proceda, canales con estado visible, NotificationManager/PendingIntent seguro y destino correcto. Trabajo persistente WorkManager para calendario por tiempo, sin requerimiento de red. Evaluación al guardar km ejecutada inmediatamente tras commit, con recuperación persistente si proceso se interrumpe. No FCM, servidor, alarmas exactas ni servicio permanente. La app no lee odómetro mediante GPS/OBD: no hay detección de kilómetros nuevos hasta recibir lectura real.

Archivos/Room privados, SAF/Photo Picker para importar/exportar, sin permisos generales de almacenamiento/contactos/localización. Adjuntos copiados de forma controlada, límites/tipos/rutas validados y FileProvider seguro. Respaldo externo versionado de registros/gastos/adjuntos/preferences; migraciones no destructivas, restauración coherente y checksums. Copias internas no sobreviven a desinstalar. No secretos ni telemetría.

Preservar applicationId y clave de firma actuales. Incrementar versionCode y entregar release firmado/apksigner verificado, debug separado y SHA-256. Clave privada fuera de git/APK/logs; no sustituirla por una nueva en silencio. Actualización conserva datos sin desinstalar. Si clave o SDK faltan, identificar requisito concreto, no afirmar APK listo sin artefacto ni resolver limpiando datos.

Codex prepara/compila con Gradle Wrapper, SDK/JDK cuando permitido, scripts reproducibles Windows/Unix y ejecuta pruebas. Emulador/dispositivo si disponible; si no, especificar comprobaciones pendientes, sin afirmar recepción física. Artefactos en dist/ y guía corta de instalar/actualizar/copias. Sin publicación ni contratación.
