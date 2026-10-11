# Verificaciones reales — P38 Journal 4.0

Encargo [REQUEST_V4](../REQUEST_V4.md), ejecutado el 11/10/2026, Asia/Nicosia. Se corrigió la app existente con agentes de dominio/datos, interfaz y revisión independiente. Los resultados v3 archivados en [TEST_REPORT_V3](TEST_REPORT_V3.md) son antecedentes y no certifican estos APK.

Entorno: Windows, JDK 21.0.8, Gradle 8.13, AGP 8.10.1, Kotlin 2.0.21, SDK/build-tools 36. Emulador Android 16/API 36 `Medium_Phone_API_36.0`, `emulator-5554`, WHPX/SwiftShader. No había teléfono físico. Fixtures identificadas y aisladas en debug/bases de prueba; no se incluyen registros ni documentos de prueba en los APK.

## Build y pruebas

Comando serial ejecutado: `gradlew.bat testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug connectedDebugAndroidTest --console=plain --no-parallel --max-workers=1`. Evidencia de entrega en [v4-delivery-validation.log](v4-delivery-validation.log), XML/lint en [evidence/v4](evidence/v4). La suite limpia anterior también pasó: [v4-clean-instrumentation.log](v4-clean-instrumentation.log), [XML conservado](evidence/v4-clean-run).

| Verificación | Resultado |
|---|---|
| Unitarias | **44 aprobadas**, sin fallos: Engine 22, perfiles 3, catálogo independiente 11, revisión v4 8 |
| Android | **69 casos en XML, 67 aprobados, 0 fallos/errores, 2 omisiones por etapas** |
| Datos Android | Repositorio 23, migración 3, reglas/pagos v4 11 |
| Plataforma Android | Archivos/avisos 11, WorkManager/NotificationManager reales 6, PDF 2 |
| Compose | 11 recorridos de pantallas reales |
| Lint | 0 errores, 33 advertencias y 2 sugerencias; sin supresiones nuevas |
| Ciclo v3→v4 | Tres etapas reales aprobadas: seed en APK v3, actualización con datos, reinstalación/restauración repetida solo debug |

El XML determina los conteos; el runner imprime un contador superior. `DeliveryLifecycleTest` es el recorrido v2→v3 archivado y no se repitió en v4; `UpgradeV4Test` se omite sin argumento y se ejecutó aparte. No se contabilizan sus tres etapas como 67 pruebas adicionales. El build de entrega [v4-delivery-build.log](v4-delivery-build.log) también pasó y copió los APK finales; el único cambio posterior a la suite general fue exigir permiso/canal permitido en la prueba por etapas, recompilada y ejecutada realmente después. Los APK de producción conservan exactamente los hashes verificados.

Se conservan los intentos fallidos: [primer build](v4-first-build.log) por API inexistente de colección Compose, [primera suite](v4-validation.log) por falta de espera tras guardar lectura, [segunda suite](v4-final-validation.log) con tres fallos de recorridos UI después de poblar datos del ciclo de actualización, y [build intermedio](v4-delivery-first-attempt.log) por una referencia de test a Espresso sin dependencia. Se corrigieron los selectores/esperas y se usó clic semántico comprobando el estado del checkbox; no se añadió dependencia ni se alteró el cálculo para satisfacer fixtures. Se separaron las pruebas de plan inicial de las de actualización poblada. Las ejecuciones limpias pasan y los XML fallidos quedan en `evidence/v4-first-run` y `v4-second-run`.

Las advertencias lint se refieren principalmente a versiones/KTX/KSP, SharedPreferences, almacenamiento y TrustManager de una dependencia; no se agregó transporte ni permiso INTERNET. No se desactivaron comprobaciones para conseguir el resultado.

## Cálculo, catálogo y formularios

Catálogo canónico de 62 reglas/revisión 3 **sin cambios de frecuencias**. Revisión independiente de las fuentes/matriz M01 y cobertura: [CATALOGUE](CATALOGUE.md), [INDEPENDENT_REVIEW_V4](INDEPENDENT_REVIEW_V4.md). Los bytes del catálogo incluidos en ambos APK coinciden con `data/maintenance-catalogue.json`.

Referencia inicial 175.000 separada de Reading/Event/Visit y fecha configurada preservada. Sin INITIAL ni tarjetas por falta de historial. Los próximos vencimientos reales iniciales tienen 29 operaciones a 195.000 y seis a 215.000; la previsión condicional tiene 33 a 215.000 (27 comunes + seis C). B comienza a +20.000/12 meses y repite cada 40.000/24 meses; C comienza a +40.000/24 y repite 40.000/24. La previsión siguiente de 235.000 contiene 31, incluidas dos operaciones adicionales coincidentes; 255.000 vuelve a 33. No se copia indiscriminadamente el primer grupo.

