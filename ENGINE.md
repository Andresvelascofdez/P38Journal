# Ampliación vigente5.0 / build6

REQUEST_V6.md y docs/CONTRACT_V6.md prevalecen sobre las exclusiones de consumo y esquema anteriores. El consumo sencillo está autorizado: llenos completos, litros posteriores y media ponderada, sin gráficos/informes. Trabajos previstos/realizados reutilizan Visit; documentación libre, renovación manual y ruedas2ejes separados del fabricante. Room4/Snapshot4 compatibles con2/3 y todos los nuevos datos/relaciones en copias. No inventar montajes a175.000, lecturas, trabajos ni datos históricos. Mantener intactas las funciones de mantenimiento de4.1 y filtros de gastos. Fuente base local6957c1a, respaldo previo en .tools/pre-v6. La evidencia final de esta entrega estará en docs/TEST_REPORT.md; las verificaciones4.1 archivadas son antecedentes.

## Antecedentes históricos, aplicables solo donde no contradigan REQUEST_V6

# Corrección vigente4.1 / build5

REQUEST_V5.md sustituye el selector4.0: Mantenimiento muestra directamente las revisiones proyectadas completas. Recorrido: Repository.tasks → Engine.evaluate (vencimientos reales) → Forecast.project (apariciones independientes) → projectedMaintenanceGroups (ID por grupo/fecha/km) → ProjectedPlanPage → ProjectedRevisionDetail. Contador y «Qué incluye» consumen occurrences del mismo grupo, sin resolver el detalle desde una Task representativa.

Desde175.000 sin historial:195.00029,215.00033,235.00031; cifras de pruebas derivadas del catálogo, no constantes productivas. firstKm/firstMonths solo fijan la primera aparición; siguientes usan km/months recurrentes. Proyección condicionada a cumplir revisiones anteriores; no genera trabajos ni avisos. Home, Vencimientos reales, TaskDetail, NotificationEvaluator y WorkManager siguen utilizando únicamente Task reales. Registrar desde una revisión ofrece solo sus apariciones sequence0 que coinciden con el próximo vencimiento real; las siguientes son consulta.

La lista muestra los primeros cuatro grupos globales completos; operaciones propias aisladas con próximo vencimiento real permanecen visibles también fuera de ese horizonte. No se añaden grupos colectivos lejanos con una proyección truncada. Las operaciones reales completas siguen accesibles en Vencimientos reales. Trabajo parcial195.200 produce215.200 exacto para aceite; las restantes permanecen195.000. Fechas se recalculan por operación desde el trabajo real. Abrir una previsión no escribe Visit/Event/Reading.

Sin cambio de Room3, catálogo, applicationId ni firma. Conservar edición, filtros de gastos y copias. Pruebas de pantalla nativa y detalle completo, parcial, actualizaciones reales de APK y evidencias nuevas en docs/TEST_REPORT.md; resultados4.0 archivados como antecedentes.

## Antecedentes conservados donde sean compatibles

# Ampliación v4 vigente

REQUEST_V4.md prevalece sobre el antecedente v3 siguiente. `Engine.evaluate` sigue devolviendo exclusivamente el próximo vencimiento real por operación; sus tareas son las únicas que alimentan estados, avisos y registros. `Forecast.project` devuelve un tipo distinto y solo de consulta: el vencimiento actual y las apariciones siguientes suponiendo que se ejecuta cada anterior exactamente en sus límites de fecha/km. No crea Event, Visit, Reading ni recordatorios. Fecha y km permanecen separados, y la previsión no afirma cuál ocurrirá antes.

La previsión suma a cada próximo vencimiento el intervalo de repetición del catálogo, no el primer desfase. Inicialmente las comunes vuelven a 215.000 junto con C; B vuelve a 235.000. Las adicionales coincidentes se agregan según su regla, sin copiar todas las primeras. Tras trabajo a 195.200, cada operación mantiene su ciclo real: común 215.200, B 235.200. Una revisión parcial deja las demás pendientes.

