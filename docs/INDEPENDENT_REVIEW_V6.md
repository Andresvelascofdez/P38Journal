# Revisión independiente — P38 Journal 5.0.0 / build 6

Estado: revisión de integración y entrega cerrada; sin defectos productivos pendientes identificados en este alcance. Base: 6957c1a; producción definitiva b68a9d171b20551d1767612e92a60a79fffb5d44. Entrega local, sin publicación. REQUEST_V6 autoriza consumo sencillo; resultados V5 son antecedentes, no pruebas del APK nuevo.

## Alcance y hallazgos

Leí AGENTS, REQUEST_V6, CONTRACT_V6 y documentación de motor, migración, QA, Android y fuentes. Inspección propia de entidades, repositorio, Snapshot/Files/DB-WAL/Room 3→4, Consumption/Tyres, avisos y navegación/formularios. Escribí este informe y ocho pruebas unitarias más ocho Android independientes. Las ejecutó el coordinador; inspeccioné XML y evidencias sin Gradle/adb ni cambios productivos.

- Previsto→realizado conserva VisitID. Fecha desconocida queda vacía/imprecisa y km desconocido null; no se rellena 175.000. Estimado no crea gasto. Una compra REAL previa puede vincularse al previsto y conservar ExpenseID al realizarlo. Trabajo libre no crea Rule/Event/Reading; vincular mantenimiento exige selección expresa y dimensión precisa.
- Fuel/Expense conservan pago único, fecha/céntimos canónicos y anulación bidireccional. Gastos antiguos no inventan litros/km. Consumo excluye litros del primer lleno, incluye parciales/cierre, pondera distancia y separa abiertos/incompletos; no actualiza odómetro implícitamente.
- Documentación libre conserva historial de renovación manual, sin gasto ni nueva vigencia automáticos. Avisos usan entrada/eje y generación propios; emitted/read se conservan al desactivar y reactivar globalmente.
- Ruedas separan uso desde montaje y aviso desde revisión. Corregir juego antiguo no sustituye posterior; ambos ejes comparten visita/pago. Historial extranjero sobre seguimiento local conserva active=true/tracking=false; restauración limpia recupera seguimiento fuente. Sin montaje ni lectura ficticios.
- Snapshot 4 admite 2/3/4 y valida relaciones. Recibo local conflictivo prevalece/se audita; fuel incoherente queda inactivo. Historial tracking=false conserva FK sin exigir coincidencia operativa con visita local editada. Doble restauración no duplica ni resucita anulaciones.

Hallazgos comunicados y correcciones reinspeccionadas: pérdida de emitted/read al desactivar avisos; conflicto copia Fuel/recibo local; secuencia extranjera mayor sustituyendo juego local; fecha obligatoria para mantenimiento manual con km conocidos; borrador padre perdido al volver de gasto; anulación de historial importado afectando visita local; JSON estructurado visible como notas manuales tras desvincular mantenimiento. Esta última corrección muestra solo notes y conserva schema/budget/lines; prueba Compose recorre vínculo/desvínculo sin fecha, presupuesto 50,00 y edición de notas sin gasto/lectura/evento activo.

Una suite sobre fixture poblado de migración expuso un defecto real: evento fechado 178.000 seguido de trabajo manual date=null/km=180.000 dejaba aceite personalizado a 10.000 km en 188.000 km. Se comunicó antes de limpiar el fixture. Fix mínimo: evento activo/válido con fecha null y km exactos superiores puede avanzar ancla km a 180.000→190.000; conserva calendario conocido. Igual/menor, impreciso, inactivo, prebase o recuperado no desplaza; eventos fechados, incluidas fechas aproximadas, mantienen criterio previo. Cuatro pruebas de motor, una Room y mi octava unitaria cubren caso/exclusiones.

Forecast, MaintenanceGroups, ProjectedPlanPage y catálogo preservados; Engine cambia únicamente ese caso de fecha null. Lista principal conserva 29/33/31 y avisos siguen tareas reales, sin emitir desde proyecciones.

## Verificaciones definitivas

Recuento propio de XML archivados en docs/evidence/v6: **74 unitarias aprobadas; Android: 108 casos, 103 aprobados, 0 fallos/errores, 5 omisiones**. Cuatro omisiones son ciclos por etapas; la quinta requiere permiso de notificaciones y se ejecutó por separado con permiso real, **OK (1 test)**, en real-deadline-delivery.txt. No se suma ese caso a 103 ni se usa contador inflado del runner. Lint: 0 errores/fatales, 33 advertencias y 2 sugerencias; build definitivo correcto en delivery-validation.log.

Mis ocho unitarias y ocho Android aprobaron: 165 L/1750 km = 9,43 ponderado, abiertos/incompletos/recuperación, orden/decimales; secuencia/uso/generación de ruedas; ancla manual sin fecha; identidad del trabajo/compra real; edición/anulación/restauración de fuel; recibo local conflictivo y copia posterior válida; dos ejes/pago único/corrección antigua; secuencia extranjera 99 frente a local 1/2; off/read/on sin duplicación; ausencia de Reading sin aviso y Reading 171.000 con aviso único. Transporte simulado no se presenta como notificación visible Android.

