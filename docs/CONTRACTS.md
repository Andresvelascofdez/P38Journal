# Ampliación vigente5.0 / build6

REQUEST_V6.md y docs/CONTRACT_V6.md prevalecen sobre las exclusiones de consumo y esquema anteriores. El consumo sencillo está autorizado: llenos completos, litros posteriores y media ponderada, sin gráficos/informes. Trabajos previstos/realizados reutilizan Visit; documentación libre, renovación manual y ruedas2ejes separados del fabricante. Room4/Snapshot4 compatibles con2/3 y todos los nuevos datos/relaciones en copias. No inventar montajes a175.000, lecturas, trabajos ni datos históricos. Mantener intactas las funciones de mantenimiento de4.1 y filtros de gastos. Fuente base local6957c1a, respaldo previo en .tools/pre-v6. La evidencia final de esta entrega estará en docs/TEST_REPORT.md; las verificaciones4.1 archivadas son antecedentes.

## Antecedentes históricos, aplicables solo donde no contradigan REQUEST_V6

# Corrección vigente4.1 / build5

REQUEST_V5.md sustituye el selector4.0: Mantenimiento muestra directamente las revisiones proyectadas completas. Recorrido: Repository.tasks → Engine.evaluate (vencimientos reales) → Forecast.project (apariciones independientes) → projectedMaintenanceGroups (ID por grupo/fecha/km) → ProjectedPlanPage → ProjectedRevisionDetail. Contador y «Qué incluye» consumen occurrences del mismo grupo, sin resolver el detalle desde una Task representativa.

Desde175.000 sin historial:195.00029,215.00033,235.00031; cifras de pruebas derivadas del catálogo, no constantes productivas. firstKm/firstMonths solo fijan la primera aparición; siguientes usan km/months recurrentes. Proyección condicionada a cumplir revisiones anteriores; no genera trabajos ni avisos. Home, Vencimientos reales, TaskDetail, NotificationEvaluator y WorkManager siguen utilizando únicamente Task reales. Registrar desde una revisión ofrece solo sus apariciones sequence0 que coinciden con el próximo vencimiento real; las siguientes son consulta.

La lista muestra los primeros cuatro grupos globales completos; operaciones propias aisladas con próximo vencimiento real permanecen visibles también fuera de ese horizonte. No se añaden grupos colectivos lejanos con una proyección truncada. Las operaciones reales completas siguen accesibles en Vencimientos reales. Trabajo parcial195.200 produce215.200 exacto para aceite; las restantes permanecen195.000. Fechas se recalculan por operación desde el trabajo real. Abrir una previsión no escribe Visit/Event/Reading.

Sin cambio de Room3, catálogo, applicationId ni firma. Conservar edición, filtros de gastos y copias. Pruebas de pantalla nativa y detalle completo, parcial, actualizaciones reales de APK y evidencias nuevas en docs/TEST_REPORT.md; resultados4.0 archivados como antecedentes.

## Antecedentes conservados donde sean compatibles

# Contratos vigentes v4

REQUEST_V4.md es el encargo vigente. Versión 4.0.0/code 4; release `dev.p38journal` y firma externa original, debug separado. No publicar ni enviar cambios a GitHub durante este encargo. El historial del repositorio se conserva.

Propiedad: domain modifica Engine/data/esquemas y pruebas de persistencia; ui modifica Compose/ViewModel y pruebas UI; root implementa Forecast/ExpenseFilters, plataforma/notificaciones/build/scripts/entrega/documentación; review revisa independientemente y añade sus pruebas/documento. Gradle y el emulador son exclusivos del coordinador.

`ForecastOccurrence(rule, dueDate, dueKm, sequence)` tiene `assumesPrevious=sequence>0`. `Forecast.project(tasks, stepsPerRule=4)` proyecta a partir de Tasks reales, incluyendo sequence 0, sin eventos ni mutación. La UI agrupa separadamente y explica la condición de ejecutar revisiones previas en sus límites. No hay estado de vencimiento/registro/avisos para previsiones futuras.

`ExpenseFilter(month:Int?, year:Int?, category:String?)` usa calendario ISO. `ExpenseFilters.apply(items,filter,date,cents,category,active)` retorna rows, subtotalCents, totalCents y years descendentes de activos. La UI conserva filtros con rememberSaveable. No Double.

