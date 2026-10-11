# Verificaciones reales — P38 Journal 3.0

Corrección v3 ejecutada el 11/10/2026, fecha local Asia/Nicosia. Los timestamps UTC de algunas pruebas corresponden al 10/10 por la diferencia horaria. Esta entrega reemplaza la política v2 de mantenimiento inicial. [El informe v2](TEST_REPORT_V2.md) queda como antecedente y no certifica estos APK.

Entorno real: Windows, JDK 21.0.8, Gradle 8.13, AGP 8.10.1, SDK/build-tools 36, Android 16/API 36 en `Medium_Phone_API_36.0`, `emulator-5554`, WHPX/SwiftShader. No había teléfono físico conectado. Las pruebas utilizan debug, bases temporales y fixtures identificados; los APK distribuidos no contienen trabajos, gastos ni documentos de prueba.

## Build y pruebas definitivas

Comando ejecutado: `gradlew.bat testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug connectedDebugAndroidTest --console=plain --no-parallel --max-workers=1`.

**BUILD SUCCESSFUL** en [v3-final-validation-serial.log](v3-final-validation-serial.log). Después, `scripts/build.ps1` volvió a pasar y copió los APK definitivos a dist; [v3-delivery-build.log](v3-delivery-build.log). El único cambio posterior a la suite fue la metadata del estado de revisión del catálogo; ninguna regla ni código de cálculo cambió. El ciclo de instalación y los recorridos visuales usan los APK definitivos.

| Verificación | Resultado |
|---|---|
| Unitarias | **32 aprobadas**: 18 motor, 3 perfiles, 11 revisión independiente |
| Android | **49 registradas, 48 aprobadas, 0 fallos/errores y 1 omisión intencional** de la prueba por etapas, ejecutada aparte |
| Datos Android | 23 repositorio + 3 migración |
| Plataforma Android | 10 archivos/avisos + 4 WorkManager/NotificationManager reales + 2 PDF |
| Compose | 6 recorridos: gasto/recreación, confirmación de trabajo, revisión parcial, referencia editable, perfil secundario y lectura/avería/pieza/PDF |
| Lint | **0 errores y 33 advertencias**, sin baseline ni supresiones nuevas |

Los XML y lint definitivos están en [evidence/v3](evidence/v3). El contador de consola «Finished 50» incluye una diferencia del runner; el XML contiene 49 casos y es la fuente utilizada para este informe.

Lint advierte sobre versiones/KTX/KSP, commit de SharedPreferences, espacio disponible y tres TrustManager de la dependencia criptográfica. El APK no solicita INTERNET ni implementa transporte de red. Una ejecución conjunta anterior falló dentro del analizador Kotlin/FIR de lint, con un stub kapt ausente; se conservó [el log](v3-final-validation.log) y se repitió con compilación previa y tareas serializadas. No se desactivaron comprobaciones para conseguir el resultado.

## Cálculo y registro

El plan persistido parte de 175.000 km sin crear Reading, Visit ni Event. Las pruebas comprueban próximo aceite/filtro a 195.000; a 184.500 faltan 10.500, a 194.999 todavía no vence, a 195.000 vence y a 197.000 sigue pendiente. Una ejecución válida a 195.200 mueve esa operación a 215.200; registrar otra operación, una comprobación o un gasto no satisface el cambio.

La referencia temporal fixture 15/10/2026 genera 15/10/2027 para 12 meses. Fecha y km actúan por separado; ambas causas mantienen una tarea. Se verifican calendario/bisiesto, precisión parcial con referencia anterior/base, fases B/C, hitos únicos anteriores a la base, eventos anteriores y edición/anulación. Editar notas/taller conserva la generación efectiva y no reemite el mismo ciclo.

No se genera INITIAL. Los controles rutinarios no aparecen en el calendario ni alertan por defecto. Las 42 operaciones aplicables se agrupan inicialmente en seis límites; las 29 coincidentes del primer servicio conservan registro individual. Un trabajo parcial separa solo lo realizado. Las referencias manuales y perfiles condicionados siguen accesibles en opciones secundarias, con confirmación y evidencia, sin crear trabajos ficticios.

