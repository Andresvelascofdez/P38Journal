package dev.p38journal.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.p38journal.data.*
import dev.p38journal.domain.Task
import dev.p38journal.domain.ConditionalProfiles
import dev.p38journal.domain.Consumption
import dev.p38journal.platform.DocumentViewer
import com.google.gson.JsonParser
import java.time.LocalDate
import dev.p38journal.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalApp(model: JournalViewModel, targetRule: String? = null, clearTarget: () -> Unit = {}) {
    val ready by model.ready.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val tasks by model.tasks.collectAsStateWithLifecycle()
    val workLines by model.workLines.collectAsStateWithLifecycle()
    val visits by model.visits.collectAsStateWithLifecycle()
    val works by model.works.collectAsStateWithLifecycle()
    val fuelEntries by model.fuelEntries.collectAsStateWithLifecycle()
    val deadlines by model.deadlines.collectAsStateWithLifecycle()
    val renewals by model.deadlineRenewals.collectAsStateWithLifecycle()
    val tyreEvents by model.tyreEvents.collectAsStateWithLifecycle()
    val readings by model.readings.collectAsStateWithLifecycle()
    val notes by model.notes.collectAsStateWithLifecycle()
    val expenses by model.expenses.collectAsStateWithLifecycle()
    val documents by model.documents.collectAsStateWithLifecycle()
    val inbox by model.inbox.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val planning by model.planning.collectAsStateWithLifecycle()
    val currentKm = readings.firstOrNull()?.km ?: planning?.baselineKm ?: 175000
    val groups = maintenanceGroups(tasks)
    val projectedGroups = projectedMaintenanceGroups(tasks)
    var tab by rememberSaveable { mutableStateOf("home") }
    var route by rememberSaveable { mutableStateOf("") }
    var recordId by rememberSaveable { mutableStateOf("") }
    var previousRoute by rememberSaveable { mutableStateOf("") }
    var previousId by rememberSaveable { mutableStateOf("") }
    var libraryLink by rememberSaveable { mutableStateOf("") }
    var selectedOperations by rememberSaveable { mutableStateOf("") }
    var taskBack by rememberSaveable { mutableStateOf("") }
    var ruleBack by rememberSaveable { mutableStateOf("operation") }
    var operationParent by rememberSaveable { mutableStateOf("") }
    var operationParentRoute by rememberSaveable { mutableStateOf("task") }
    var worksView by rememberSaveable { mutableStateOf("planned") }
    var relatedRoute by rememberSaveable { mutableStateOf("") }
    var relatedId by rememberSaveable { mutableStateOf("") }
    var relatedDestination by rememberSaveable { mutableStateOf("") }
    var visitIsNew by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    fun go(to: String, id: String = "") {
        if (to in setOf("secondary", "works", "fuels", "deadlines", "tyres")) { relatedRoute = ""; relatedId = ""; relatedDestination = "" }
        if (to == "task" && route !in setOf("profile", "override", "appointment")) taskBack = if (route in setOf("references", "real-plan")) route else ""
        if (to == "operation" && route != "override") {
            operationParent = if (route in setOf("task", "revision")) recordId else ""
            operationParentRoute = if (route == "revision") "revision" else "task"
        }
        if (to == "override") ruleBack = route
        if (to == "visit") visitIsNew = id.isBlank()
        val destinationId = if (id.isBlank() && to in setOf("visit", "expense", "fault", "part", "new-rule", "work-new", "fuel-new", "deadline-new")) freshId() else id
        if (to in setOf("visit", "expense", "fault", "part", "appointment", "work", "fuel", "deadline", "tyre-form", "work-new", "fuel-new", "deadline-new")) screenStates.removeState("$tab/$to/$destinationId")
        if (to == "documents") libraryLink = id
        route = to; recordId = destinationId
    }
    fun back() {
        if (route == relatedDestination && relatedRoute.isNotBlank()) {
            route = relatedRoute; recordId = relatedId; relatedRoute = ""; relatedId = ""; relatedDestination = ""; return
        }
        if (route == "task" && taskBack in setOf("references", "real-plan")) { route = taskBack; recordId = ""; return }
        if (route == "viewer") { route = "documents"; recordId = libraryLink; return }
        if (route == "override") { route = ruleBack; return }
        if (route == "operation" && operationParent.isNotBlank()) { route = operationParentRoute; recordId = operationParent; operationParent = ""; return }
        if (route in setOf("appointment", "profile")) { route = "task"; return }
        if (route == "documents" && previousRoute.isNotEmpty()) {
            route = previousRoute; recordId = previousId; previousRoute = ""; previousId = ""; return
        }
        if (route == "expense") tab = "expenses"
        route = when (route) {
            "fault", "part" -> "${route}s"
            "work", "work-new" -> "works"
            "fuel", "fuel-new" -> "fuels"
            "deadline", "deadline-new" -> "deadlines"
            "tyre", "tyre-form" -> "tyres"
            else -> ""
        }
        recordId = ""
    }
    fun attachments(id: String) { previousRoute = route; previousId = recordId; go("documents", id) }
    fun openWork(id: String) {
        selectedOperations = ""
        val tyreOperation = tyreEvents.firstOrNull { it.visitId == id && it.active && it.tracking } ?: tyreEvents.firstOrNull { it.visitId == id }
        if (tyreOperation != null) go("tyre-form", tyreOperation.id)
        else go(if (works.firstOrNull { it.id == id }?.manual == true && workLines.none { it.visitId == id && it.ruleId != null }) "work" else "visit", id)
    }
    fun openLinked(to: String, id: String) {
        if (route == relatedDestination && relatedId == id && (to == relatedRoute || to == "linked-work" && relatedRoute in setOf("work", "visit", "tyre-form"))) { back(); return }
        relatedRoute = route; relatedId = recordId
        if (to == "linked-work") openWork(id) else go(to, id)
        relatedDestination = route
    }
    fun openNotice(target: String) {
        when {
            target == "maintenance-summary" -> { tab = "plan"; route = "real-plan"; recordId = "" }
            target == "odometer" -> go("reading")
            target.startsWith("deadline:") -> go("deadline", target.removePrefix("deadline:"))
            target.startsWith("tyres:") -> go("tyre", target.removePrefix("tyres:"))
            target.isBlank() -> go("inbox")
            else -> go("task", target)
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    LaunchedEffect(targetRule, ready) {
        if (ready && targetRule != null) {
            tab = "home"
            openNotice(targetRule)
            clearTarget()
        }
    }
    BackHandler(route.isNotEmpty()) { back() }
    val title = when (route) {
        "revision" -> "Detalle de revisión"; "real-plan" -> "Vencimientos reales"
        "task", "operation" -> "Detalle de mantenimiento"; "new-rule", "override" -> "Editar mantenimiento"; "visit" -> "Registrar trabajo"; "reading" -> "Kilometraje"
        "expenses" -> "Gastos"; "expense" -> "Gasto"; "faults" -> "Averías"; "fault" -> "Avería"
        "parts" -> "Piezas"; "part" -> "Pieza"; "documents" -> "Manuales y documentos"; "viewer" -> "Documento"
        "works", "work", "work-new" -> "Trabajos y mejoras"
        "fuels", "fuel", "fuel-new" -> "Repostajes y consumo"
        "deadlines", "deadline", "deadline-new" -> "Documentación y vencimientos"
        "tyres", "tyre", "tyre-form" -> "Ruedas"
        "inbox" -> "Bandeja de avisos"; "settings" -> "Ajustes"; "copies" -> "Copias"; "references" -> "Referencias del mantenimiento"; "secondary" -> "Más opciones"; else -> if (tab == "plan") "Mantenimiento" else "P38 Journal"
    }
    Scaffold(topBar = { TopAppBar(title = { Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("v${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.labelSmall)
    } }, navigationIcon = {
        if (route.isNotEmpty()) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
    }, actions = { IconButton(onClick = { go("secondary") }) { Icon(Icons.Default.MoreHoriz, "Más opciones") } }) }, bottomBar = {
        NavigationBar {
            listOf(Triple("home", "Inicio", Icons.Default.Home), Triple("plan", "Mantenimiento", Icons.AutoMirrored.Filled.List),
                Triple("history", "Historial", Icons.Default.History), Triple("expenses", "Gastos", Icons.Default.Payments)).forEach { (id, label, icon) ->
                NavigationBarItem(selected = tab == id, onClick = { tab = id; route = ""; recordId = ""; previousRoute = ""; relatedRoute = ""; relatedDestination = "" }, modifier = Modifier.semantics { contentDescription = label },
                    icon = { Icon(icon, null) }, label = { Text(if (id == "plan") "Mant." else label, maxLines = 1, softWrap = false) })
            }
        }
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!ready) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(); Text("Preparando tus datos locales…")
                    message?.let { Text(it) }
                }
            } else screenStates.SaveableStateProvider("$tab/$route/$recordId") {
                when (route) {
                    "revision" -> ProjectedRevisionDetail(projectedGroups.firstOrNull { it.id == recordId }, tasks, currentKm, model.today(), { operations ->
                        selectedOperations = operations.joinToString("|"); go("visit")
                    }, { go("operation", it) })
                    "real-plan" -> RealPlanPage(groups, model.today(), currentKm, { go("new-rule") }) { go("task", it.representative.rule.id) }
                    "task", "operation" -> {
                        val group = if (route == "operation") tasks.firstOrNull { it.rule.id == recordId }?.let { MaintenanceGroup(listOf(it)) } else groups.firstOrNull { entry -> entry.tasks.any { it.rule.id == recordId } }
                            ?: tasks.firstOrNull { it.rule.id == recordId }?.let { MaintenanceGroup(listOf(it)) }
                        TaskDetail(group, currentKm, model.today(), planning?.baselineKm ?: 175000, { operations ->
                            selectedOperations = operations.joinToString("|"); go("visit")
                        }, { go("override", group?.representative?.rule?.id ?: recordId) }, { go("profile", recordId) }, { go("operation", it.rule.id) })
                    }
                    "profile" -> ConditionalProfileForm(model, tasks.firstOrNull { it.rule.id == recordId }, busy) { go("task", if (recordId.endsWith("-active")) recordId else "$recordId-active") }
                    "override" -> OverrideForm(model, tasks.firstOrNull { it.rule.id == recordId }, busy) { go("operation", it) }
                    "new-rule" -> OverrideForm(model, null, busy) { go("operation", it) }
                    "appointment" -> AppointmentForm(model, tasks.firstOrNull { it.rule.id == recordId }) { go("task", recordId) }
                    "visit" -> {
                        val original = works.firstOrNull { it.id == recordId }
                        if (!visitIsNew && original == null) Hint("Este trabajo ya no está activo o se está cargando.")
                        else VisitForm(model, original, tasks, expenses, selectedOperations.split("|").filter { it.isNotBlank() }, currentKm, busy, { selectedOperations = ""; go("", ""); tab = "history" }, ::attachments, { openLinked("expense", it) })
                    }
                    "reading" -> ReadingForm(model, readings, busy) { back() }
                    "expenses" -> ExpensesPage(expenses, { go("expense") }, { go("expense", it.id) })
                    "expense" -> ExpenseForm(model, expenses.firstOrNull { it.id == recordId }, works, expenses, busy, { if (relatedRoute.isNotBlank() && relatedDestination == "expense") back() else { go(""); tab = "expenses" } }, ::attachments, { openLinked("linked-work", it) }, { openLinked("fuel", it) })
                    "works" -> WorksPage(works, worksView, { worksView = it }, { go("work-new") }, { openWork(it.id) })
                    "work", "work-new" -> {
                        val original = works.firstOrNull { it.id == recordId }
                        if (route == "work" && original == null) Hint("Este trabajo ya no está activo o se está cargando.")
                        else ManualWorkForm(model, original, expenses, busy, { worksView = it.status; go("works") }, { openLinked("expense", it) }, { selectedOperations = ""; go("visit", it) }, ::attachments)
                    }
                    "fuels" -> FuelPage(fuelEntries, { go("fuel-new") }, { go("fuel", it.id) })
                    "fuel", "fuel-new" -> {
                        val original = fuelEntries.firstOrNull { it.id == recordId }
                        if (route == "fuel" && original == null) Hint("Este repostaje ya no está activo o se está cargando.")
                        else FuelForm(model, original, readings.firstOrNull()?.km, busy, { if (relatedDestination == "fuel" && relatedRoute.isNotBlank()) back() else go("fuels") }, { openLinked("expense", it) })
                    }
                    "deadlines" -> DeadlinesPage(deadlines, model.today(), { go("deadline-new") }, { go("deadline", it.id) })
                    "deadline", "deadline-new" -> {
                        val original = deadlines.firstOrNull { it.id == recordId }
                        if (route == "deadline" && original == null) Hint("Esta entrada no está disponible o se está cargando.")
                        else DeadlineForm(model, original, renewals, busy) { go("deadlines") }
                    }
                    "tyres" -> TyresPage(tyreEvents, readings.firstOrNull()?.km, { go("tyre", it) }, { go("tyre-form", "both/change") })
                    "tyre" -> TyreDetail(if (recordId == "rear") "rear" else "front", tyreEvents, readings.firstOrNull()?.km, { axis, kind -> go("tyre-form", "$axis/$kind") }, { go("tyre-form", it.id) })
                    "tyre-form" -> {
                        val first = tyreEvents.firstOrNull { it.id == recordId }
                        val originals = if (first?.visitId != null) tyreEvents.filter { it.visitId == first.visitId && it.active == first.active && it.tracking == first.tracking } else listOfNotNull(first)
                        val axes = if (originals.isNotEmpty()) originals.map { it.axis }.distinct() else if (recordId.startsWith("both/")) listOf("front", "rear") else listOf(recordId.substringBefore('/'))
                        val kind = first?.kind ?: recordId.substringAfter('/', "mount")
                        if (first == null && !recordId.contains('/')) Hint("Este registro de ruedas no está disponible o se está cargando.")
                        else TyreForm(model, axes, kind, originals, tyreEvents, expenses, busy, { if (relatedDestination == "tyre-form" && relatedRoute.isNotBlank()) back() else go(if (axes.size == 2) "tyres" else "tyre", axes.first()) }, { openLinked("expense", it) })
                    }
                    "faults", "parts" -> {
                        val kind = route.removeSuffix("s")
                        NotesPage(kind, notes, { go(kind) }, { go(kind, it.id) })
                    }
                    "fault", "part" -> {
                        val kind = route
                        NoteForm(model, kind, notes.firstOrNull { it.id == recordId }, visits, notes, busy, { go("${kind}s") }, ::attachments)
                    }
                    "documents" -> DocumentsPage(model, documents, recordId) { go("viewer", it.id) }
                    "viewer" -> documents.firstOrNull { it.id == recordId }?.let { DocumentViewer(it) { go("documents", libraryLink) } }
                    "inbox" -> InboxPage(inbox, ::openNotice)
                    "settings" -> SettingsPage(model, settings, busy, "preferences")
                    "copies" -> SettingsPage(model, settings, busy, "copies")
                    "references" -> ReferencesPage(tasks) { go("task", it.rule.id) }
                    "secondary" -> SecondaryPage { go(it) }
                    else -> when (tab) {
                        "home" -> HomePage(groups, readings, currentKm, model.today(), { go("reading") }, { selectedOperations = ""; go("visit") }, { go("task", it.representative.rule.id) }, { tab = "plan"; route = if (groups.any { it.status == "DUE" }) "real-plan" else "" }, fuelEntries, { go("expense") }, { go("fuel-new") }, { go("work-new") }, { go("fuels") })
                        "plan" -> ProjectedPlanPage(projectedGroups, currentKm, model.today(), { go("new-rule") }, { go("real-plan") }) { go("revision", it.id) }
                        "history" -> HistoryPage(visits, workLines, tasks, notes, documents, { selectedOperations = ""; go("visit") }, { openWork(it.id) }, ::attachments)
                        else -> ExpensesPage(expenses, { go("expense") }, { go("expense", it.id) })
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun HomePage(groups: List<MaintenanceGroup>, readings: List<Reading>, currentKm: Int, today: String, reading: () -> Unit, visit: () -> Unit, open: (MaintenanceGroup) -> Unit, plan: () -> Unit,
             fuelEntries: List<FuelEntry>, expense: () -> Unit, fuel: () -> Unit, manualWork: () -> Unit, fuelList: () -> Unit) {
    Page {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Range Rover P38", style = MaterialTheme.typography.headlineSmall)
                Hint("Autobiography · 4.6 V8 · Automático · 2000")
                val current = readings.firstOrNull()
                Text(kmLabel(currentKm), style = MaterialTheme.typography.headlineSmall)
                Hint(current?.let { "Lectura del ${dateLabel(it.date)}" } ?: "Valor inicial")
                TextButton(onClick = reading) { Text("Actualizar kilómetros") }
            }
        }
        Button(onClick = visit, modifier = Modifier.fillMaxWidth()) { Text("Registrar trabajo") }
        Hint("Mantenimiento realizado: selecciona expresamente las operaciones completadas.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = expense, modifier = Modifier.weight(1f).testTag("quick_expense")) { Text("Añadir gasto") }
            OutlinedButton(onClick = fuel, modifier = Modifier.weight(1f).testTag("quick_fuel")) { Text("Registrar repostaje") }
        }
        OutlinedButton(onClick = manualWork, modifier = Modifier.fillMaxWidth().testTag("quick_manual_work")) { Text("Añadir trabajo o mejora") }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Consumo medio", style = MaterialTheme.typography.titleMedium)
            Text(consumptionLabel(Consumption.calculate(fuelEntries).average), modifier = Modifier.testTag("home_consumption_average"))
            TextButton(onClick = fuelList) { Text("Ver repostajes") }
        } }
        val pending = groups.filter { it.status == "DUE" }
        if (pending.isEmpty()) Heading("Todo al día") else {
            Heading("Pendiente")
            pending.take(3).forEach { GroupCard(it, currentKm, today) { open(it) } }
        }
        val next = groups.firstOrNull { it.status != "DUE" }
        next?.let { Heading(if (it.status == "SOON") "Próximamente" else "Próximo mantenimiento"); GroupCard(it, currentKm, today) { open(it) } }
        OutlinedButton(onClick = plan, modifier = Modifier.fillMaxWidth()) { Text(if (pending.isEmpty()) "Ver mantenimiento" else "Ver todos los pendientes (${pending.size})") }
    }
}

@Composable
fun GroupCard(group: MaintenanceGroup, currentKm: Int, today: String, open: () -> Unit) {
    ItemCard(group.title, statusLabel(group.status), open) {
        if (group.dueKm != null) Text("Próximo: ${kmLabel(group.dueKm)}")
        if (group.dueDate != null) Text(dateLabel(group.dueDate))
        Hint(remainingLabels(group, currentKm, today).joinToString(" · "))
        if (group.tasks.size > 1) Hint("${group.tasks.size} operaciones incluidas")
    }
}

@Composable
fun RealPlanPage(groups: List<MaintenanceGroup>, today: String, currentKm: Int, add: () -> Unit, open: (MaintenanceGroup) -> Unit) {
    var status by rememberSaveable { mutableStateOf("all") }
    Page {
        Hint("Cada operación aparece solo en su próximo vencimiento real.")
        Choice("Mostrar", status, listOf("all" to "Todos", "SOON" to "Próximos", "DUE" to "Pendientes")) { status = it }
        val filtered = groups.filter { status == "all" || it.status == status }
        filtered.forEach { GroupCard(it, currentKm, today) { open(it) } }
        if (filtered.isEmpty()) Hint(if (status == "DUE") "Todo al día. No hay mantenimientos pendientes." else "No hay mantenimientos en esta selección.")
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Crear mantenimiento") }
    }
}

@Composable
fun TaskDetail(group: MaintenanceGroup?, currentKm: Int, today: String, baselineKm: Int, record: (List<String>) -> Unit, customize: () -> Unit, profile: () -> Unit, operation: (Task) -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var sourceOpen by rememberSaveable { mutableStateOf(false) }
    Page {
        if (group == null) { Hint("Este mantenimiento ya no está disponible."); return@Page }
        val task = group.representative
        Heading(group.title)
        if (group.tasks.size == 1) {
            Text(operationInterval(task))
            if (task.rule.origin == "personalized") Hint("Personalizado")
            if (task.rule.origin == "custom") Hint("Operación propia")
            if (!task.rule.enabled) Hint("Recordatorios desactivados")
            if (task.rule.notes.isNotBlank()) Text(task.rule.notes)
            OutlinedButton(onClick = customize, modifier = Modifier.fillMaxWidth()) { Text("Editar mantenimiento") }
        } else Hint("Próximos vencimientos coincidentes. Consulta el intervalo de cada operación.")
        group.dueKm?.let { Text("Próximo: ${kmLabel(it)}", style = MaterialTheme.typography.titleMedium) }
        group.dueDate?.let { Text("Fecha: ${dateLabel(it)}") }
        if (group.dueKm != null && group.dueDate != null) Hint("Lo que ocurra antes")
        remainingLabels(group, currentKm, today).forEach { Text(it) }
        val last = group.tasks.mapNotNull { it.lastEvent }.maxByOrNull { it.date ?: "" }
        if (last != null) Hint("Último registrado: ${dateLabel(last.date)} · ${kmLabel(last.km)}")
        else if (group.dueDate != null || group.dueKm != null) Hint("Plan calculado desde ${kmLabel(baselineKm)}")
        if (group.dueDate != null || group.dueKm != null) {
            Button(onClick = { record(group.tasks.map { it.rule.id }) }, modifier = Modifier.fillMaxWidth()) { Text("Registrar realizado") }
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "Ocultar qué incluye" else "Qué incluye") }
        }
        if (expanded) {
            Hint("Consulta o edita una operación. Las realizadas se seleccionan en el formulario de registro.")
            group.tasks.forEach { member ->
                ItemCard(friendlyTitle(member), operationInterval(member), { operation(member) }) {
                    Hint("Consultar y editar operación")
                    if (member.rule.origin == "personalized") Hint("Personalizado")
                    if (!member.rule.enabled) Hint("Recordatorios desactivados")
                }
            }
        }
        val ownWithoutSource = group.tasks.size == 1 && task.rule.origin == "custom" && task.rule.source.isBlank()
        TextButton(onClick = { sourceOpen = !sourceOpen }) { Text(if (ownWithoutSource) if (sourceOpen) "Ocultar información" else "Información de la operación" else if (sourceOpen) "Ocultar fuente del fabricante" else "Ver fuente del fabricante") }
        if (sourceOpen) {
            val sources = group.tasks.map { it.rule.source }.filter { it.isNotBlank() }.distinct()
            sources.forEach { Text(manufacturerReference(it)) }
            if (sources.isEmpty()) Hint("Operación creada por ti, sin atribución al fabricante.")
            group.tasks.map { it.rule.condition }.filter { it.isNotBlank() }.distinct().forEach { Hint(it) }
            Hint("Las referencias distinguen páginas impresas y páginas del PDF.")
            if (task.rule.sourceUrl.isNotBlank()) OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.rule.sourceUrl))) }.onFailure {
                    android.widget.Toast.makeText(context, "No hay una aplicación disponible para abrir el enlace.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, enabled = task.rule.sourceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Abrir documento") }
            if (group.tasks.size == 1 && (ConditionalProfiles.proposed(task.rule) != null || task.rule.id.endsWith("-active"))) {
                OutlinedButton(onClick = profile, modifier = Modifier.fillMaxWidth()) { Text(if (task.rule.id.endsWith("-active")) "Gestionar perfil condicionado" else "Confirmar aplicabilidad y activar intervalo") }
            }
        }
    }
}