Rule se amplía por JSON con origin (manufacturer/personalized/custom), notes, enabled, dueDate/dueKm para once y manufacturerRevision. Defaults compatibles con copias anteriores. Repo expone saveCustomRule, saveRuleOverride, setRuleEnabled, resetRule y originalRule; mismas reglas/IDs e historial, sin copias al editar. Personalización simple de fase limpia first*. Notificaciones filtra enabled, mientras el motor conserva consulta de límites.

Gastos inactivos quedan sin visitId, con referencia previa en auditoría. Normalización idempotente de datos existentes/restaurados antes de imponer vínculo activo. Guardar visita conserva los metadatos del gasto existente y mantiene un único importe canónico. Room/snapshot permanecen esquema 3 al no requerirse columnas nuevas; la ampliación JSON y normalización se verifican con datos previos.

## Contratos v3 — antecedente compatible

# Contratos de integración v3

Contrato vigente para la app Android existente: release `dev.p38journal`, debug `dev.p38journal.debug`, versión 3.0.0 y versionCode 3. La firma externa se conserva. La documentación v3 prevalece sobre el antecedente v2 del final.

Responsabilidades: domain modifica dominio, Room, esquemas y sus pruebas; ui modifica Compose y sus recorridos; catalogue mantiene JSON y documentación de fuentes; root integra plataforma, avisos, build, firma y entrega; review revisa de forma independiente y aporta pruebas propias. Gradle y los recorridos del emulador se ejecutan de forma exclusiva.

## Dominio y migración

La referencia operativa es independiente de lecturas y trabajos. El valor inicial es 175.000 km y se identifica como tal cuando todavía no existe una lectura real. Establecer la referencia no crea Reading, Event ni Visit.

Rule incorpora `scheduleVisibility`, `serviceGroup`, `firstKm` y `firstMonths`. Las visibilidades son maintenance/service/manual/conditional/inactive. `Engine.evaluate` recibe `plan: PlanningReference?=null` al final de la firma; la app siempre proporciona el plan persistido. El motor no genera INITIAL. Calcula cada dimensión desde la ejecución exacta válida más reciente o desde su referencia anterior/base. B empieza a 12 meses/20.000 km y C a 24 meses/40.000 km; ambas repiten cada 24 meses/40.000 km. Los hitos únicos ya pasados, condiciones sin confirmar y antigüedades sin fecha original no alertan.

La interfaz agrupa por `serviceGroup+dueDate+dueKm`, conserva cada operación y permite registrar solo las seleccionadas. El catálogo contiene 62 reglas y cubre las 59 filas, con dos inspecciones de servicio separadas de sus controles rutinarios.

La migración 2→3 conserva datos y comprueba un respaldo privado antes de abrir la base anterior. Añade planning y los campos origin/policyVersion de Inbox/Ledger. Retira derivados reconocidos mediante metadata y relaciones; no utiliza títulos. Los intervalos personalizados anteriores se conservan.

`saveReading` guarda en la misma transacción el marker `v3-reconcile`, de tipo reading y estado pending. El evaluador lo reconcilia al terminar, para recuperar una evaluación interrumpida. `saveVisit` admite `updateCurrentReading: Reading?` en la misma transacción; la UI exige confirmación y no reduce la lectura por registrar un trabajo pasado.

### Referencia persistente y esquema 3

`domain.PlanningReference` es un valor puro; `data.PlanningRecord` lo persiste en `planning_reference`. Sus campos son `id="main"`, `baselineKm=175000`, `baselineDate` ISO local, `provenance="user_selected_planning_baseline"` y `policyVersion=3`. La fecha inicial se recupera de una auditoría explícita de creación cuando existe; en caso contrario se utiliza la fecha local de migración/creación. Establecer o editar este plan no crea lecturas, visitas ni trabajos completados.

El repositorio expone `suspend planningReference(): PlanningReference`, `observePlanningReference(): Flow<PlanningReference?>` y `suspend updatePlanningReference(date: String, km: Int = 175000)`. `tasks()` pasa al motor el plan persistido y la lectura real válida, o el kilometraje base si todavía no existe lectura. Las reglas antiguas sin campos de visibilidad reciben la política del catálogo actual; los intervalos personalizados se conservan.