Las ocho pruebas independientes v4 verifican conjuntos y fases, aceite a 195.200 → 215.200, B a 195.200 → 235.200, parcial sin cerrar lo pendiente, tiempo/km por lo que llegue antes y previsión sin cambiar generación. La UI muestra los primeros cuatro hitos para no presentar grupos lejanos incompletos; no permite registrar previsión ni persistirla como evento/lectura/visita/aviso.

Compose comprueba acceso individual desde grupo y personalización de fase a 10.000 km/6 meses (fixture, no fabricante), eliminación de primeros desfases y restauración; operación propia sin URL, edición/notas/desactivación y única completada sin repetición. Una recurrente existente no se convierte desde UI a otra cita única: crear una tarea nueva evita que su historial complete una cita futura. Dominio conserva compatibilidad con JSON único existente/importado. Visitas nuevas empiezan sin selección; confirmación de fecha/km/operaciones, edición con selección real, trabajo parcial y búsqueda por operaciones están cubiertos. Se mantienen averías, piezas y PDF; su recorrido de regresión también pasó.

## Gastos y vínculos

Pruebas independientes y Compose cubren combinaciones mes/año/tipo, todos los años, año disponible, vacío/0, navegación y recreación, edición de fecha/tipo/anulación y subtotal frente a total activo. Fechas de calendario locales y céntimos Long, sin Double. Gasolina no altera mantenimiento ni odómetro.

Once pruebas Android nuevas cubren identidad/historial de reglas y pagos: normalización de enlaces inactivos existentes y restaurados Snapshot 2/3, auditoría, reemplazo tras anular, ausencia de doble importe y metadatos de gasto personalizados preservados al editar visita. Un gasto de 300 € con líneas 150+150, corregido a 310 desde Gastos, permite editar notas/km manteniendo las líneas históricas; no se inventa reparto 155+155. Intentar cambiarlo a 160+140 se rechaza y revierte; 160+150 se acepta. También se verificó este caso en Compose.

## Avisos reales

Seis pruebas `SystemWorkTest` consultan NotificationManager/WorkManager reales: cruce manual 194.999→195.000 fuera del horario, deduplicación/postTime, cancelación al completar y nuevo ciclo, trabajador de fecha con actividad cerrada, planificación/respaldo sin Inbox inicial, aviso de prueba sin eventos, cambio de regla que genera/cancela su límite y recordatorio opcional de odómetro de siete días. Una lectura nueva cancela ese recordatorio sin inventar kilometraje.

Las once pruebas de plataforma también cubren permiso denegado, horario, SOON→DUE, resumen parcial silencioso y resumen descartado que no reaparece. El alias de generación v3 conserva estado emitido y bandeja leída al actualizar sin cambio efectivo. Nombre/notas no reinician ciclo; intervalo/trabajo/activación sí recalculan y cancelan lo obsoleto. Los hitos hipotéticos no llegan al evaluador de avisos.

La evidencia de los tests del sistema es independiente de transportes simulados. Emitir demuestra entrega al sistema, no lectura humana. El recorrido visible offline y de continuidad se describe en la sección siguiente.

## Pantallas y recorrido visible

Se capturaron y revisaron 18 vistas de matriz: Inicio, plan real, previsión superior/inferior a 320/360/390/430 dp y oscuro a 320 dp con fuente al 130%. [Log visual](v4-visual.log), archivos `screenshots/v4-*`. Otras once capturas a 360 dp muestran grupo, fase B y editor, formularios propios recurrente/única, visita nueva sin selección, historial/búsqueda y gastos con filtros/subtotales. [Log suplementario](v4-details.log); su filtro vacío combina Enero/Todos los años/Taller, subtotal 0 y total 0. Los importes con fixtures pobladas se comprueban en Compose y repositorio, no se deducen de capturas vacías.

Los primeros scripts de captura fallaron al buscar un selector fuera de pantalla, un mes fuera del área visible del desplegable o al asumir que Volver desde visita abría el detalle en vez del plan. Se corrigieron los recorridos/scroll y se conservaron `v4-visual-first-attempt.log` y `v4-details-*-attempt.log`; ninguna corrección del script cambió el APK. Los recorridos finales de captura pasaron. Revisión propia del coordinador y [revisión independiente](INDEPENDENT_REVIEW_V4.md).