Las operaciones propias no tienen fuente inventada. Una tarea única usa fecha/km absolutos y no se repite tras ejecución válida. Personalizar una fase a periodicidad simple limpia firstKm/firstMonths, conserva el original del fabricante y el ID/historial; restaurar vuelve al original. Desactivar recordatorios no borra trabajos ni cambia límites. Cambiar solo notas/título conserva generación si los límites efectivos no cambian.

Los filtros de gastos son consulta de registros activos por fecha local ISO, mes/año/tipo; no constituyen informes. Subtotal y total acumulado suman Long céntimos exactos. Anular un vínculo libera visitId y conserva auditoría; editar la visita no sustituye fecha/concepto/tipo personalizados del gasto.

## Antecedente v3 compatible

# Motor desde referencia operativa

## Separar base, lectura y trabajo

`baseline_km=175000` es la referencia de planificación elegida por el propietario. `current_km` es última lectura manual válida o valor inicial identificado. `baseline_date` es fecha operativa editable. No crear MaintenanceEvent ni visita «realizada» por establecer una base. Históricos precompra no cambian esa base silenciosamente. Mantener seed idempotente y auditoría interna, sin códigos expuestos en UI.

## Reglas recurrentes de componente

Para cada dimensión, seleccionar referencia válida de la operación que realmente satisface la regla, después del inicio operativo; si no hay referencia posterior, usar base operativa. Con precisión parcial no inventar la dimensión ausente: conservar su referencia previa/base explícita y distinguirla internamente de una ejecución.

`due_km = anchor_km + interval_km`
`due_date = add_calendar_interval(anchor_date, interval_calendar)`
`due = (current_km >= due_km) OR (local_today >= due_date)` para dimensiones disponibles.

No elegir el mínimo numérico de fecha/km. Mostrar ambas causas sin duplicar. Los meses son naturales con fin de mes documentado y reloj/zona horaria inyectables. No redondear al múltiplo absoluto más cercano del odómetro.

Ejemplo de prueba, no dato del coche: intervalo 20.000 km desde base 175.000 → próximo 195.000. Lectura 184.500 → faltan 10.500. Trabajo real de esa operación a 195.200 → siguiente 215.200. Registrar solamente comprobar nivel no reinicia el cambio. Un evento anterior a la base queda en historial, salvo cambio de referencia explícito del propietario. Correcciones/anulaciones recomputan desde eventos activos y base; trabajo antiguo añadido después no desplaza al último válido.

## Decisión de integración v3: inicio operativo y precisión

«Después del inicio operativo» distingue el historial del propietario del historial precompra; no exige que un trabajo existente se haya realizado después de activar v3. Un evento real del propietario con kilometraje exacto igual o superior a `baseline_km` es elegible aunque su fecha sea anterior a `baseline_date`. Así se conservan las referencias de trabajos reales registrados antes de esta actualización. La procedencia identificada como precompra o recuperada queda excluida de las anclas automáticas. Si el evento no tiene kilometraje exacto, necesita una fecha exacta igual o posterior a `baseline_date` para ser elegible.

Entre los eventos elegibles que satisfacen la operación se selecciona por separado la última fecha exacta y el último kilometraje exacto. La dimensión imprecisa de un trabajo posterior conserva la referencia exacta anterior o, si falta, la base operativa; no se presenta ese respaldo como una ejecución. Esta política puede conservar una fecha de vencimiento anterior a la activación v3 cuando procede de un trabajo real válido: cambiar de versión no reinicia sus intervalos.

La generación de aviso depende de la regla, el plan, los límites y los valores de las referencias efectivas. Los ids nuevos de eventos equivalentes, editar notas/taller o añadir un histórico que no cambia esas referencias no abren otro ciclo ni cancelan su ledger emitido.

