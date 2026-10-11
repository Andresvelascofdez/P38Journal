# Corrección vigente4.1 / build5

REQUEST_V5.md sustituye el selector4.0: Mantenimiento muestra directamente las revisiones proyectadas completas. Recorrido: Repository.tasks → Engine.evaluate (vencimientos reales) → Forecast.project (apariciones independientes) → projectedMaintenanceGroups (ID por grupo/fecha/km) → ProjectedPlanPage → ProjectedRevisionDetail. Contador y «Qué incluye» consumen occurrences del mismo grupo, sin resolver el detalle desde una Task representativa.

Desde175.000 sin historial:195.00029,215.00033,235.00031; cifras de pruebas derivadas del catálogo, no constantes productivas. firstKm/firstMonths solo fijan la primera aparición; siguientes usan km/months recurrentes. Proyección condicionada a cumplir revisiones anteriores; no genera trabajos ni avisos. Home, Vencimientos reales, TaskDetail, NotificationEvaluator y WorkManager siguen utilizando únicamente Task reales. Registrar desde una revisión ofrece solo sus apariciones sequence0 que coinciden con el próximo vencimiento real; las siguientes son consulta.

La lista muestra los primeros cuatro grupos globales completos; operaciones propias aisladas con próximo vencimiento real permanecen visibles también fuera de ese horizonte. No se añaden grupos colectivos lejanos con una proyección truncada. Las operaciones reales completas siguen accesibles en Vencimientos reales. Trabajo parcial195.200 produce215.200 exacto para aceite; las restantes permanecen195.000. Fechas se recalculan por operación desde el trabajo real. Abrir una previsión no escribe Visit/Event/Reading.

Sin cambio de Room3, catálogo, applicationId ni firma. Conservar edición, filtros de gastos y copias. Pruebas de pantalla nativa y detalle completo, parcial, actualizaciones reales de APK y evidencias nuevas en docs/TEST_REPORT.md; resultados4.0 archivados como antecedentes.

## Antecedentes conservados donde sean compatibles

# Catálogo P38 — revisión 3

V4 conserva este catálogo y sus frecuencias sin cambios. La revisión adicional de fases B/C y previsión por operación está en [INDEPENDENT_REVIEW_V4.md](INDEPENDENT_REVIEW_V4.md); conjuntos, APK y pruebas ejecutadas en [TEST_REPORT.md](TEST_REPORT.md). La base 175.000 es referencia operativa y no una intervención realizada. Las personalizaciones del propietario no modifican esta fuente canónica.

Revisado documentalmente el 11/10/2026 sobre el catálogo completo v2 y sus PDFs privados. Se conservan las 60 reglas previas y se añaden dos comprobaciones específicas de taller: 62 reglas y cobertura íntegra de las 59 filas M01. La revisión independiente v3 e integración ejecutable se documentarán en los informes de pruebas; este archivo no las da por realizadas.

## Fuentes y evidencia

| Fuente | Edición | Páginas PDF | SHA-256 |
|---|---|---:|---|
| [M01](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Maintenance_Sheet_2000_ES.pdf) | LRL0354SPA, 04/00 | 2 | `5a2107fc44b4553735cacc5c465aabab232c435448599c33d12e9d78c90927f4` |
| [M02](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_2000_UK.pdf) | LRL0314ENG, 3.ª edición, ©2000 | 211 | `dbd4f4513a1e7dab7f998e46fd6ffb40ce12047643950ab15719fdf3403263b5` |
| [M03](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_1999_ES.pdf) | VDH100730, 1999; apoyo de idioma | 207 | `1c9ff1e1c0d2294e30003e8f2bd96bcb4096c8d9e73aeb3e8f271c01eb7aa5ec` |
| [M04](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_Supplement_2000_ES.pdf) | LRL0314-2SPA, ©2001; URL denominada 2000 | 29 | `a26a41a827ba6ba1518bf2a9d17cde4629b9168d88d792225dce8094dab48af4` |

Los PDFs se descargaron realmente en v2. En v3 se verificaron sus bytes contra estos hashes y se releyeron visualmente M01 PDF 1–2 y M02 PDF 138–139, 149, 47 y 54. M02 impresa 135/136/146 corresponde a PDF 138/139/149; impresa 44/51 a PDF 47/54. El suplemento M04 fue contrastado en v2 (portada, índice, batería y texto completo); no sustituye el manual principal. Los PDFs, extracciones y renders quedan en `private-reference/`, fuera de git/APK/distribución.

## Política operativa v3

