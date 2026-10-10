# Catálogo de mantenimiento P38 — revisión documental

Revisión 2. Verificado el 10/10/2026 mediante descarga real y lectura visual de la matriz y glosario M01, páginas relevantes M02 y suplemento M04. 60 reglas incluidas en APK, cobertura explícita de las 59 filas M01. Este documento describe extracción y decisiones; la revisión independiente se registra por separado cuando se ejecute. No constituye diagnóstico del coche.

## Evidencia

| Fuente | Edición comprobada | PDF | SHA-256 |
|---|---|---:|---|
| [M01](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Maintenance_Sheet_2000_ES.pdf) | LRL0354SPA, 04/00 | 2 páginas | `5a2107fc44b4553735cacc5c465aabab232c435448599c33d12e9d78c90927f4` |
| [M02](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_2000_UK.pdf) | LRL0314ENG, 3.ª edición,©2000 | 211 páginas | `dbd4f4513a1e7dab7f998e46fd6ffb40ce12047643950ab15719fdf3403263b5` |
| [M03](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_1999_ES.pdf) | VDH100730, manual 1999, apoyo de idioma | 207 páginas | `1c9ff1e1c0d2294e30003e8f2bd96bcb4096c8d9e73aeb3e8f271c01eb7aa5ec` |
| [M04](https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/RR_P38_Owners_Handbook_Supplement_2000_ES.pdf) | LRL0314-2SPA,©2001 (aunque URL contenga 2000) | 29 páginas | `a26a41a827ba6ba1518bf2a9d17cde4629b9168d88d792225dce8094dab48af4` |

PDFs, extracciones y renders están únicamente en `private-reference/`, fuera de git/APK/distribución. Los enlaces, metadatos y reglas para cálculo local sí forman parte de la entrega. M02 impresa 135/136/146 corresponde a PDF 138/139/149; SRS impresa 44/51 a PDF 47/54. No se confunden ambos tipos de paginación.

## Decisiones de cálculo

- Servicio principal cada 12 meses/20.000 km: B en 12/20, 36/60, 60/100, 84/140; C en 24/40, 48/80, 72/120, 96/160. A inicial 6/10 se conserva como hecho documental anterior; posteriores A son exclusivamente diésel. No crear A periódico para gasolina ni tomar compra como ejecución.
- Columnas B+C sin marcas especiales producen reglas de 12 meses/20.000 km. C-only usa `phase`; B-only usa `phase_b`, conservando sus offsets originales. Los textos de condición explican que una referencia operativa adoptada expresamente es distinta de demostrar la fase de fábrica.
- Las marcas especiales 60k/100k/120k/160k se representan como ciclos `component`, siguiendo la vigencia recurrente del glosario (ejemplo cada 60 meses/100.000 km); filtro automático primer 40k y ejes primer 10k no pasan a ser intervalos repetidos. Ejes preservan el posterior 160k. El líquido de frenos y eje de dirección usan intervalo recurrente expresamente escrito en M02, evitando inferirlo solo por columnas.
- Seed e instalación no completan operaciones. Fechas/km desconocidos o aproximados no permiten vencimiento preciso. ATF, transferencia y diferenciales conocidos cambiados llevan token `known_pre_purchase_replaced`, conservan la regla de cambio y reciben inspecciones iniciales separadas. Comprobar niveles no satisface el cambio.
- Balonas y neumáticos: inspección, sin intervalo de sustitución inventado ni cambio por historial parcial.
- Prioridad y agrupación de avisos son decisiones de producto, no características atribuidas al fabricante. No se introducen importes, VIN, fecha de matriculación, kilómetros actuales ni reparaciones ficticios.

## Aplicabilidad y divergencias

- Filtro de gasolina M01 fila16 solo hasta WA410481: verificación profesional concreta sin asumir VIN por año. Marca 80k preservada en condición; sin activación de reemplazo hasta confirmarlo.
- Techo solar, remolque y airbags laterales son condicionales al equipamiento. Corrosión anual incluye la condición de venta posterior 21/11/1998 sin afirmar garantía.
- Pretensores: M01 PDF 2 señala10 años y M02 imp 44/PDF 47 señala15 años. La tarea solicita resolver especificación con especialista; no se eligió un intervalo numérico silenciosamente.
- M02 imp 51/PDF 54 confirma airbags frontales10 años y laterales15 años, con fecha base matriculación o montaje del módulo. Fecha base desconocida genera servicio inicial profesional y no una fecha de atraso fabricada. Inspección visual no prueba cumplimiento ni seguridad.
- M04 es un suplemento©2001, no un manual 2000 completo. Índice y texto revisados: mensajes, retención infantil, elevalunas, arranque/recuperación, batería, enganche y declaraciones RF. No reemplaza intervalos de M01/M02. Su espera mínima de 2 min antes de desconectar batería con navegación debe contrastarse con la alarma/equipamiento; la app no da una secuencia DIY universal.
- M02 exige OAT en su edición. La revisión de refrigerante conserva la fuente/condición y deriva formulación y procedimiento al taller; no aplica especificación1999 por traducción.
- Uso severo: las notas no justifican acortar genéricamente todos los intervalos.1.500 km para frenos/sensores solo tras confirmar uso extenso severo; presión diaria solo durante uso fuera de carretera. Las tareas actuales establecen un plan concreto con taller; no existe detección automática del uso.

## Inventario M01

Toda fila de esta tabla procede de PDF 1 y, cuando aplica, glosario/notas PDF 2. `included`: aplicable o incorporada a una operación agrupada; `excluded`: nota administrativa de aplicabilidad o versión ajena; `conditional`: depende de VIN/equipo/hito/síntoma. Los IDs enlazan con el catálogo, evitando duplicar las instrucciones completas del fabricante.