El snapshot JSON exportado usa `schemaVersion=3` e incluye un array `planning` con la referencia principal, además de los registros existentes. Las APIs siguen siendo `suspend exportSnapshot(): String`, `validateSnapshot(json: String)` y `suspend restoreSnapshot(json: String)`. La importación admite esquema 2 y no fabrica trabajos ni lecturas. Si falta el plan, puede recuperar la fecha de creación de la auditoría importada; un plan local editado explícitamente se conserva. La restauración combina ids sin duplicar registros, conserva las personalizaciones locales y permite aplicar una personalización de la copia sobre una regla predeterminada recién sembrada; una regla estándar antigua no rebaja la revisión actual. El ledger de emisiones se reconstruye en lugar de importar emisiones como si ya se hubieran realizado en esta instalación.

### Respaldo anterior a abrir la base

Antes de abrir una base existente de esquema 1/2 con Room, `PreMigrationBackup.ensure()` copia el archivo SQLite y su WAL, si existe, a `filesDir/migration-backups/`. Se incluye WAL porque puede contener datos y la versión de esquema todavía no incorporados al archivo principal. La copia verifica los SHA-256 y ejecuta `PRAGMA integrity_check` sobre la base copiada; su manifiesto guarda versión de origen/destino, fecha, checksums y resultado de integridad. Un fallo detiene la apertura y migración. Un reintento reutiliza una copia ya verificada del mismo contenido.

Este respaldo privado previo no sustituye al ZIP completo exportable mediante SAF: los adjuntos permanecen en su ubicación privada original y la migración no los modifica. Las migraciones 1→2→3 preservan los datos de negocio; 2→3 crea el plan y añade `origin`/`policyVersion` a bandeja y ledger, con operaciones de esquema idempotentes.

### Procedencia de avisos y ledger efectivo

Los avisos nuevos llevan `origin="automatic"` y `policyVersion=3`. Al migrar datos anteriores, se identifica procedencia automática mediante tipos conocidos del ledger y su relación con la bandeja. Un registro cuya procedencia no puede demostrarse recibe `origin="legacy_unknown"`; los registros manuales o desconocidos se preservan. La cancelación de avisos iniciales antiguos se limita a registros automáticos identificados por esos metadatos y relaciones. No se clasifica ni borra por el texto del título, y no se elimina ningún evento de mantenimiento.

La generación utiliza los límites y referencias efectivas seleccionados por el motor, no la lista completa de históricos ni sus ids. `saveVisit` y `voidVisit` comparan las generaciones antes/después y cancelan los avisos afectados solo cuando cambia el ciclo efectivo. Editar notas/taller o añadir/anular un trabajo antiguo sin efecto en el calendario mantiene el estado emitido y evita un aviso repetido.

`saveReading` guarda la lectura y el marcador único `id="v3-reconcile"`, `ruleId="system"`, `kind="reading"`, `state="pending"` en la misma transacción. El evaluador protege este marcador y lo marca `reconciled` al terminar, permitiendo recuperar la evaluación inmediata tras una interrupción. La emisión Android y el commit del ledger no son atómicos: la recuperación actualiza el mismo id de notificación, sin prometer recepción exactamente una vez.

## Plataforma y UI

`Notifications.evaluateNow(context, trigger="change")` se ejecuta tras confirmar lecturas, trabajos o permisos. La evaluación manual no espera a las 09:00; WorkManager evalúa fechas y recupera evaluaciones sin Internet. Respeta activación, permiso y canal. Un resumen de varios grupos abre Mantenimiento con `maintenance-summary`; uno solo abre su detalle. Cancela los IDs anteriores 1003/1004 y deduplica por regla, generación, estado y ventana.

La navegación principal es Inicio/Mantenimiento/Historial/Gastos. Ajustes, manuales, referencias, copias, bandeja, averías y piezas quedan en opciones secundarias. El detalle muestra intervalo, límite, restante, último registro y registro realizado; las operaciones incluidas y las fuentes se despliegan. El formulario precarga operación, fecha y km; taller, notas, costes y adjuntos son opcionales.

Se conservan ZIP completos, visor nativo y acceso SAF. No cambia la clave de firma ni se añaden permisos generales de almacenamiento.

---

## Contrato historico v2 sustituido donde contradiga v3

# Contratos de integración