175.000 km es la referencia del calendario elegida por el propietario. No es lectura actual ni prueba de ejecución; el catálogo no crea eventos, visitas ni fechas de compra/servicio. La fecha del plan es una referencia operativa recuperada o de migración, editable. Se conservan lecturas y trabajos reales.

Los intervalos recurrentes parten de esa base cuando no existe ejecución posterior válida. Después, cada operación usa su propia ejecución exacta; una comprobación no satisface un cambio. Historial aproximado o precompra se conserva sin desplazar silenciosamente la base. Las fases B/C y la antigüedad original tienen semánticas separadas. No se genera mantenimiento inicial por ausencia de historial.

| Campo | Semántica |
|---|---|
| `scheduleVisibility=maintenance` | Mantenimiento periódico real, en calendario y avisos. |
| `scheduleVisibility=service` | Operación real incluida en revisión de taller, agrupada; no se elimina por ser inspección. |
| `scheduleVisibility=manual` | Recomendación rutinaria de uso, accesible en manuales, sin tarea ni alerta por defecto. |
| `scheduleVisibility=conditional` | Aplicabilidad/fecha original/uso no confirmado: fuente conservada, sin falso vencimiento ni alerta por defecto. |
| `scheduleVisibility=inactive` | ID derivado v2 conservado para historial/migración, fuera del plan y avisos. |
| `serviceGroup=workshop` | Agrupa operaciones aplicables solo cuando coinciden sus límites; mantiene registro individual. |
| `firstKm` / `firstMonths` | Offset inicial de fase respecto al plan; en una primera intervención única, kilometraje original absoluto. |

Hay 12 reglas de mantenimiento, 30 operaciones de revisión, 7 referencias rutinarias, 11 recomendaciones condicionadas y 2 IDs inactivos. Todos los mantenimientos/revisiones aplicables usan el mismo grupo `workshop`; la interfaz separa grupos con límites distintos y permite completar solamente las operaciones realizadas.

## Fases e intervalos comprobados

- M01 prescribe servicio principal cada 12 meses/20.000 km, primero que ocurra. Fase B: 12/20, 36/60, 60/100, 84/140. Fase C: 24/40, 48/80, 72/120, 96/160. El calendario operativo nuevo adopta B primero, C segundo desde la base, sin reconstruir historial.
- B-only: `recurrence=phase_b`, primer offset 12 meses/20.000 km, ciclo 24 meses/40.000 km. C-only: `recurrence=phase`, primer offset 24 meses/40.000 km, mismo ciclo. `firstKm` y `firstMonths` hacen explícito el primer offset.
- B+C sin marca especial: 12 meses/20.000 km. El aceite/filtro de motor y las inspecciones coincidentes se agrupan por límite, sin perder sus IDs ni imponer completar todas al registrar una visita.
- Las marcas especiales tienen vigencia recurrente según glosario M01: refrigerante 36 meses/60.000 km, evaporación 96/160.000, bujías 72/120.000, correa 60/100.000 y ejes 96/160.000. Se conserva la corrección independiente v2 que eliminó redondeo al siguiente múltiplo absoluto; estas reglas usan `component`.
- M02 confirma explícitamente líquido de frenos 36 meses/60.000 km, hidráulica de frenos 72/120.000 y eje intermedio de dirección 24/40.000. No se sustituye la inspección de dirección por lubricación.
- A inicial 6 meses/10.000 km y primeros servicios excepcionales de ejes 10.000 km/filtro automático 40.000 km son anteriores a la base; no crean alertas retroactivas. A posterior es diésel, excluido. El filtro no se convierte en un cambio cada 40.000 km.
- `automatic-filter-history` conserva el ID de revisión v2, inactivo, con `recurrence=once` y `firstKm=40000` como evidencia del hito original. `automatic-initial-inspection` conserva el ID derivado v2 inactivo. No se borran los eventos ya registrados para esos IDs.

## Operaciones independientes y condiciones

Las filas 13 (neumáticos) y 34 (niveles) ya estaban representadas en v2 mediante controles rutinarios M02. Al reubicarlos en manuales, se añaden `tyre-service-inspection` y `fluid-reservoirs-service` para conservar también las inspecciones prescritas dentro de cada servicio principal. No reinician cambios de neumáticos o líquidos.

Neumáticos y balonas conservan inspecciones, sin intervalos de sustitución por desgaste/antigüedad inventados. ATF, transferencia y diferenciales mantienen las reglas periódicas del fabricante; el historial recuperado no se convierte en un cambio realizado a 175.000 km ni exige inspección inicial.

