# Verificaciones reales — P38 Journal 4.1.0 / build 5

Corrección de REQUEST_V5 ejecutada sobre el proyecto existente con agentes de interfaz, pruebas y revisión independiente. Fuente productiva del APK: commit local **fd5a1b27ade4b718de2303244d2e25297aad4b36**, base1315c56. No hay cambios productivos posteriores a ese commit; [huellas de fuente](v5-source-verification.json). Los cambios posteriores son documentación y herramientas de captura. No se ha publicado ni enviado a GitHub.

Mantenimiento abre directamente la proyección completa. Engine mantiene vencimientos reales; Forecast genera apariciones; projectedMaintenanceGroups agrupa por servicio/fecha/km; tarjeta y detalle consumen exactamente occurrences del mismo grupo. No se codificaron29/33/31 en la interfaz ni se crearon Task artificiales. firstKm/firstMonths fijan el primer plazo; los siguientes usan intervalos recurrentes. Los ciclos futuros presuponen realizar los anteriores cuando toca. Home, avisos y registro conservan Task reales.

## Resultados definitivos

| Comprobación | Resultado real |
|---|---|
| Unitarias |49 aprobadas, 0 fallos: 22 motor, 3 perfiles, 11 catálogo, 8 revisión anterior, 5 contrato de grupos de esta pantalla |
| Android en emulador API 36 |70 casos XML: 67 aprobados, 0 fallos/errores, 3 omitidos porque requieren orquestación explícita de APK |
| Interfaz Compose |11 aprobadas, incluidas pantalla principal y revisión parcial; conservadas edición, gastos, formularios y documentos |
| Actualización y restauración por etapas |3 etapas aprobadas: fixture en APK 4.0, actualización 4.0→4.1 sin desinstalar y restauración ZIP dos veces |
| Lint |0 errores/fatales, 33 advertencias, 2 indicaciones |
| Pantalla nativa instalada |195.000 km: 29; 215.000 km: 33; 235.000 km: 31. Encontrados todos los títulos al recorrer cada detalle |
| Notificaciones/runtime |5 comprobaciones aprobadas, descritas abajo |
| Firma y versión |Release 4.1.0/build 5, mismo applicationId y certificado; debug separado |

La prueba `maintenanceDefaultShowsFourCompleteRevisionsAndEveryCatalogueOperationWithoutInventingWork` entra desde la navegación Mantenimiento sin selector, verifica195k29/215k33/235k31/255k33, abre cada tarjeta, compara todos sus IDs con el catálogo mediante cálculo independiente y coteja contador/lista. Comprueba que consultar no añade Visit/Event/Reading y que registro ofrece únicamente los próximos vencimientos reales29/6/2/0.

`groupedReviewRecordsOnlySelectedOperationWithPrefilledDateAndKilometres` registra mediante esta pantalla solamente aceite a195.200. Confirma un solo trabajo satisfecho, vencimiento215.200 exacto, otras28 operaciones aún195.000 y tarjeta/detalle195k con28 IDs; abre el hito215.200 de una operación y su detalle real. No cambia el odómetro ni la referencia por registrar una visita. La prueba de edición de fase conserva personalización/restauración y ausencia de trabajos ficticios.

La comprobación adicional de APK instalado usa accesibilidad Android y capturas reales, sin inyectar contadores. Recorre todos los títulos en cada «Qué incluye»; [resultado nativo](v5-maintenance-screen-verification.json). Comprobados320/360/390/430dp, modo oscuro320dp y fuente130%, y retorno editor→operación→misma revisión. 29 capturasPNG/XML nuevas; se inspeccionaron visualmente por coordinador y revisor. [Release en Mantenimiento](screenshots/v5-release-maintenance.png), [detalle215k](screenshots/v5-revision-215000-includes-first.png), [detalle235k](screenshots/v5-revision-235000-includes-first.png), [retorno de edición](screenshots/v5-return-editor-to-same-revision.png).