## Hitos, fases y condiciones

El algoritmo anterior no convierte todas las filas en recurrencia simple. El catálogo distingue recurrencia, ciclo de servicio, primera intervención única, antigüedad y condición. Hitos únicos ya superados antes de 175.000 no crean una tarea inicial retroactiva. No repetir primera intervención ni traducir «por condición» a cambio periódico. Intervalos B/C y sus operaciones se revisan desde la matriz: la fase operativa de un calendario nuevo es planificación desde la base elegida, no reconstrucción del historial. Conservar semántica/periodicidad y documentar fase inicial adoptada sin exigir investigación al propietario.

Las operaciones de una misma revisión se agrupan en interfaz/avisos, pero conservan ids y registro individual. Comprobaciones rutinarias de uso se sitúan solo en manuales y no generan tareas ni avisos por defecto. Una comprobación prescrita dentro de servicio mantiene su periodicidad dentro del grupo. Ausencia de historial nunca dispara generador de mantenimiento inicial. Fuente/regla condicional no confirmada no produce falsos vencimientos.

## Avisos

Al guardar lectura actual, commit Room y reevaluación inmediata con el mismo motor; emitir NotificationManager si se cruza un límite y hay permiso. No aplazar esa evaluación a WorkManager. Mantener reconciliación pendiente si se interrumpe el proceso entre guardar y emitir. Una lectura que salta varios límites conserva todas las operaciones y puede emitir un resumen.

Preaviso 1.000 km/30 días cuando proporcionado, configurable; alerta al vencer; recordatorio moderado de pendientes. Deduplicar por regla/grupo, generación y ventana/tipo en ledger persistente. App abierta, reintento, doble toque, edición de lectura igual y varias causas no repiten alerta de vencimiento. Nuevo ciclo sí puede avisar. Completar trabajo cancela avisos visibles/pendientes del ciclo anterior. Al anular un trabajo, reaparece pendiente si corresponde y se notifica según estado/ventana, sin ráfaga.

WorkManager único evalúa vencimientos por fecha con interfaz cerrada, sin restricción de red, recuperando reinicios. Android controla ejecución diferible. No garantizar minuto exacto ni funcionamiento mientras el usuario fuerza detención desde Ajustes. No implementar servicio permanente ni alarmas exactas para mantenimiento. Permiso/channels reales, estado claro y botón de prueba sin modificar mantenimiento. Notificaciones locales son los avisos que el propietario llama «push»; no necesitan FCM/servidor.

Ledger/bandeja en Room y ids estables: emisión del SO y DB no son atómicas; recuperar con actualización del mismo id, sin afirmar recepción exactamente una vez. Bandeja aunque permiso denegado. Tocar aviso abre tarea/grupo correcto. Al conceder permiso mostrar resumen actual, no historial acumulado. Ninguna estimación cuenta como odómetro: los límites por km solo se detectan cuando se registra lectura/trabajo actual.

## Persistencia y gastos

Room transaccional, archivos privados, migraciones no destructivas y catálogo versionado con procedencia. Gastos en céntimos EUR, vínculos opcionales únicos al pago de visita; total suma pagos reales activos una vez. Crear gasto de gasolina no modifica odómetro, trabajos ni referencias. Idempotencia, edición/anulación y respaldo incluyen gastos/adjuntos. Precio de compra y costes desconocidos no se inventan. Copias completas validables con schema/checksum y restauración coherente; permisos SAF mínimos. Regenerar planificación después de restaurar sin duplicar.

## Frontera V6 comprobada

Un trabajo explícitamente vinculado con fecha desconocida y km exactos mayores sustituye sólo el ancla de kilómetros. La fecha usa el último trabajo fechado preciso; los eventos con fecha mantienen su selección anterior.178.000 fechado +180.000 sin fecha /10.000→190.000; anular recupera188.000. No se inventa fecha ni se cambia catálogo o fase.