@Composable
fun AppointmentForm(model: JournalViewModel, task: Task?, after: () -> Unit) {
    var date by rememberSaveable { mutableStateOf(model.today()) }
    var notes by rememberSaveable { mutableStateOf("") }
    val id = rememberSaveable { freshId() }
    Page {
        Heading(task?.rule?.title ?: "Cita de taller")
        Hint("Programar la cita no marca operaciones realizadas ni reinicia intervalos.")
        Field(date, { date = it }, "Fecha (AAAA-MM-DD)")
        Field(notes, { notes = it }, "Taller o notas", multiline = true)
        Button(onClick = {
            runCatching { validateDate(date) }.onSuccess { valid ->
                model.saveNote(Note(id = id, kind = "appointment", title = task?.rule?.title ?: "Cita", body = notes, status = "scheduled", date = valid, relatedId = task?.rule?.id), after)
            }.onFailure { model.announce("Introduce una fecha válida (AAAA-MM-DD).") }
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cita") }
    }
}

@Composable
fun HistoryPage(visits: List<Visit>, lines: List<WorkLine>, tasks: List<Task>, notes: List<Note>, documents: List<Document>, add: () -> Unit, edit: (Visit) -> Unit, attachments: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Page {
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Registrar trabajo") }
        Field(search, { search = it }, "Buscar título, operaciones, taller o notas", "history_search")
        Heading("Trabajos registrados")
        if (visits.isEmpty()) Hint("Aquí se conservarán los trabajos que registres.")
        val matches = visits.filter { visit ->
            val operations = lines.filter { it.visitId == visit.id }.joinToString(" ") { line -> line.title + " " + (tasks.firstOrNull { it.rule.id == line.ruleId }?.rule?.title ?: "") }
            val visitNotes = runCatching { JsonParser.parseString(visit.notes).asJsonObject.get("notes")?.asString ?: "" }.getOrDefault(visit.notes)
            listOf(visit.title, operations, visit.workshop, visitNotes).joinToString(" ").contains(search.trim(), ignoreCase = true)
        }
        if (matches.isEmpty() && visits.isNotEmpty()) Hint("No hay trabajos que coincidan con la búsqueda.")
        matches.sortedByDescending { it.date }.forEach { visit ->
            ItemCard(visit.title, "${dateLabel(visit.date)}${if (visit.date.isNotBlank() && !visit.dateExact) " (aproximada)" else ""} · ${kmLabel(visit.km)}${if (!visit.kmExact && visit.km != null) " (aproximados)" else ""}${if (!visit.active) " · Anulado" else ""}", { edit(visit) }) {
                if (visit.workshop.isNotBlank()) Text(visit.workshop)
                Hint(visit.paidCents?.let(::money) ?: "Importe desconocido")
                val count = documents.count { it.tags.split(" ").contains(visit.id) }
                TextButton(onClick = { attachments(visit.id) }) { Text("Adjuntos ($count)") }
            }
        }
        Heading("Historial anterior a la compra")
        notes.filter { it.kind == "history" }.forEach { note ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(note.title, style = MaterialTheme.typography.titleMedium); Text(historyDescription(note.body)); Hint("Resumen histórico; no es una operación confirmada hoy.") } }
        }
    }
}

