# Verificaciones reales de entrega — 10/10/2026

Aplicación Android nativa compilada y firmada en este workspace. JDK 21.0.8, Gradle 8.13, SDK 36 y emulador `Medium_Phone_API_36.0`, Android 16/API 36, `emulator-5554`, WHPX y renderizado SwiftShader. No había teléfono físico conectado. Las pruebas usan APK debug, bases de datos temporales o fixtures identificados; el release distribuido no contiene operaciones, gastos ni documentos ficticios.

## Build y artefactos

Comando real: `scripts/build.ps1` (invoca `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain`). Resultado **BUILD SUCCESSFUL**, en `build-delivery.log`.

| Verificación | Resultado real |
|---|---|
| Motor, perfiles condicionados y catálogo | 26 pruebas unitarias aprobadas: 19 Engine, 3 ConditionalProfiles, 4 revisión independiente |
| Lint debug | 0 errores, 33 advertencias, sin baseline ni supresión para ocultarlas |
| APK debug | `dev.p38journal.debug`, versión 2.0.0-debug, versionCode 2, minSdk 26, targetSdk 36 |
| APK release | `dev.p38journal`, versión 2.0.0, versionCode 2, minSdk 26, targetSdk 36 |
| Firma | Ambos APK verificados con apksigner; release firma v2, RSA 3072 y certificado persistente |
| Instalación | Ambos APK definitivos instalados realmente en el emulador; release instalado encima del candidato anterior |

Certificado release SHA-256: `ce157b9b800d3e21c544ca21cbb398ffdfb3fab46ba5318adcc63d8cd14adf2b`. Huellas de los APK en `../dist/SHA256SUMS.txt`. Evidencia: `signature-release.log`, `signature-debug.log`, `apk-release-metadata.txt`, `apk-debug-metadata.txt`. La clave privada está fuera del repositorio y no se distribuye.

Las advertencias lint corresponden a versiones más recientes disponibles (13), sugerencias KTX (13), KSP (1), SharedPreferences commit usado en IO (1), comprobación de espacio (2) y tres TrustManager de la biblioteca criptográfica. No se usa transporte de red: el manifiesto APK no pide INTERNET; la app no tiene backend, IA remota, GPS ni servicio permanente. ACCESS_NETWORK_STATE/WAKE_LOCK/BOOT_COMPLETED/FOREGROUND_SERVICE son capacidades fusionadas por WorkManager; no implican una conexión o servicio permanente en la implementación.

## Pruebas Android

Comando real: `gradlew.bat connectedDebugAndroidTest --console=plain`. **BUILD SUCCESSFUL**. XML final: **33 pruebas registradas, 32 aprobadas, 0 fallos, 0 errores y 1 omitida intencionalmente**, porque la prueba de ciclo de instalación necesita tres etapas externas y se ejecuta por separado. El XML y los resultados unitarios/lint están copiados a `evidence/` para que posteriores builds no los sobrescriban.

| Área | Aprobadas | Cobertura comprobada |
|---|---:|---|
| Room/repositorio | 15 | Seed idempotente, datos históricos sin completar mantenimiento, transacciones, líneas/operaciones, factura canónica, edición/anulación, precisión, lecturas/correcciones, respaldo, overrides y actualización de catálogo |
| Migración | 1 | Esquema Room 1 → 2 con datos conservados, usando fixture de esquema previo |
| Archivos/avisos | 8 | ZIP íntegro y malicioso, bytes borrados y restaurados por hash, marcadores, dinero y ledger, concurrencia/reintentos, estados y cancelación de grupos obsoletos |
| Visor PDF | 2 | PDF con texto/búsqueda real/marcadores y PDF sin texto con búsqueda no disponible |
| Android real | 2 | WorkManager real ejecuta evaluación y copia interna; NotificationManager registra aviso de prueba con PendingIntent sin eventos de mantenimiento |
| Compose | 4 | Visita/confirmación, gasto exacto/recreación, perfil condicionado/evidencia/desactivación, lectura/avería/pieza/PDF vinculado/navegación atrás |

Las pruebas del motor incluyen fecha o km, ambas causas, meses/calendario, precisión parcial, referencias históricas, controles frecuentes y fases operativas explícitas. Las pruebas de transporte controlado comprueban permisos/canales y deduplicación lógica; no se presentan como recepción física. La migración usa un esquema previo de prueba: no existía un APK histórico publicado desde este workspace.

## Avisos visibles, segundo plano y reinicio

`scripts/verify-runtime.py` y su continuación `--resume-lifecycle` operaron sólo en el debug del emulador. Cinco comprobaciones **PASS** conservadas en `runtime-verification.json` y `.log`:

- Modo avión real en el emulador, permiso Android concedido y botón **Probar aviso**: notificación nativa visible en la cortina del sistema. Tocar su PendingIntent abre **Bandeja de avisos**.
- Hora de aviso ajustada explícitamente a la hora actual de Asia/Nicosia para esta prueba. Guardar ajustes y pulsar Home deja el launcher como Activity reanudada (`background-activity.txt`). WorkManager/JobScheduler ejecutan `MaintenanceWorker` con resultado SUCCESS en `background-work.log`. La cortina muestra un resumen de mantenimiento y resúmenes de controles/lectura sin red. Tocar el aviso de mantenimiento abre **Detalle de mantenimiento**.
- Cierre normal mediante Home y `am kill`: el trabajo periódico sigue registrado y la app vuelve a abrir. Evidencia en `jobs-after-process-kill.txt` y captura.
- Reinicio real `adb reboot`: sin abrir primero la app, se espera que Android entregue BOOT_COMPLETED y WorkManager recupere sus trabajos. Después se verifica la app visible. `jobs-after-reboot.txt` y `after-reboot.png` documentan recuperación; no se afirma emisión de un nuevo aviso justo al arrancar.
- `am force-stop` seguido de apertura explícita: se comprueba reprogramación en `jobs-after-force-stop-reopen.txt`. No se promete entrega durante la detención forzada.

El helper inicialmente no reconocía el formato de JobScheduler con namespace de API 36 y esperaba el arranque a los tres segundos. Se corrigieron el parser y la espera acotada de reprogramación; las pruebas reales anteriores de notificación se conservaron y las etapas de proceso/reinicio se repitieron. Las capturas son del emulador, no de un teléfono físico. El modo avión se desactivó al finalizar.

## Presentación y navegación

`scripts/verify-visual.py` obtuvo diez capturas reales: Inicio/Plan a 320/360/390/430 dp; Inicio/Ajustes a 320 dp en oscuro con fuente al 130%. Se inspeccionaron por el coordinador y el revisor; navegación, acciones y títulos se mantienen legibles, y la lectura de compra se diferencia de la actual. Se corrigieron los colores de contenedores Material 3 y se repitieron build, suite Android, ciclo de instalación y capturas con los APK definitivos. Sigue un detalle estético legible: la etiqueta de zona horaria ocupa dos líneas junto al borde del campo al 130%; no bloquea edición. Tamaño/densidad/fuente/tema del emulador se restituyeron al terminar.

## Actualización y recuperación tras desinstalar

Comando real: `python scripts/verify-lifecycle.py --old-apk .tools/prior-debug-v1.apk --new-apk dist/p38-journal-2.0.0-debug.apk --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Tres etapas **PASS** en `lifecycle-verification.json` y `.log`:

1. Instalación debug del candidato real versionCode 1 y creación de fixtures aislados: gasto de 12,50 €, lectura 176.001 km, avería resuelta, PDF privado vinculado y marcador de página; cero mantenimiento ficticio completado.
2. Instalación del APK definitivo versionCode 2 con `adb install -r`, sin desinstalar: registros, total, PDF/checksum y marcador conservados.
3. Desinstalación exclusivamente del debug del emulador, reinstalación limpia y recuperación del ZIP guardado en el host; reimportación dos veces sin duplicados. Se comparan bytes del ZIP transferido, registros, checksum PDF, marcador, total y lectura. Después se retiraron los fixtures mediante reinstalación limpia del debug para la revisión visual.

La copia es externa a los datos desinstalados y demuestra recuperación completa. El helper inicialmente truncó un ZIP al enviarlo por stdin del shell ADB; se corrigió usando transferencia binaria con `adb push` y comparación byte a byte antes de probar restore. No se alteró una instalación personal del usuario.

## Catálogo y revisión independiente

Se leyeron las 59 filas, columnas, notas y glosario de M01 visualmente; catálogo de 60 reglas, revisión 2, con fuentes/páginas/aplicabilidad. Se revisaron controles M02 y suplemento M04. Cobertura incluida/excluida/condicional en `CATALOGUE.md`; revisión propia de otro agente en `INDEPENDENT_REVIEW.md`. Se corrigieron ciclos desde la última operación, revisión de catálogo, recordatorio de odómetro, cancelación de resumen, recálculo al reanudar, marcadores en copias y perfiles con evidencia. PDFs privados de investigación y fixtures PDF no están en los APK distribuidos.

## Incidencias corregidas durante las verificaciones

Los logs anteriores se conservan como evidencia de iteración; no son resultados finales aprobados. Se corrigieron asset de test en contexto incorrecto, tipo de excepción ZIP rechazado por Android, métodos JUnit con retorno no void, cadenas Unicode, altura finita del visor PDF y sincronización Compose con Snackbar de guardado. Se repitieron las pruebas afectadas y después la suite completa. Una ejecución PDF interferida por apertura concurrente de otra Activity se repitió de forma exclusiva. No se eliminaron funcionalidades para obtener resultados aprobados.

## Límites concretos

- No se ha probado en un teléfono físico ni en otro fabricante/API. Compatibilidad declarada desde API 26; la ejecución real de esta sesión fue API 36.
- No se ha recorrido TalkBack con audio. Las pruebas Compose usan semántica; esto no equivale a una auditoría completa de accesibilidad.
- El respaldo se verifica a través de la misma API de archivos y el ciclo externo de instalación. El selector SAF y la revocación real de permisos de una carpeta externa no están cubiertos por esas pruebas automatizadas.
- La aceptación de NotificationManager y los transportes controlados no demuestran que una persona haya leído un aviso. Android decide cuándo ejecutar trabajo diferible; no se garantiza el minuto preferido ni ejecución durante detención forzada.
- Sin VIN/fecha exacta de matriculación/equipamiento confirmado, las condiciones siguen visibles y requieren confirmación. El conflicto documental SRS/pretensores se remite a revisión profesional; no se inventaron datos para resolverlo.