v6-lifecycle-verification.json acredita tres etapas PASS con APK 4.1 real→5.0, Room 3→4 sin desinstalar y ZIP restaurado dos veces. Conserva base 175.000/01-10-2026, Reading 182.000, aceite personalizado, visita/pago 300 € con metadatos, PDF/hash/marcador, preferencias 777 y aviso leído sin reemisión con permiso real. Fuel/documentación/ruedas inicialmente vacíos. Nuevos datos: trabajo libre desconocido, consumo 14, renovación manual y dos ejes/pago único; total 540 €, Reading anterior preservada, initialize/export válidos e idempotentes. Nombre seed-v4 de prueba antigua heredado; script instala APK real 4.1/code5, no reconstruye versión ficticia.

Intentos fallidos archivados: primera suite: 107 casos/96 PASS/7 FAIL/4 SKIP por seis helpers que exigían Scroll en diálogos/popup y ruta antigua sin desplazamiento; segunda: 107 casos/101 PASS/1 FAIL/5 SKIP contaba búsqueda editable y tarjeta como dos filas aunque VisitID era único. Se corrigieron recorridos/aserciones. Suite posterior sobre fixture poblado tenía expectativas iniciales incompatibles con aceite personalizado y descubrió defecto de motor anterior; se corrigió producción y después se aisló solo debug descartable, preservando release/ciclo. Ningún intento fallido se declara aprobado.

## APK y pantallas

Comprobación propia con SHA256, apksigner, aapt y ZIP: release dev.p38journal y debug dev.p38journal.debug, versión 5.0.0/code6, min26/target36, firmas válidas, catálogo idéntico al JSON fuente y sin permiso INTERNET. Gitdiff productivo (src/main y configuración) vacío tras commit definitivo; el test por etapas añade comprobaciones de conservación del historial sin cambiar APK de producto.

| APK | Bytes | SHA256 |
| --- | ---: | --- |
| release | 20599208 | eb64a294f5728c3fc9eb89f63fab96f1c3970c68e236a7ac2a985e625bcb24be |
| debug | 27655723 | fae0760fb12462c0477bdb11a39befe66a5c44f4977c94ff4486f0ae7ba999cf |

Certificado release original SHA256 ce157b9b800d3e21c544ca21cbb398ffdfb3fab46ba5318adcc63d8cd14adf2b. v6-release-install-verification.json y capturas propias inspeccionadas acreditan install-r release 5→6 correcto sin desinstalar/borrar datos; mantenimiento nativo muestra 29/33/31 y 5.0.0·6. Migración con datos poblados demostrada separadamente en ciclo anterior. Cotejé los 51 SHA de v6-source-verification.json contra archivos actuales: cero diferencias. v6-installed-verification.json registra ambos APK instalados con SHA idénticos a los entregados.

Inspección visual propia de muestras reales: Inicio/lectura182.000/acciones/consumo 14, repostajes 500 km, realizados con fecha desconocida, documentación/historial renovado, ruedas con uso pendiente si lectura precede montaje, mantenimiento release y destino delantero de aviso. Matriz automatizada a 320/360/390/430 dp y 320 oscuro/fuente130%; inspeccioné directamente 320 normal/oscuro, 430 y formulario130%. Campos y acciones legibles; cabecera larga del formulario usa elipsis normal a 130%. Capturas con conectividad visible; no se presentan como prueba nueva de modo avión.

## Recorrido final y límites

`v6-native-verification.json` y logs finales acreditan destinos individuales reales desde la cortina Android: ruedas delanteras y documentación exacta. Inspeccioné capturas de destino y revisión: montaje 175.000/uso 20.200 se conservan; revisión nueva 195.200 da próximo aviso 205.200 y mantiene historial anterior 185.200. Confirmación documental crea una renovación. `native-runtime-verify.txt` contiene OK (1 test) con esas comprobaciones persistidas.

Cancelación comprobada por identidad, no solo por canal: `v6-runtime-notification-state.json` acredita ausencia de IDs manuales 1355887020 y 484462842. Sobreviven fabricante1001/maintenance y resumen automático Android id0, tag Aggregate_AlertingSection, flags1808/groupSummary=true en canal documental. Ese resumen sintético no es el aviso documental retirado ni autoriza afirmar que la cortina queda vacía.

Intentos nativos fallidos conservados: desplazamiento, filtro persistido, preview no clickable del resumen autogrupado, selección de historia frente acción nueva, renovación fuera de viewport, título de diálogo confundido con TextButton, fallo temporal adb y aserción de canal que contaba el resumen OS. Jerarquías reales identificaron controles/ancestros clickable; comprobaciones de datos e IDs evitan ocultar edición del historial o cancelación defectuosa. Se corrigieron helpers y prueba por etapas, sin cambios de APK/producto. Muestras visuales proceden del mismo APK definitivo; verificación persistida final se ejecutó separadamente tras distinguir el resumen OS.

Verificado en emulador API36, sin teléfono físico. WorkManager puede retrasarse; force-stop exige reabrir y no garantiza entrega mientras detenido. Uso/avisos km dependen de lecturas manuales. V6 no vuelve a certificar escenario nuevo de avión/reinicio real por reutilizar evidencias V5.