Workspace: C:/Users/Admin/SapAssistant/Range rover. Paquete dev.p38journal. Android nativo; no servidor. Los agentes leen los documentos originales UTF-8. Propiedad: catálogo data/maintenance-catalogue.json y docs/CATALOGUE*; dominio domain/ y data/ y sus tests; interfaz ui/ y MainActivity.kt; coordinador Gradle, platform/, manifest, recursos, copias, integración y entrega.

## Dominio y persistencia (API fijada)
Rule(id:String,title:String,system:String,operation:String,days:Int?=null,months:Int?=null,km:Int?=null,source:String,sourceUrl:String,condition:String="",initialAction:String=title,priority:Int=2,recurrence:String="component",revision:Int=1).
Event(id:String,visitId:String,ruleId:String,date:String?,km:Int?,dateExact:Boolean=true,kmExact:Boolean=true,active:Boolean=true,revision:Int=1).
Task(rule:Rule,status:String,dueDate:String?,dueKm:Int?,reasons:List<String>,generation:String,lastEvent:Event?). Status INITIAL/DUE/SOON/PLANNED. Pure Engine.evaluate(rules:List<Rule>,events:List<Event>,today:LocalDate,currentKm:Int?,leadDays:Int=30,leadKm:Int=1000):List<Task>. Calendar clamp via java.time.

Room entities Visit(id,date,km,kmExact,title,workshop,notes,paidCents:Long?,active), WorkLine(id,visitId,title,ruleId:String?,costCents:Long?), Expense(id,date,cents:Long,concept,category,notes,visitId:String?,active), Reading(id,date,km), Note(id,kind,title,body,status,date,relatedId:String?), Document(id,name,mime,path,sha256,size:Long,tags:String=""), Inbox(id,ruleId,title,body,generation,state,createdAt). Defaults suitable for UI, entity types in data package. Notes kind fault/part/history; body stores JSON for structured fault/part fields where needed.

Repository(context:Context) exposes db:JournalDatabase, dao:JournalDao, suspend initialize(), Flow observeVisits/observeExpenses/observeReadings/observeNotes/observeDocuments/observeInbox, suspend tasks():List<Task>, suspend currentKm():Int?, suspend saveVisit(visit:Visit,lines:List<WorkLine>,ruleIds:List<String>,existingExpenseId:String?=null), voidVisit(id), saveExpense(expense), voidExpense(id), saveReading(reading,allowCorrection:Boolean=false), saveNote(note), deleteNote(id), saveDocument(document), deleteDocument(id), saveInbox(inbox). Editing visit replaces associated events transactionally; explicit same id avoids double save. Single expense per linked visit; visit payment counted once. Expose suspend allEvents(), allRules(), allRecords(): backup integration coordinator will adapt DAO. Migration schema v1→v2 non destructive. Repositories no main-thread IO. Settings platform/Preferences.kt coordinator.

## Catalogue JSON
{"schema_version":1,"revision":1,"rules":[{"id":"...","title":"...","system":"...","operation":"...","days":null,"months":12,"km":20000,"source":"M01 LRL0354SPA 04/00 PDF 1 row 37; glossary PDF 2","sourceUrl":"https://...","condition":"...","initialAction":"...","priority":2,"recurrence":"component","revision":1}]}. Audit complete matrix in documentation; preserve phase/milestone conditions rather than manufacture component intervals. Conditional applicability creates concrete workshop check, no false compatibility.

## Coordinator platform API for UI
Preferences(context) flow:Flow<Settings>; suspend update(settings). Settings(zone:String="Asia/Nicosia",leadDays:Int=30,leadKm:Int=1000,hour:Int=9,enabled:Boolean=false,weekly:Boolean=true,checks:Boolean=true,odometerReminder:Boolean=true).
Notifications.schedule(context), Notifications.refresh(context), Notifications.test(context), Notifications.permissionState(context):String, Notifications.openSettings(context). WorkManager uses same Repository and Engine.
Files(context,repo): suspend importDocument(uri:Uri):Document, exportBackup(uri:Uri), previewBackup(uri:Uri):String, restoreBackup(uri:Uri), openDocument(document). Coordinator writes PDF viewer and search screen via platform.DocumentViewer composable(document:Document,onBack:()->Unit). Attachment linkage via Document.tags with record IDs. Backup methods copy private bytes and validate full snapshot before transactional restore.
MainActivity creates repository, initialize before UI. UI can call Notifications.refresh after domain changes.
