# P38 Journal — corrección 3.0: calendario desde 175.000 km

Encargo actualizado el 11/10/2026 e implementado en la app Android existente. Sustituye los criterios contradictorios de la versión 2. Conserva datos, applicationId y certificado de firma.

## Entrega disponible

Instala [p38-journal-3.0.0-release.apk](dist/p38-journal-3.0.0-release.apk) encima de la versión anterior, sin desinstalar. Android 8 o posterior. [APK debug separado](dist/p38-journal-3.0.0-debug.apk) para pruebas sin alterar tus datos de release. [Guía breve](docs/USER_TEST.md), [verificaciones reales](docs/TEST_REPORT.md), [revisión independiente](docs/INDEPENDENT_REVIEW.md) y [checksums](dist/SHA256SUMS.txt).

32 pruebas unitarias y 48 Android aprobadas; actualización v2→v3, avisos del sistema offline, reinicio y release firmado comprobados en emulador Android 16. No se probó un teléfono físico. La entrega es una app nativa local, sin servidor.

## Qué quiere el propietario

Una app sencilla que indique qué mantenimiento corresponde según el fabricante, cuándo toca y cuánto falta, tomando **175.000 km como referencia operativa inicial**. El propietario actualiza kilómetros a mano, registra lo realizado y añade gastos. La app calcula y avisa. No necesita crear manualmente reglas ni gestionar una revisión inicial por falta de historial.

Se elimina el mantenimiento inicial automático y la avalancha de inspecciones. Las comprobaciones que formen parte de una revisión real del fabricante se agrupan en esa revisión; no se convierten en decenas de tarjetas pendientes desde el primer día. Los controles diarios/semanales de uso no forman parte del calendario principal ni generan alertas por defecto.

## Uso

En el workspace de la app existente, sustituye la documentación anterior por estos archivos, conservando el código y datos. Pega en Codex el contenido de INICIO_CODEX.txt. Codex debe revisar el proyecto, aplicar cambios de motor/interfaz/migración, probar y entregar APK actualizado con la misma identidad y firma. No necesita volver a construir desde cero.

## Archivos

PROMPT_CODEX.md contiene el encargo; SPEC.md las pantallas; ENGINE.md el cálculo; MIGRATION.md la actualización segura; QA.md las pruebas; ANDROID.md las condiciones Android; AGENTS.md la coordinación; SOURCES.md los manuales; HANDOFF.md la entrega. data/ conserva el coche y un starter de fuentes, que nunca sustituye al catálogo completo verificado de la app.

## Resultado esperado

Navegación Inicio / Mantenimiento / Historial / Gastos. Inicio compacto: km actuales, próximo servicio, tareas próximas/vencidas y accesos a actualizar km/registrar trabajo. Detalle legible con nombre, intervalo, próximo límite, cuánto falta y botón «Registrar realizado». Fuente en sección secundaria desplegable. Gastos: lista y total, sin informes.

Android nativo Kotlin/Compose, datos locales Room y avisos del sistema. El teléfono no conoce el odómetro automáticamente: evalúa los límites por km al guardar una lectura; evalúa por tiempo en segundo plano. Los intervalos del fabricante no se alteran. Los 175.000 km son una referencia de planificación elegida por el propietario, no un servicio ficticio.