Gastos exactos: 100+25+60 = 185 €, editar gasolina a 55 → 180 €, anular extras → 155 €. Un pago de taller de 300 € repartido entre líneas se cuenta una sola vez. Reimportaciones, edición, anulación y pagos independientes están cubiertos.

## Avisos Android reales

`SystemWorkTest` comprueba NotificationManager, además de los transportes controlados:

- Guardar 194.999 → 195.000 emite 1001 fuera de la hora preferida, con PendingIntent y bandeja persistida. Repetir el valor, reabrir y reevaluar conserva el postTime/ledger. Completar aceite a 195.200 cancela el ciclo anterior; el cruce posterior de 215.200 emite un ciclo nuevo.
- Un vencimiento por fecha, sin lectura ni eventos, se evalúa con la actividad cerrada mediante WorkManager: resultado SUCCEEDED, aviso real con fecha y PendingIntent. La prueba verifica que no hay actividades STARTED/RESUMED.
- WorkManager real en una instalación limpia produce plan y copia interna, sin Inbox inicial ni servicios inventados. El aviso de prueba no completa mantenimiento.

Las pruebas controladas verifican permiso denegado, ventana horaria, transición SOON→DUE, salto con varias operaciones, deduplicación y resumen parcial. Si el resumen sigue visible, se actualiza silenciosamente al completar una parte; si ya se descartó, no se resucita. Los avisos manuales y de procedencia antigua desconocida se conservan al cambiar trabajo o plan.

El recorrido visible del APK definitivo terminó con **cinco comprobaciones PASS**: aviso de prueba offline y destino Bandeja; lectura manual 194.999→195.000 offline con aviso DUE y destino Detalle de mantenimiento; salida a Home y muerte normal del proceso; reinicio real del emulador con trabajos recuperados antes de abrir la app; detención forzada seguida de reapertura y reprogramación. Evidencias: [runtime JSON](v3-runtime-verification.json), [log](v3-runtime.log), [aviso por km](screenshots/v3-notification-km-crossing-offline.png) y [destino](screenshots/v3-notification-maintenance-destination.png). La recuperación tras force-stop no prueba entrega mientras la aplicación permanece detenida.

El log [v3-background-work.log](v3-background-work.log) conserva la ejecución real de MaintenanceWorker en segundo plano. Los dumps `v3-jobs-after-*.txt` y las capturas tras proceso/reinicio registran la continuidad. La primera ejecución del script visible falló al localizar un campo vacío; se conservó el log y se corrigió el selector antes del recorrido completo, sin cambiar el APK.

## Actualización y respaldo

La fuente/APK v2 y la documentación recibida se respaldaron en `.tools/pre-v3` con hashes antes de implementar. Al abrir una DB 1/2, la app crea una copia privada SQLite+WAL con SHA-256 e integrity_check antes de migrar; un error detiene la apertura. Se probó WAL real sin alterar el origen y reutilización del respaldo verificado.

`verify-lifecycle.py` ejecutó el ciclo con el APK debug v2 real, su test APK conservado y el APK debug v3 definitivo. Tras el seed nativo se preparó una fixture controlada SQLite de esquema 2 y un ZIP coherente: lectura **182.000**, dos operaciones reales de prueba a 178.000, pago vinculado 300 € + gasto 12,50 €, nota resuelta, PDF y marcador; creación del plan explícita en audit, pendiente INITIAL derivado y tarea CUSTOM del mismo título.

