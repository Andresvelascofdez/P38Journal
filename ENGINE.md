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