| Condición | Tratamiento por defecto |
|---|---|
| Filtro de gasolina hasta VIN WA410481 | Fuente conserva marca 80.000 km/48 meses; sin asumir VIN por año ni activar sustitución. |
| Corrosión, venta posterior al 21/11/1998 | Pauta anual preservada; fecha de venta/garantía no certificada, sin alerta falsa de aplicabilidad. |
| Techo solar / enganche de remolque | Equipamiento no confirmado: recomendaciones conservadas, sin tareas urgentes. |
| Luz de avería / síntoma | Diagnóstico condicionado a observación real; notas históricas SRS/EAS no prueban avería actual. |
| Polvo, vadeo, campo, remolque, extremos térmicos | Mayor frecuencia documentada sin acortar genéricamente intervalos ni inferir uso. |
| Uso extenso severo: frenos y sensores | Marca de 1.500 km conservada en condición; no activa avisos sin uso confirmado. |
| Presión diaria en uso fuera de carretera | Referencia condicional, independiente del control semanal ordinario. |

Airbags frontales y laterales conservan 120/180 meses y `recurrence=age`, con visibilidad condicionada. M02 usa primera matriculación o instalación de módulo sustituto. La fecha del plan/migración no equivale a esa fecha original y no se usa como ancla; si falta, no se inventa vencimiento. Preservar ese requisito evita presentar como nuevo un componente de edad desconocida. La inspección visual tampoco certifica seguridad ni cumple una renovación.

Pretensores: M01 PDF 2 señala 10 años; M02 impresa 44/PDF 47 señala 15 años. Se mantiene la divergencia, sin elegir intervalo automático ni desencadenar investigación urgente. Queda por confirmar especificación aplicable con profesional, dentro de la referencia secundaria.

M04 es una publicación ©2001 aunque su URL diga 2000; su espera mínima de dos minutos antes de desconectar batería con navegación y el procedimiento de alarma dependen del equipamiento. No cambian intervalos ni se ofrece una secuencia DIY universal. M02 exige OAT en la edición revisada; M03 no se utiliza para sustituir esa especificación por traducción.

## Cobertura de las 59 filas M01

Cada fila procede de PDF 1, con glosario/notas PDF 2. Incluida significa operación aplicable o procedimiento incorporado al servicio; condicionada conserva el hecho sin alertas por defecto; excluida significa nota de aplicabilidad o variante ajena. Los pasos de desmontaje/montaje/despresurización no se convierten en tareas periódicas aisladas.