Resultados **PASS** en [v3-lifecycle-verification.json](v3-lifecycle-verification.json) y [log](v3-lifecycle.log): instalación `adb install -r` de code 2→3 **sin desinstalar**, datos/total **312,50 €**/PDF/hash/marcador conservados, lectura 182.000 y base 175.000, siguiente aceite 198.000 desde el trabajo; INITIAL cancelado y tarea manual preservada. El respaldo previo sigue en esquema 2 con la lectura original. Initialize repetido no duplica. Después, exclusivamente en debug del emulador, se desinstaló/reinstaló y restauró el ZIP dos veces sin duplicados.

Las migraciones 1→3 y 2→3, repetición idempotente, INITIAL huérfano sin ledger y CUSTOM/manual/formatos desconocidos están cubiertos en Android. No se identifican derivados por títulos. La restauración admite copias v2 y v3, conserva referencias editadas y personalizaciones, y valida bytes/checksums antes de integrar.

## APK y firma

Release `dev.p38journal`, 3.0.0/code 3; debug `dev.p38journal.debug`, 3.0.0-debug/code 3. Ambos minSdk 26/targetSdk 36. Apksigner verifica ambos; el certificado release coincide con el APK v2: `ce157b9b800d3e21c544ca21cbb398ffdfb3fab46ba5318adcc63d8cd14adf2b` (RSA 3072). La clave permanece fuera del workspace y de la entrega.

Evidencias [firma release](v3-signature-release.log), [firma anterior](v3-signature-previous-release.log), [firma debug](v3-signature-debug.log), metadata `v3-apk-*-metadata.txt` y [SHA256SUMS](../dist/SHA256SUMS.txt). La app conserva el ID, fichero Room y firma de actualización; debug mantiene datos separados.

También se instaló el **release definitivo sobre el release v2 existente** del emulador mediante `adb install -r`, sin desinstalar ni borrar datos: Success, code 2→3, arranque nativo y pantalla Inicio renderizada. [Registro](v3-release-install-verification.json) y [captura](screenshots/v3-release-home.png). Esa instalación no tenía una lectura real registrada: muestra 175.000 como «Valor inicial», siguiente revisión a 195.000, 20.000 km restantes y ninguna tarjeta inicial ficticia. La comprobación detallada de datos poblados corresponde al ciclo debug descrito arriba.

## Revisión visual e independiente

Se capturaron y revisaron Inicio/Mantenimiento a 320, 360, 390 y 430 dp, y Inicio/Ajustes a 320 dp oscuro con letra al 130%, además de avisos/destinos y continuidad: [log visual](v3-visual.log), archivos `screenshots/v3-*`. Contenido y acciones operables; en anchos estrechos la etiqueta Mantenimiento se parte dentro de la palabra y una etiqueta de fecha ocupa dos líneas al 130%. Es un detalle estético documentado, sin truncar valores ni impedir acciones. El release se inspeccionó por separado tras su actualización.

Los agentes de dominio, interfaz, catálogo y revisión independiente trabajaron sobre contratos compartidos. Diez hallazgos de revisión se corrigieron y la [revisión independiente](INDEPENDENT_REVIEW.md) quedó cerrada con las evidencias finales, sin defectos funcionales pendientes conocidos.

## Límites concretos

- Solo se ejecutó Android 16/API 36 en emulador; compatibilidad declarada desde Android 8/API 26. No se probó un teléfono físico, otro OEM ni TalkBack con audio.
- El recorrido de SAF y la revocación real del permiso de una carpeta externa no constituyen pruebas automatizadas completas. Las copias internas no sobreviven a desinstalar; usar ZIP externo.
- Android decide cuándo ejecutar trabajos diferibles por fecha. No se garantiza un minuto exacto ni ejecución durante una detención forzada; al reabrir se reprograma. Emitted demuestra llamada al sistema, no lectura humana del aviso.
- VIN/equipamiento/fecha original SRS y conflicto de pretensores siguen sin inventarse. Esas referencias condicionadas no se convierten en alertas desde la fecha de migración.

[Guía de instalación y prueba](USER_TEST.md) · [Catálogo y fuentes](CATALOGUE.md) · [Revisión independiente](INDEPENDENT_REVIEW.md).