private fun historyDescription(body: String): String = runCatching {
    val note = JsonParser.parseString(body).asJsonObject
    buildList {
        note.get("description")?.takeIf { !it.isJsonNull }?.asString?.let(::add)
        if (note.has("performed_before_purchase")) {
            add("Intervención anterior a la compra. Fecha de ejecución no conocida.")
            note.get("odometer_km")?.takeIf { !it.isJsonNull }?.asInt?.let { add("${kmLabel(it)}${if (note.get("odometer_precision")?.asString == "approximate") " aproximados" else ""}.") }
            add("Importe desconocido. Procedencia: resumen de conversación; factura original no revisada. No establece una referencia exacta de recurrencia.")
        } else add("Antecedente sin confirmación de avería activa hoy.")
    }.joinToString("\n")
}.getOrDefault(body)

@Composable
fun SecondaryPage(go: (String) -> Unit) {
    Page {
        listOf("works" to "Trabajos y mejoras", "deadlines" to "Documentación y vencimientos", "tyres" to "Ruedas", "fuels" to "Repostajes y consumo",
            "settings" to "Ajustes", "documents" to "Manuales y documentos", "references" to "Referencias del mantenimiento", "copies" to "Copias", "inbox" to "Bandeja de avisos").forEach { (id, title) ->
            ItemCard(title, onClick = { go(id) })
        }
        Heading("Otros registros conservados")
        TextButton(onClick = { go("faults") }) { Text("Averías") }
        TextButton(onClick = { go("parts") }) { Text("Piezas") }
    }
}

