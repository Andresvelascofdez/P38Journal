# Verificaciones reales — P38 Journal 5.0.0 · build 6

Ampliación de REQUEST_V6 ejecutada sobre la app existente con agentes de datos/migración, interfaz, avisos y revisión independiente. Código productivo del APK: **b68a9d171b20551d1767612e92a60a79fffb5d44**, base6957c1a. Sin modificaciones productivas posteriores; [huellas del workspace usado](v6-source-verification.json). Los cambios posteriores son documentación, capturas y comprobaciones por etapas. Entrega local: no se ha publicado ni enviado a GitHub.

Trabajos previstos/realizados reutilizan Visit y gastos; el estimado no genera gasto ni mantenimiento. Documentación libre requiere renovación confirmada. Repostajes tienen un gasto canónico y consumo ponderado entre llenos completos; no añaden kilómetros silenciosamente. Ruedas separan montaje/uso del próximo aviso y comparten visita/pago al cambiar ambas. Datos desconocidos siguen desconocidos. Room4/Snapshot4 conservan datos anteriores y nuevos.

## Resultados definitivos

| Comprobación | Resultado ejecutado |
|---|---|
| Unitarias |74 aprobadas,0 fallos/omisiones |
| Android API36 |108 casos XML:103 aprobados,0 fallos/errores,5 omitidos |
| Interfaz Compose |17 aprobadas:6 nuevas y11 de regresión |
| Actualización por etapas |3 etapas aprobadas: APK real4.1→5.0, Room3→4 y restauración ZIP dos veces |
| Aviso documental Android real |Caso omitido por permiso en suite, ejecutado aparte: OK1test |
| Lint |0 errores/fatales,33 advertencias,2 indicaciones |
| APK firmado instalado |Release code5→6 con -r, sin desinstalar ni borrar; firma original |
| Pantallas instaladas |Revisiones29/33/31, módulos nuevos, cuatro anchos y oscuro con fuente130% |
| Avisos manuales |Android visible, toques a eje/entrada correctos y revisión/renovación comprobadas |

Comando definitivo: `gradlew.bat testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug connectedDebugAndroidTest --console=plain --no-parallel --max-workers=1`. BUILD SUCCESSFUL3m43s; [salida](evidence/v6/delivery-validation.log), [XML y conteos](v6-tests-verification.json), [lint](evidence/v6/lint-final.xml). Se usa el XML para contar; el progreso del runner imprime totales mayores que los casos reales.

Las cinco omisiones son cuatro pruebas que necesitan orquestación explícita de versiones APK y el aviso documental con permiso. La etapa vigente UpgradeV6Test se ejecutó aparte sobre APKs reales; las otras tres son antecedentes y no se atribuyen como repetidas sobre5.0. El aviso documental se repitió concediendo permiso, publicado mediante NotificationManager y cancelado al renovar, sin tocarlo antes: [resultado](evidence/v6/real-deadline-delivery.txt). No se suma este caso dos veces al103.

## Comportamiento comprobado

La UI entra en Mantenimiento desde su pestaña, deriva29/33/31 del catálogo y abre los detalles completos cotejando IDs y contador. Aceite parcial195.200→215.200 exacto conserva pendientes las operaciones no realizadas; edición, personalización, documentos/PDF y filtros mes/año/tipo siguen probados. Catálogo, Forecast, MaintenanceGroups y pantalla de proyección permanecen; únicamente Engine añade el caso nuevo de trabajo sin fecha y km exactos mayores:178.000 fechado +180.000 sin fecha con intervalo10.000→190.000, manteniendo el plazo temporal anterior. Eventos fechados conservan su selección previa; anular recupera188.000. Cinco unitarias y una Room cubren esta frontera.

Las seis pruebas Compose nuevas registran/convierten un previsto al mismoID sin gasto/Event/Reading; compra REAL vinculada en ambas direcciones; mantenimiento explícito con km y fecha desconocida; notas amigables tras desvincular conservando presupuesto/líneas; repostaje decimal con pago único y sincronización desde Gastos; renovación de nombre libre/desactivación y montaje/revisión/cambio conjunto. Las15 pruebas nuevas de repositorio y8 Android independientes cubren edición/anulación, relaciones, copias antiguas/actuales y conflictos de restauración. Consumo:70L posteriores/500km=14,00, primer lleno excluido; acumulado ponderado, parciales abiertos e intervalos inválidos probados. Ruedas:175.000/185.200/10.000→195.200 y uso10.200; juego antiguo corregido no sustituye posterior.

La deduplicación conserva ledger emitted/Inbox read al apagar/encender avisos. Cambios de nombre/notas no generan un nuevo ciclo, colisiones de IDs tienen asignación separada, límites exactos y ausencia de avisos sin lectura real están probados. La revisión independiente incluye8 unitarias y8 Android propios; [informe](INDEPENDENT_REVIEW_V6.md).

## Actualización, copias y pantalla nativa

