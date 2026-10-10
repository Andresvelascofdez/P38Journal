# Arquitectura y entrega Android

## Proyecto

Kotlin, Jetpack Compose/Material 3, Room/SQLite, DataStore, Coroutines/Flow y WorkManager. Un módulo app con paquetes claramente separados es suficiente; crear módulos solo si ayudan a mantener contratos. Motor de reglas puro Kotlin independiente de UI/Android y reloj inyectable. Repositorios como única vía de escritura; ViewModels y flujos de estado lifecycle-aware.

Base minSdk 26. Seleccionar compileSdk/targetSdk estables actuales y compatibles, verificándolos en documentación oficial junto con JDK, AGP, Kotlin y librerías; documentar versiones reales y fijarlas. Gradle Wrapper, catálogo de versiones y resolución reproducible. No activar versiones preview por defecto. Compose preview/demo usa fixtures aislados; nunca los inserta en la base real.

Identidad release estable propuesta `dev.p38journal`; debug usa sufijo `.debug` para separar datos de pruebas. VersionCode creciente y versionName. Aceptar teléfonos de distintas densidades y tamaños; diseño en dp, fuentes en sp, insets, teclado, Back y restauración de estado tras muerte de proceso. Sin dependencia de navegador ni backend.

## Persistencia y archivos

Room para datos estructurados; archivos privados para adjuntos; DataStore para preferencias. No bloquear el hilo principal con I/O. Integridad transaccional en visitas/eventos/gastos, índices únicos de idempotencia y migraciones explícitas. No fallback destructivo. Actualizar catálogo mantiene overrides y revisiones de eventos.

Importar con SAF/Photo Picker y copiar bytes validados a ubicación privada controlada. No depender de una URI temporal que desaparece tras revocar permiso. Validar tamaños, tipos, nombres y espacio; evitar rutas arbitrarias al extraer copias. Compartir con FileProvider y permisos temporales, limitar componentes exportados y deep links. PDF con visor compatible y búsqueda textual real; documentar licencias y límites de la librería elegida.

Respaldo versionado incluye DB lógica/registros, preferencias de producto, catálogo/revisiones y adjuntos con checksums; excluye secretos de firma y datos técnicos temporales. Exportar por ACTION_CREATE_DOCUMENT; importar por ACTION_OPEN_DOCUMENT. Carpeta automática externa solo si el propietario la elige y su permiso persistente sigue válido. Copias internas rotadas sirven para recuperación rápida, no sobreviven a desinstalar. Explicar qué protege cada copia. Revisar Android Auto Backup: excluir secretos y no restaurar un ledger que silencie avisos sin reconstruir el estado. Restauración completa validada antes de transacción y archivos temporales con rollback; no dejar DB y adjuntos inconsistentes.

## Notificaciones

WorkManager con trabajo único persistente, sin requisito de red para evaluación local. Recalcular también al iniciar, cambiar lectura/trabajos/reglas/zona horaria o restaurar. Usar periodos admitidos por WorkManager; un periodo no asegura ejecución exacta. Preferencia de mañana y recordatorio semanal son decisiones de producto, implementadas con evaluación flexible. No AlarmManager exacto ni servicio permanente.

Canales estables para mantenimiento/resúmenes y avisos de prueba. Android 13+ requiere POST_NOTIFICATIONS en runtime. Solicitar tras acción «Activar avisos»; consultar también estado de canales, y permitir ir a Ajustes. Denegar no bloquea el uso. NotificationManager + PendingIntent seguros abren el destino correcto y Back conserva recorrido. Deduplicación persistente y cancelación de avisos obsoletos. Bandeja independiente del permiso.

Verificar cierre normal, muerte de proceso, reinicio y funcionamiento sin Internet. Diferenciar quitar de recientes de forzar detención en Ajustes. Después de una detención forzada no prometer avisos hasta abrir de nuevo. No pedir al propietario desactivar indiscriminadamente protecciones del móvil; ofrecer ayuda contextual si se observa una restricción.

## Preparación y compilación

Codex detecta Windows/Linux/macOS, SDK/JDK presentes y configura dependencias necesarias si puede. Preparar scripts bootstrap/build PowerShell y shell con detección clara, sin sobrescribir SDK ajenos ni instalar servicios globales innecesarios. Descargar únicamente dependencias oficiales/autorizadas; fallos de red se documentan. Mantener local.properties/rutas locales fuera de git. Licencias y cualquier acceso de sistema que requiera intervención real deben señalarse concretamente.

Compilación por Gradle Wrapper: validación del dominio, lint Android, ensamblado debug/release y pruebas instrumentadas cuando haya dispositivo. Android Studio no es requisito para ejecutar Gradle si SDK/JDK están listos. Codex ejecuta los comandos; el usuario no debe tener que compilar. Emulador si el entorno permite aceleración; si no, no afirmar pruebas de dispositivo. Adb solo con dispositivo accesible y autorización disponible; no fingir instalación remota en un móvil no conectado.

## Firma y actualización

Entregar debug APK y release APK firmado. Crear o reutilizar una clave privada local estable, almacenada fuera del repositorio; secretos desde configuración privada, nunca en código, APK, logs ni .env público. No generar nueva firma en cada compilación. Verificar firma con apksigner y registrar SHA-256 del APK. Documentar ubicación protegida y procedimiento de copia segura de la clave para el propietario: sin ella no se garantiza actualizar una instalación existente. Una clave nueva no sustituye otra en silencio.

Conservar applicationId/firma y aumentar versionCode al actualizar. Probar migración con APK anterior instalado, sin desinstalar ni borrar base. APKs en `dist/` con nombres/versiones; README con compatibilidad, instalación, actualización y copia externa. La instalación desde un archivo puede requerir permiso del instalador elegido, acción normal del propietario. Sin publicación en tiendas ni contratación. Si el entorno bloquea SDK o firma/compilación, entregar lo terminado y el requisito preciso; no llamar «APK listo» a código sin artefacto.