@Composable
fun ReferencesPage(tasks: List<Task>, open: (Task) -> Unit) {
    Page {
        Hint("Controles orientativos, condiciones de aplicación y perfiles. Consulta la fuente y confirma una condición únicamente cuando esté comprobada.")
        tasks.filter { it.rule.scheduleVisibility in setOf("manual", "conditional", "inactive") || it.rule.recurrence == "inactive" || it.rule.id.endsWith("-active") || it.rule.origin in setOf("custom", "personalized") }
            .sortedBy { friendlyTitle(it) }.forEach { task ->
                ItemCard(friendlyTitle(task), when {
                    task.rule.recurrence == "once" && task.dueDate == null && task.dueKm == null && task.lastEvent != null -> "Tarea única completada"
                    task.rule.origin == "custom" -> "Operación propia"
                    task.rule.origin == "personalized" -> "Personalizado"
                    task.rule.recurrence == "inactive" -> "Perfil desactivado"
                    task.rule.id.endsWith("-active") -> "Perfil confirmado"
                    task.rule.scheduleVisibility == "manual" -> "Control orientativo"
                    else -> "Según condición"
                }, { open(task) })
            }
    }
}

@Composable
fun InboxPage(items: List<Inbox>, open: (String) -> Unit) {
    Page {
        Hint("La bandeja se conserva aunque Android tenga los avisos desactivados. Emitido no demuestra que lo hayas visto.")
        if (items.isEmpty()) Hint("Todavía no hay avisos. Se actualizará al evaluar tu plan.")
        items.sortedByDescending { it.createdAt }.forEach { item ->
            ItemCard(item.title, item.body, { open(item.ruleId) }) { Hint(when (item.state) { "emitted" -> "Emitido al sistema"; "cancelled" -> "Ya no vigente"; "blocked" -> "Permiso o canal desactivado"; else -> "En bandeja" }) }
        }
    }
}
