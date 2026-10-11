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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.p38journal.data.*
import dev.p38journal.domain.Task
import dev.p38journal.domain.ConditionalProfiles
import dev.p38journal.platform.DocumentViewer
import com.google.gson.JsonParser
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalApp(model: JournalViewModel, targetRule: String? = null, clearTarget: () -> Unit = {}) {
    val ready by model.ready.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val tasks by model.tasks.collectAsStateWithLifecycle()
    val workLines by model.workLines.collectAsStateWithLifecycle()
    val visits by model.visits.collectAsStateWithLifecycle()
    val readings by model.readings.collectAsStateWithLifecycle()
    val notes by model.notes.collectAsStateWithLifecycle()
    val expenses by model.expenses.collectAsStateWithLifecycle()
    val documents by model.documents.collectAsStateWithLifecycle()
    val inbox by model.inbox.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val planning by model.planning.collectAsStateWithLifecycle()
    val currentKm = readings.firstOrNull()?.km ?: planning?.baselineKm ?: 175000
    val groups = maintenanceGroups(tasks)
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
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    fun go(to: String, id: String = "") {
        if (to == "task" && route !in setOf("profile", "override", "appointment")) taskBack = if (route == "references") "references" else ""
        if (to == "operation") operationParent = if (route == "task") recordId else ""
        if (to == "override") ruleBack = route
        val destinationId = if (id.isBlank() && to in setOf("visit", "expense", "fault", "part", "new-rule")) freshId() else id
        if (to in setOf("visit", "expense", "fault", "part", "appointment")) screenStates.removeState("$tab/$to/$destinationId")
        if (to == "documents") libraryLink = id
        route = to; recordId = destinationId
    }
    fun back() {
        if (route == "task" && taskBack == "references") { route = "references"; recordId = ""; return }
        if (route == "viewer") { route = "documents"; recordId = libraryLink; return }
        if (route == "override") { route = ruleBack; return }
        if (route == "operation" && operationParent.isNotBlank()) { route = "task"; recordId = operationParent; operationParent = ""; return }
        if (route in setOf("appointment", "profile")) { route = "task"; return }
        if (route == "documents" && previousRoute.isNotEmpty()) {
            route = previousRoute; recordId = previousId; previousRoute = ""; previousId = ""; return
        }
        if (route == "expense") tab = "expenses"
        route = when (route) { "fault", "part" -> "${route}s"; else -> "" }
        recordId = ""
    }
    fun attachments(id: String) { previousRoute = route; previousId = recordId; go("documents", id) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    LaunchedEffect(targetRule, ready) {
        if (ready && targetRule != null) {
            tab = "home"
            when (targetRule) {
                "odometer" -> go("reading")
                "maintenance-summary" -> { tab = "plan"; route = ""; recordId = "" }
                "" -> go("inbox")
                else -> go("task", targetRule)
            }
            clearTarget()
        }
    }
    BackHandler(route.isNotEmpty()) { back() }
    val title = when (route) {
        "task", "operation" -> "Detalle de mantenimiento"; "new-rule", "override" -> "Editar mantenimiento"; "visit" -> "Registrar trabajo"; "reading" -> "Kilometraje"
        "expenses" -> "Gastos"; "expense" -> "Gasto"; "faults" -> "Averías"; "fault" -> "Avería"
        "parts" -> "Piezas"; "part" -> "Pieza"; "documents" -> "Manuales y documentos"; "viewer" -> "Documento"
        "inbox" -> "Bandeja de avisos"; "settings" -> "Ajustes"; "copies" -> "Copias"; "references" -> "Referencias del mantenimiento"; "secondary" -> "Más opciones"; else -> "P38 Journal"
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
        if (route.isNotEmpty()) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
    }, actions = { IconButton(onClick = { go("secondary") }) { Icon(Icons.Default.MoreHoriz, "Más opciones") } }) }, bottomBar = {
        NavigationBar {
            listOf(Triple("home", "Inicio", Icons.Default.Home), Triple("plan", "Mantenimiento", Icons.AutoMirrored.Filled.List),
                Triple("history", "Historial", Icons.Default.History), Triple("expenses", "Gastos", Icons.Default.Payments)).forEach { (id, label, icon) ->
                NavigationBarItem(selected = tab == id, onClick = { tab = id; route = ""; recordId = ""; previousRoute = "" }, modifier = Modifier.semantics { contentDescription = label },
                    icon = { Icon(icon, null) }, label = { Text(if (id == "plan") "Mantenim." else label, maxLines = 1, softWrap = false) })
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
                    "visit" -> VisitForm(model, visits.firstOrNull { it.id == recordId }, tasks, expenses, selectedOperations.split("|").filter { it.isNotBlank() }, currentKm, busy, { selectedOperations = ""; go("", ""); tab = "history" }, ::attachments)
                    "reading" -> ReadingForm(model, readings, busy) { back() }
                    "expenses" -> ExpensesPage(expenses, { go("expense") }, { go("expense", it.id) })
                    "expense" -> ExpenseForm(model, expenses.firstOrNull { it.id == recordId }, visits, expenses, busy, { go(""); tab = "expenses" }, ::attachments)
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
                    "inbox" -> InboxPage(inbox) { if (it == "maintenance-summary") { tab = "plan"; route = ""; recordId = "" } else go("task", it) }
                    "settings" -> SettingsPage(model, settings, busy, "preferences")
                    "copies" -> SettingsPage(model, settings, busy, "copies")
                    "references" -> ReferencesPage(tasks) { go("task", it.rule.id) }
                    "secondary" -> SecondaryPage { go(it) }
                    else -> when (tab) {
                        "home" -> HomePage(groups, readings, currentKm, model.today(), { go("reading") }, { selectedOperations = ""; go("visit") }, { go("task", it.representative.rule.id) }, { tab = "plan"; route = "" })
                        "plan" -> PlanPage(groups, tasks, model.today(), currentKm, { go("new-rule") }) { go("task", it.representative.rule.id) }
                        "history" -> HistoryPage(visits, workLines, tasks, notes, documents, { selectedOperations = ""; go("visit") }, { selectedOperations = ""; go("visit", it.id) }, ::attachments)
                        else -> ExpensesPage(expenses, { go("expense") }, { go("expense", it.id) })
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun HomePage(groups: List<MaintenanceGroup>, readings: List<Reading>, currentKm: Int, today: String, reading: () -> Unit, visit: () -> Unit, open: (MaintenanceGroup) -> Unit, plan: () -> Unit) {
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
fun PlanPage(groups: List<MaintenanceGroup>, tasks: List<Task>, today: String, currentKm: Int, add: () -> Unit, open: (MaintenanceGroup) -> Unit) {
    var status by rememberSaveable { mutableStateOf("all") }
    var view by rememberSaveable { mutableStateOf("next") }
    Page {
        Choice("Vista del mantenimiento", view, listOf("next" to "Próximos vencimientos reales", "forecast" to "Previsión de revisiones futuras")) { view = it }
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Crear mantenimiento") }
        if (view == "forecast") { ForecastPage(tasks); return@Page }
        Hint("Cada operación aparece en su próximo límite real. Las revisiones futuras completas están en Previsión.")
        Choice("Mostrar", status, listOf("all" to "Todos", "SOON" to "Próximos", "DUE" to "Pendientes")) { status = it }
        val filtered = groups.filter { status == "all" || it.status == status }
        filtered.forEach { GroupCard(it, currentKm, today) { open(it) } }
        if (filtered.isEmpty()) Hint(if (status == "DUE") "Todo al día. No hay mantenimientos pendientes." else "No hay mantenimientos en esta selección.")
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
            ItemCard(visit.title, "${dateLabel(visit.date)}${if (!visit.dateExact) " (aproximada)" else ""} · ${kmLabel(visit.km)}${if (!visit.kmExact && visit.km != null) " (aproximados)" else ""}${if (!visit.active) " · Anulado" else ""}", { edit(visit) }) {
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
        listOf("settings" to "Ajustes", "documents" to "Manuales y documentos", "references" to "Referencias del mantenimiento", "copies" to "Copias", "inbox" to "Bandeja de avisos").forEach { (id, title) ->
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
