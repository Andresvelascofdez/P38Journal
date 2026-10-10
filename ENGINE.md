# Motor de mantenimiento y notificaciones

## Contratos mínimos

Entidades: Vehicle, OdometerReading, SourceDocument, MaintenanceRule, RuleRevision, TaskInstance, ServiceVisit, WorkItem, MaintenanceEvent, Fault, Part, Attachment, Expense, CostAllocation, NotificationLedger, NotificationInbox, AuditEvent y BackupManifest. Codex puede reducir tablas si mantiene las garantías; no copiar este listado sin justificar relaciones.

MaintenanceRule: id estable, nombre, sistema, acción, applicability (motor/transmisión/año/VIN/equipamiento), intervalo por unidad (días/semanas/meses/años y/o km), base de recurrencia, condición, fuente/edición/página impresa/página PDF, regla original preservada en paráfrasis breve, revisión, prioridad y tratamiento inicial. Aceptar reglas recurrentes, por hitos, únicas iniciales y basadas en condición. Campos desconocidos son null y tienen precisión, no valores inventados.

MaintenanceEvent: operation, performed_at, odometer_km, precision, provenance, work_item_id y rule_revision. Una fecha aproximada o ausente no sirve como fecha exacta silenciosamente; un kilometraje aproximado no produce un vencimiento presentado como exacto. El usuario puede adoptar expresamente una referencia operativa y debe quedar auditada.

## Cálculo

Para recurrencia por componente con referencia exacta:

due_date = sumar el intervalo de calendario a performed_at.
due_km = odometer_km + interval_km.
is_due = (hoy local >= due_date si existe) OR (lectura actual >= due_km si existe).

No tomar el mínimo numérico entre fecha y km. Devolver ambos y los motivos que se cumplen. Sumar meses naturales, con política de fin de mes documentada: 31 de enero + 1 mes termina el último día de febrero. Evaluar fechas locales y guardar timestamps UTC donde corresponda. Inyectar reloj para pruebas y manejar horario de verano de Asia/Nicosia.

Usar la operación posterior válida que satisface la regla. El control del nivel no resetea el cambio; una reparación no satisface tareas no relacionadas. Se pueden cumplir varias reglas en una visita, conservando enlaces explícitos. Edición/anulación obliga a recalcular desde los eventos activos y a invalidar avisos antiguos.

Los hitos de fabricante y alternancia de revisiones no se transforman ciegamente en intervalos «desde el último trabajo». Codex debe representar y revisar sus fases/hitos por fuente. Un servicio inicial a kilometraje arbitrario no demuestra cuál fue la revisión anterior ni habilita saltarse operaciones más extensas. Mantener fase de servicio separada de referencias de componentes; una revisión inicial suficientemente completa podrá establecer una nueva referencia operativa explícita sin falsear los hitos originales. No asignar fase por el nombre comercial B/C sin comprobar su contenido.

Sin referencia fiable: crear tarea de mantenimiento inicial accionable, no calcular una fecha falsa ni un atraso preciso. La fecha de activación de la app puede programar esa tarea inicial, pero no contar como ejecución. Una intervención previa conocida sin fecha/km queda en Historial y no cancela una tarea periódica automáticamente. Inspección inicial y reemplazo son operaciones distintas; no forzar cambiar cualquier elemento cuya antigüedad sea desconocida.

## Avisos previos

30 días y 1.000 km son defaults de producto, editables, no intervalos del fabricante. Aplicarlos proporcionalmente: tareas semanales/mensuales necesitan otros horizontes o resumen; no lanzar «próximamente» permanentemente. Una sola instancia por tarea/generación puede tener ambas causas. Preservar próxima fecha y próximos km aunque uno ya venza. No convertir una estimación de conducción en lectura.

## WorkManager y registro persistente de avisos

Evaluar periódicamente con WorkManager aunque la interfaz esté cerrada, y después de cambios en lecturas, eventos, reglas o ajustes. Trabajo único persistente sin restricción de Internet, dependiente de Room y del mismo motor puro de cálculo. No usar temporizadores en UI como sustituto. Comprobación flexible que considera zona/hora local y ventanas de preferencia; no garantizar ejecución a un minuto concreto. Reintentos acotados ante I/O temporal y recuperación tras reinicio.

Ledger con clave única vehicle + task/rule + generation + notification_kind + reminder_window. Guardar decisiones y bandeja en transacción, con estado pending/emitted/cancelled y auditoría. Claims o exclusión equivalente evitan evaluación concurrente; recuperar claims antiguos. Una tarea con varios motivos se presenta una vez. Reevaluar vigencia justo antes de emitir: completar/editar/anular invalida generación anterior. Cancelar avisos visibles antiguos y recuperar operaciones incompletas al siguiente ciclo.

La API del SO y la transacción Room no son atómicas entre sí. Usar identificador de notificación estable y recuperación que actualiza la misma notificación, minimizando repeticiones; no afirmar entrega exactamente una vez. No marcar como recibido/ leído porque NotificationManager aceptó una llamada: emitted no demuestra visibilidad ni lectura. Bandeja creada aunque falte permiso, con estado correcto. Al conceder permiso, enviar resumen relevante limitado, sin reproducir una avalancha de avisos antiguos.

Tipos: próximo, vence, pendiente, resumen de controles y actualizar kilómetros. Pendientes semanal por defecto; resumen preferido 09:00 local, editable como ventana. Controles frecuentes agrupados, límites de ruido inicial. Notificación de prueba separada que no crea eventos de mantenimiento. Carga concisa, sin factura ni importes personales en pantalla bloqueada por defecto; deep link al detalle. Configuración de canales/permisos se consulta al operar. Reinstalación/restauración reconstruye planificación, cancela ids antiguos y no confunde estado de un teléfono anterior.

## Datos y seed

Seed idempotente por id, nunca sobrescribir eventos del propietario. No crear trabajos ficticios completados. fixture_started_at y reloj de pruebas separados de datos reales. Catálogo versionado y migración no destructiva: conservar overrides del propietario y revisión utilizada por cada evento. Las modificaciones personalizadas se etiquetan como tales y se diferencian de intervalos del fabricante.

## Arranque y operación Android

Migrar Room y sembrar idempotentemente antes de habilitar evaluador. El catálogo llega dentro del APK; funciones principales sin red. Flujos observables actualizan la UI después de commit. Muerte de proceso conserva datos confirmados y restaura formularios sin duplicar visitas. Fallos de importación no dejan registros parciales. Logs sin secretos ni contenido de adjuntos. ANDROID.md define permisos, compilación y firma; no crear servicio servidor.

## Gastos manuales

Expense: id, vehicle_id, paid_on, amount_cents EUR, concept, category (workshop, extras, fuel, other), notes opcionales, attachment_ids opcionales, service_visit_id opcional, voided_at y audit. Una visita pagada puede tener un gasto canónico ligado; importe de visita/factura y Expense no son dos gastos distintos. No contar líneas asignadas una segunda vez. Clave/idempotencia para doble toque y reintentos; vínculos únicos para pagos ya registrados en una visita, sin deduplicación destructiva por coincidencia aproximada.

Total acumulado suma gastos reales vigentes una sola vez, sin presupuestos ni importes desconocidos. Dinero exacto en céntimos y formato EUR; admitir entrada decimal con coma o punto según controles, evitar errores de conversión. Crear gasto de gasolina o extra independiente sin disparar eventos de mantenimiento, recalcular reglas ni cambiar lecturas. Editar/anular actualiza total y auditoría. Exportar y restaurar Expense con el resto de datos. No crear motor de informes financieros ni estadísticas de combustible.