| Fila | Decisión | Columnas originales | Regla | Límite o agrupación |
|---:|---|---|---|---|
| 1 | excluded | A |  | Nota de aplicabilidad, no trabajo. A inicial 6m/10k; A posterior exclusivo diésel. |
| 2 | included | B/C | `seats-belts` |  |
| 3 | included | B/C | `lights-horn` |  |
| 4 | included | B/C | `wipers-washers` |  |
| 5 | included | B/C | `handbrake` |  |
| 6 | included | C | `remote-batteries` |  |
| 7 | conditional | A/B/C | `corrosion-inspection` | Fecha de venta/garantía desconocida; inspección anual, no requisito de garantía confirmado. |
| 8 | included | C | `eas-compressor-service` | Agrupada: incluye secuencia profesional completa; secador condicionado al agua, no cambio indiscriminado. |
| 9 | included | C | `eas-compressor-service` | Agrupada: incluye secuencia profesional completa; secador condicionado al agua, no cambio indiscriminado. |
| 10 | included | C | `eas-compressor-service` | Agrupada: incluye secuencia profesional completa; secador condicionado al agua, no cambio indiscriminado. |
| 11 | included | B/C | `headlight-alignment` |  |
| 12 | included | B/C | `brake-mechanisms` | Desmontaje de ruedas es procedimiento para inspección; no tarea recurrente independiente. |
| 13 | included | A/B/C | `tyre-service-inspection` | Inspección real de revisión conservada; chequeo rutinario semanal en manual sin avisos. |
| 14 | included | B/C | `brake-mechanisms` |  |
| 15 | included | A/B/C | `abs-sensor-wiring` |  |
| 16 | conditional | C:80k | `fuel-filter-applicability` | Aplicabilidad no asumida; intervalo original preservado como condición, sin activar reemplazo falso. |
| 17 | excluded | A/B/C |  | Motor diésel; vehículo gasolina. |
| 18 | excluded | A/B/C |  | Motor diésel; vehículo gasolina. |
| 19 | included | C | `brake-mechanisms` | Montaje seguro en posición original y fijación de ruedas queda en el procedimiento del taller. |
| 20 | included | B/C | `radiator-fan` |  |
| 21 | included | B/C | `locks-hinges` |  |
| 22 | conditional | B/C | `sunroof-drains` | Equipamiento/persistencia de incidencia debe comprobarse; no diagnóstico inventado. |
| 23 | included | B:60k/C:120k | `coolant-renewal` | Hitos de hoja; no precisión cronológica desde año modelo. |
| 24 | included | B:60k/C:120k | `brake-fluid` | Intervalo recurrente explícito contrastado M02 imp 146/PDF149. |
| 25 | included | A/B/C | `cooling-hoses` |  |
| 26 | included | C:160k | `evap-service` |  |
| 27 | included | C:120k | `spark-plugs` |  |
| 28 | included | C | `air-filter` |  |
| 29 | excluded | B/C |  | Filtro de aire específico diésel. |
| 30 | included | B/C | `pollen-filters` |  |
| 31 | included | B/C | `auxiliary-belt-inspection` |  |
| 32 | included | C:100k | `auxiliary-belt-renewal` | Marca especial100k prevalece sobre alternancia nominal; no convertir columnaC en 40k. |
| 33 | included | C | `steering-shaft` | Intervalo explícito M02, además de fase hoja. |
| 34 | included | A/B/C | `fluid-reservoirs-service` | Comprobación real del servicio, independiente de controles rutinarios y sustituciones. Excluye embrague automático. |
| 35 | included | A/B/C | `battery-condition` |  |
| 36 | excluded | C:80k |  | Limpieza intercooler exclusiva diésel. |
| 37 | included | A/B/C | `engine-oil-filter` |  |
| 38 | excluded | C |  | Caja manual excluida. |
| 39 | excluded | B |  | Caja manual excluida. |
| 40 | conditional | C:primer 40k | `automatic-filter-history` | Primera intervención única 40.000 km anterior a base: no alerta retroactiva ni repetición; ID v2 preservado inactivo. |
| 41 | included | C | `automatic-fluid` | Historial previo conservado; fase C del plan desde base operativa, sin inventar ejecución. |
| 42 | included | C | `transfer-fluid` | Historial previo conservado; fase C del plan desde base operativa, sin inventar ejecución. |
| 43 | included | B | `transfer-level` | B no C; offset de 20k documentado. No reinicia cambio de aceite. |
| 44 | included | A:primer 10k/C:160k | `axle-fluid` | Primer hito 10.000 km anterior a base no se repite. Ciclo posterior 96 meses/160.000 km desde referencia válida/base operativa. |
| 45 | included | B | `axle-level` | Comprobación fase B; no satisface cambio de aceite, sin tarea inicial. |
| 46 | included | B/C | `propshaft-lubrication` |  |
| 47 | included | A/B/C | `underbody-lines` |  |
| 48 | included | A/B/C | `steering-suspension-lines` |  |
| 49 | included | A/B/C | `powertrain-leaks` |  |
| 50 | included | B/C | `exhaust-inspection` |  |
| 51 | included | B/C | `chassis-fasteners` |  |
| 52 | included | B/C | `steering-balljoints` |  |
| 53 | included | B/C | `air-springs-lines` |  |
| 54 | included | B/C | `dampers-height-sensors` |  |
| 55 | conditional | B/C | `towing-mount` |  |
| 56 | conditional | B/C | `warning-diagnostics` | Diagnóstico condicionado a luz/avería observada; sin incidencia activa inventada. |
| 57 | included | A/B/C | `road-test` |  |
| 58 | included | B/C | `service-report` | Acción administrativa de visita; no cancela otras operaciones. |
| 59 | included | B/C | `service-report` | Acción administrativa de visita; no cancela otras operaciones. |

## Verificaciones reales y pendientes

- Hashes reales de los cuatro PDFs privados coinciden con las fuentes preservadas; no se afirmó una descarga nueva v3.
- JSON UTF-8, 62 IDs únicos, 59 filas únicas 1–59 y vínculos de cobertura comprobados. Clasificaciones y offsets B/C comprobados contra matriz/glosario visuales.
- Falta la revisión independiente v3; la anterior revisión v2 se conserva como antecedente en `INDEPENDENT_REVIEW.md`.
- Las pruebas del motor, actualización/migración, agrupación de UI y avisos corresponden al informe de integración: no se afirman ejecutadas solamente por validar este JSON.
- TBC del vehículo: VIN/equipamiento, fecha original SRS y divergencia de pretensores; ninguna de estas ausencias genera mantenimiento inicial ni modifica datos reales.