El recorrido visible del APK final dio **cinco comprobaciones PASS**: aviso de prueba sin Internet y destino Bandeja; lectura manual 194.999→195.000 sin Internet, aviso DUE y destino Detalle; salida a Home/muerte normal del proceso y trabajos conservados; reinicio real del emulador con WorkManager recuperado; force-stop seguido de reapertura y reprogramación. [JSON](v4-runtime-verification.json), [log](v4-runtime.log), [aviso visible](screenshots/v4-notification-km-crossing-offline.png) y [destino real](screenshots/v4-notification-maintenance-destination.png). Se capturaron seis vistas y dumps de jobs tras proceso/reinicio/force-stop. Recuperación tras reabrir no significa entrega mientras la app sigue detenida. Los logs de trabajo/actividad `v4-background-*` pertenecen a este recorrido; el caso específico por fecha con actividad cerrada está verificado en SystemWorkTest.

## Actualización y restauración

Fuente y APK v3 respaldados antes de editar en `.tools/pre-v4`, ignorado. Room continúa en esquema 3; metadata JSON aditiva y Snapshot 2/3, sin borrado ni migración destructiva. Migraciones anteriores 1/2→3 y respaldo previo DB/WAL siguen cubiertos.

[verify-v4-lifecycle.py](../scripts/verify-v4-lifecycle.py) ejecuta **APK debug v3 real → APK debug v4 mediante install-r**, sin desinstalar entre versiones. Después reinstala únicamente debug del emulador para restaurar dos veces el ZIP v4. Tres etapas PASS en [v4-lifecycle-verification.json](v4-lifecycle-verification.json) y [log](v4-lifecycle.log). Fixture: lectura 182.000, base175.000/fecha01-10-2026, aceite personalizado10.000 hecho a178.000/editado178.500 →188.500, metadatos personalizados del pago, enlace inactivo antiguo reparado, gasto total310 €, PDF/hash/marcador, preferencias777km y recordatorio odómetro, nueva regla propia desactivada y tarea única completada. Initialize/reimportación no duplican. La actualización conserva un ciclo v3 emitido/leído; el recorrido definitivo concede permiso Android antes de comprobar ausencia de reemisión real.

## APK y firma

4.0.0/code4: release `dev.p38journal`, debug `dev.p38journal.debug`, min26/target36. Apksigner verifica ambos y el certificado release coincide con v3: `ce157b9b800d3e21c544ca21cbb398ffdfb3fab46ba5318adcc63d8cd14adf2b`, RSA3072. La clave privada original permanece fuera del repositorio. [Firma release](v4-signature-release.log), [firma anterior](v4-signature-previous-release.log), [debug](v4-signature-debug.log), metadata `v4-apk-*-metadata.txt`, [hashes](../dist/SHA256SUMS.txt).

El **release v4 definitivo se instaló realmente sobre release v3** existente con `adb install -r`, Success, code3→4, sin desinstalar ni borrar datos, y renderizó Inicio nativo: [registro de artefactos/actualización](v4-release-install-verification.json), [captura](screenshots/v4-release-home.png). Esa instalación no tenía lectura real; la preservación poblada se comprueba en el ciclo debug descrito. Los archivos de APK se entregan en `dist/` y quedan ignorados por git; no hay publicación v4.

## Límites concretos

- Solo Android 16/API36 en emulador. Android8/API26 es el mínimo declarado; no se probaron teléfono físico, otros OEM ni TalkBack con audio.
- A 320 dp y fuente al 130%, el rótulo abreviado de mantenimiento pierde sus últimos caracteres. Conserva icono, acción y descripción accesible completa; no se parte en dos líneas. Es un detalle cosmético documentado.
- SAF/revocación real de carpeta externa no tienen un recorrido automatizado completo. Usar ZIP externo; las copias internas no sobreviven a desinstalar/perder el teléfono.
- Android puede diferir WorkManager; no se garantiza minuto exacto ni ejecución durante force-stop. Reabrir reprograma. El odómetro depende de lecturas manuales.
- VIN/equipamiento/fecha original SRS y condiciones del catálogo siguen sin inventarse. Una frecuencia personalizada se distingue del fabricante.

[Guía de instalación y prueba](USER_TEST.md) · [Privacidad/firma](BUILD_AND_PRIVACY.md) · [Revisión independiente](INDEPENDENT_REVIEW_V4.md).