| Fila | Decisión | Columnas | Regla/agrupación | Motivo o límite |
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
| 13 | included | A/B/C | `tyre-check` | Comprobación más frecuente M02; neumáticos conocidos nuevos no se sustituyen por seed. |
| 14 | included | B/C | `brake-mechanisms` |  |
| 15 | included | A/B/C | `abs-sensor-wiring` |  |
| 16 | conditional | C:80k | `fuel-filter-applicability` | Aplicabilidad no asumida; intervalo original preservado como condición, sin activar reemplazo falso. |
| 17 | excluded | A/B/C |  | Motor diésel; vehículo gasolina. |
| 18 | excluded | A/B/C |  | Motor diésel; vehículo gasolina. |
| 19 | included | C | `brake-mechanisms` | Montaje seguro en posición original y fijación de ruedas queda en el procedimiento del taller. |
| 20 | included | B/C | `radiator-fan` |  |
| 21 | included | B/C | `locks-hinges` |  |
| 22 | conditional | B/C | `sunroof-drains` | Equipamiento/persistencia de incidencia debe comprobarse; no diagnóstico inventado. |
| 23 | included | B:60k/C:120k | `coolant-renewal` | Intervalo especial de la hoja, vigencia recurrente del glosario; no precisión cronológica desde año modelo. |
| 24 | included | B:60k/C:120k | `brake-fluid` | Intervalo recurrente explícito contrastado M02 imp 146/PDF 149. |
| 25 | included | A/B/C | `cooling-hoses` |  |
| 26 | included | C:160k | `evap-service` |  |
| 27 | included | C:120k | `spark-plugs` |  |
| 28 | included | C | `air-filter` |  |
| 29 | excluded | B/C |  | Filtro de aire específico diésel. |
| 30 | included | B/C | `pollen-filters` |  |
| 31 | included | B/C | `auxiliary-belt-inspection` |  |
| 32 | included | C:100k | `auxiliary-belt-renewal` | Marca especial100k prevalece sobre alternancia nominal; no convertir columnaC en 40k. |
| 33 | included | C | `steering-shaft` | Intervalo explícito M02, además de fase hoja. |
| 34 | included | A/B/C | `brake-level`, `steering-level`, `washer-level` | Niveles cubiertos por controles propietario más frecuentes; excluir embrague automático. Comprobar no equivale a cambiar fluidos. |
| 35 | included | A/B/C | `battery-condition` |  |
| 36 | excluded | C:80k |  | Limpieza intercooler exclusiva diésel. |
| 37 | included | A/B/C | `engine-oil-filter` |  |
| 38 | excluded | C |  | Caja manual excluida. |
| 39 | excluded | B |  | Caja manual excluida. |
| 40 | conditional | C:primer 40k | `automatic-filter-history` | Hito único ya anterior a compra; verificación concreta, sin reemplazo periódico inventado. |
| 41 | included | C | `automatic-fluid` | Historial conocido conservado, cambio no forzado en revisión inicial. |
| 42 | included | C | `transfer-fluid` | Cambio previo sin referencia exacta; inspección inicial separada. |
| 43 | included | B | `transfer-level` | B no C; offset de 20k documentado. No reinicia cambio de aceite. |
| 44 | included | A:primer 10k/C:160k | `axle-fluid` | Primer hito no recurrente; respetar cambio previo sin fecha/km. |
| 45 | included | B | `axle-level` | Nivel no satisface sustitución; niveles/fugas concretos para mantenimiento inicial. |
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

## Verificación realizada y TBC

- Descarga HTTP exitosa de M01–M04; hashes y número real de páginas registrados arriba.
- Renders reales inspeccionados: M01 PDF 1–2; M02 PDF 138–139, 149, 47, 54; M04 PDF 1, 3, 19. Texto de todo M04 y sección de mantenimiento M02 PDF 136–161 leído para contrastar alcance. M03 no se usa para reemplazar especificaciones de M02.
- Validación ejecutada: 59 filas únicas 1–59; 60 IDs de regla únicos; todas las referencias de cobertura resuelven; JSON UTF-8 válido.
- Revisión independiente completada: véase [INDEPENDENT_REVIEW.md](INDEPENDENT_REVIEW.md). Fases operativas explícitas y perfiles condicionados separados comprobados en pruebas de motor/Room/UI; resultados reales en [TEST_REPORT.md](TEST_REPORT.md). La verificación documental no demuestra por sí sola recepción de avisos.
- TBC del vehículo: especificación exacta por VIN/equipamiento, fecha de matriculación y conflicto documentado de pretensores. Se conservan como condiciones y acciones profesionales sin inventar evidencia.

## Corrección tras revisión independiente

El glosario M01 PDF 2 define vigencia recurrente para las marcas especiales. Refrigerante (36 meses/60.000 km), evaporación (96 meses/160.000 km), bujías (72 meses/120.000 km), correa (60 meses/100.000 km) y ejes (96 meses/160.000 km) usan intervalos desde la última operación válida. No se calcula el próximo múltiplo absoluto del odómetro. Los primeros servicios excepcionales 10.000/40.000 km siguen documentados aparte. Los intervalos B/C y offsets de origen permanecen separados y no se infieren de la compra.

La revisión 2 incrementa las cinco reglas corregidas para que las instalaciones anteriores reciban la corrección idempotente; los overrides del propietario y las revisiones de eventos realizados se conservan.