Actualización con fixture aislado: lectura182.000, referencia175.000/01-10-2026, aceite personalizado10.000 y trabajo178.000, gastos personalizados, pago anulado, PDF/marcador, preferencias y aviso emitido/leído. Actualizar conserva esos datos, no vuelve a emitir el ciclo con permiso/canal realmente concedidos y permite editar trabajo a178.500→188.500; pago total310€, propias desactivadas/únicas y dos restauraciones comprobadas. Son datos de prueba, no registros del propietario. Release4.0→4.1 también instalado con `-r`, sin borrar ni desinstalar; la instalación release del emulador tenía el plan inicial. [Ciclo y restores](v5-lifecycle-verification.json), [instalación/firma](v5-release-install-verification.json).

Avisos locales comprobados sin conectividad: notificación visible de prueba y destino Bandeja; lectura manual194.999→195.000 produce aviso real y abre detalle de mantenimiento. Comprobados proceso cerrado, reinicio real del emulador con trabajos WorkManager conservados y force-stop seguido de apertura con reprogramación. [Resultados runtime](v5-runtime-verification.json), volcados de jobs/actividad/logcat `v5-*`. No se garantiza entrega mientras Android mantiene la app forzada a detenerse.

## Artefactos identificables

- [Release nuevo](../dist/p38-journal-4.1.0-release.apk):20435368 bytes; SHA256`7fbcdcec1457a1cda65fd198f2acd0eb5b5a7cc0404115efb139d97f7d96bf27`.
- [Debug nuevo](../dist/p38-journal-4.1.0-debug.apk):27598229 bytes; SHA256`4d2d5083ef9a98ba7910c6b76717fd87bfc2ba3a6af3a8992b6093a5c8cbae9a`.
- Certificado release SHA256`ce157b9b800d3e21c544ca21cbb398ffdfb3fab46ba5318adcc63d8cd14adf2b`, RSA3072 original. minSdk26/target36, sin permiso INTERNET, catálogo empaquetado idéntico al canónico. Room sigue esquema3; no se cambió catálogo ni repositorio de datos. Clave externa no incluida.

Versión/build visibles en todas las pantallas: «v4.1.0 ·5» release y «v4.1.0-debug ·5» debug. Instalar release encima del anterior sin desinstalar. [Guía breve](USER_TEST.md).

## Evidencia y límites

Windows/JDK21.0.8/Gradle8.13/AGP8.10.1/Kotlin2.0.21/build-tools36. Emulador Android16/API36 Medium_Phone_API_36.0, emulator-5554, SwiftShader; ningún teléfono físico disponible. No se comprobó hardware de otros fabricantes ni un mes real de uso. Android puede retrasar trabajos por fecha bajo restricciones del sistema. No hay bloqueos pendientes para esta entrega.

Comandos ejecutados: `scripts/build.ps1` (unitarias, debug, release, testAPK ylint), `gradlew.bat connectedDebugAndroidTest`, testAPK por etapas y scripts verify-v5-artifacts/lifecycle/maintenance-screen/runtime. [XML y logs](evidence/v5), [resumen contado desde XML](v5-tests-verification.json), [revisión independiente](INDEPENDENT_REVIEW_V5.md). ConsolaGradle llegó a73/70 por seguimiento de omitidos; los70 casos del XML son la referencia, sin inflar resultados. La fuente del test por etapas se afinó después de la suite, se recompiló y aprobó esas etapas; no hubo cambios productivos después de la suite.

Intentos fallidos conservados y corregidos: helperCompose intentaba abrir «Qué incluye» ya desplegado al volver; fixtureunitarioGson no normalizaba notes/origin; arranqueadb temporal; fixture de actualización usaba prefijo de ledger incorrecto, origen crudo no personalizado y API de creación en lugar de personalización; barrido nativo rápido saltaba títulos y segundo intento conservaba scroll profundo. Los logs/XML/JSON parciales correspondientes están archivados, y los resultados definitivos separados. Los fallos no se contabilizan como aprobados. Antecedentes4.0 archivados en *_V4.md y evidence/v4; no certifican4.1.