Fixture aislado en la app debug: APK4.1/code5 real crea lectura182.000, referencia175.000/01-10-2026, aceite personalizado10.000 y trabajo178.000, gasto300€, pago anulado, PDF/marcador, preferencias y aviso emitido/leído. Instalación5.0/code6 con -r conserva esos datos, no fabrica Fuel ni documentación/ruedas y crea respaldo SQLite+WAL previo verificable. Nuevos registros prueban trabajo libre de fecha desconocida/ID único, consumo14,00, historial documental y ambas ruedas/pago único: total540€ de fixture. Exportación ZIP4 y dos restauraciones recuperan relaciones, PDF/checksum/marcador y preferencias; inicialización y restore son idempotentes. [Etapas y hashes de ZIP](v6-lifecycle-verification.json). Sólo se desinstaló la app debug de prueba en el emulador para aislar/restaurar fixtures; release se conservó.

Los datos son ejemplos de prueba, no registros del propietario. La release del emulador tenía plan inicial; la actualización release5→6 conserva identidad y referencia, mientras el fixture debug verifica historial completo. [Firma e instalación](v6-release-install-verification.json); el SHA del APK instalado en ambos paquetes coincide exactamente con el archivo entregado: [verificación](v6-installed-verification.json).

24 PNG y sus jerarquías XML capturados realmente: [release Mantenimiento](screenshots/v6-release-maintenance.png), [consumo](screenshots/v6-fuel-summary.png), [trabajo sin fecha inventada](screenshots/v6-works-done.png), [historial documental](screenshots/v6-deadline-history.png), [modo oscuro320dp/130%](screenshots/v6-work-form-320dp-dark-font130.png). Matriz320/360/390/430dp con acciones rápidas. Se inspeccionaron muestras visualmente por coordinador y revisor.

Runtime manual sobre el mismo APK definitivo: lectura fixture195.199→195.200 publica aviso delantero con transporte Android real. Se expande el grupo del sistema y se toca el aviso individual; abre delanteras, montaje175.000/uso20.200/aviso195.200. Registrar nueva revisión195.200 desde UI conserva la anterior185.200, mantiene uso20.200 y programa205.200. Aviso documental abre su nombre exacto; la UI confirma la fecha sugerida real de prueba al renovar y conserva historial. Verificación posterior exige ausencia de los avisos obsoletos y datos correctos. La retirada se cotejó por los IDs manuales1355887020/484462842; el resumen sintético Android id0 conservó el aviso de fabricante1001. [Estado real e IDs](v6-runtime-notification-state.json). [Recorrido y comprobaciones](v6-native-verification.json), [avisos](screenshots/v6-notifications-manual-limit.png), [destino delantero](screenshots/v6-notification-opens-front.png), [nuevo plazo/uso](screenshots/v6-tyres-reviewed-keeps-usage.png), [destino documental](screenshots/v6-notification-opens-deadline.png).

Se corrigieron fallos del helper de pruebas (scroll de diálogos, fila/buscador, acceso Piezas). Una pasada mezcló el fixture migrado con pruebas que requieren plan inicial; se aisló únicamente debug. Ese intento también descubrió el defecto real de ancla sin fecha, corregido y cubierto, junto con notas JSON visibles al desvincular. La captura automatizada requirió respetar filtros guardados, expandir el grupo Android y distinguir acción nueva de tarjeta histórica. Los intentos fallidos están preservados en evidence/v6; no se presentan como aprobados. La última compilación/suite y los registros finales citados certifican esta entrega.

## APK identificables y límites

- [Release5.0.0](../dist/p38-journal-5.0.0-release.apk):20599208 bytes, SHA256`eb64a294f5728c3fc9eb89f63fab96f1c3970c68e236a7ac2a985e625bcb24be`.
- [Debug5.0.0](../dist/p38-journal-5.0.0-debug.apk):27655723 bytes, SHA256`fae0760fb12462c0477bdb11a39befe66a5c44f4977c94ff4486f0ae7ba999cf`.
- Certificado release SHA256`ce157b9b800d3e21c544ca21cbb398ffdfb3fab46ba5318adcc63d8cd14adf2b`, RSA3072 original; `dev.p38journal`. Debug usa`dev.p38journal.debug` y datos separados. min26/target36, sin permiso INTERNET, catálogo empaquetado idéntico al canónico. Clave privada externa, no incluida.

Versión visible v5.0.0·6 y v5.0.0-debug·6. [Instalar y probar](USER_TEST.md): actualizar release sin desinstalar. Windows/JDK21/Gradle8.13/AGP8.10.1/Kotlin2.0.21, emulador Medium_Phone_API_36.0 Android16/API36, SwiftShader. Ningún teléfono físico disponible; no se atribuyen pruebas de hardware, meses de uso ni modo avión nuevos. WorkManager y regresión de proceso cerrado se ejecutaron en SystemWorkTest; los avisos nuevos descritos se verificaron mediante evaluador/transporte real y UI, sin afirmar entrega en un instante exacto ni durante force-stop. Sin bloqueo de compilación, firma, migración o APK pendiente.
