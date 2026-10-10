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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val visits by model.visits.collectAsStateWithLifecycle()
    val readings by model.readings.collectAsStateWithLifecycle()
    val notes by model.notes.collectAsStateWithLifecycle()
    val expenses by model.expenses.collectAsStateWithLifecycle()
    val documents by model.documents.collectAsStateWithLifecycle()
    val inbox by model.inbox.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("home") }
    var route by rememberSaveable { mutableStateOf("") }
    var recordId by rememberSaveable { mutableStateOf("") }
    var previousRoute by rememberSaveable { mutableStateOf("") }
    var previousId by rememberSaveable { mutableStateOf("") }
    var libraryLink by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    fun go(to: String, id: String = "") {
        val destinationId = if (id.isBlank() && to in setOf("visit", "expense", "fault", "part")) freshId() else id
        if (to in setOf("visit", "expense", "fault", "part", "appointment")) screenStates.removeState("$tab/$to/$destinationId")
        if (to == "documents") libraryLink = id
        route = to; recordId = destinationId
    }
    fun back() {
        if (route == "viewer") { route = "documents"; recordId = libraryLink; return }
        if (route in setOf("appointment", "override", "profile")) { route = "task"; return }
        if (route == "documents" && previousRoute.isNotEmpty()) {
            route = previousRoute; recordId = previousId; previousRoute = ""; previousId = ""; return
        }
        route = when (route) { "expense" -> "expenses"; "fault", "part" -> "${route}s"; else -> "" }
        recordId = ""
    }
    fun attachments(id: String) { previousRoute = route; previousId = recordId; go("documents", id) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    LaunchedEffect(targetRule, ready) {
        if (ready && targetRule != null) {
            tab = "home"
            when (targetRule) { "odometer" -> go("reading"); "" -> go("inbox"); else -> go("task", targetRule) }
            clearTarget()
        }
    }
    BackHandler(route.isNotEmpty()) { back() }
    val title = when (route) {
        "task" -> "Detalle de mantenimiento"; "visit" -> "Registrar trabajo"; "reading" -> "Kilometraje"
        "expenses" -> "Gastos"; "expense" -> "Gasto"; "faults" -> "Averías"; "fault" -> "Avería"
        "parts" -> "Piezas"; "part" -> "Pieza"; "documents" -> "Manuales y documentos"; "viewer" -> "Documento"
        "inbox" -> "Bandeja de avisos"; "settings" -> "Ajustes y copias"; else -> "P38 Journal"
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
        if (route.isNotEmpty()) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
    }) }, bottomBar = {
        NavigationBar {
            listOf(Triple("home", "Inicio", Icons.Default.Home), Triple("plan", "Plan", Icons.AutoMirrored.Filled.List),
                Triple("history", "Historial", Icons.Default.History), Triple("more", "Más", Icons.Default.MoreHoriz)).forEach { (id, label, icon) ->
                NavigationBarItem(selected = tab == id, onClick = { tab = id; route = ""; recordId = ""; previousRoute = "" },
                    icon = { Icon(icon, null) }, label = { Text(label) })
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
                    "task" -> TaskDetail(tasks.firstOrNull { it.rule.id == recordId }, notes, { go("visit", recordId) }, { go("appointment", recordId) }, { go("override", recordId) }, { go("profile", recordId) }) { eventId ->
                        model.action("Referencia operativa adoptada") { model.repository.adoptOperationalAnchor(eventId) }
                    }
                    "profile" -> ConditionalProfileForm(model, tasks.firstOrNull { it.rule.id == recordId }, busy) { go("task", if (recordId.endsWith("-active")) recordId else "$recordId-active") }
                    "override" -> OverrideForm(model, tasks.firstOrNull { it.rule.id == recordId }, busy) { go("task", recordId) }
                    "appointment" -> AppointmentForm(model, tasks.firstOrNull { it.rule.id == recordId }) { go("task", recordId) }
                    "visit" -> VisitForm(model, visits.firstOrNull { it.id == recordId }, tasks, expenses, recordId.takeIf { id -> tasks.any { it.rule.id == id } }, busy, { go("", ""); tab = "history" }, ::attachments)
                    "reading" -> ReadingForm(model, readings, busy) { back() }
                    "expenses" -> ExpensesPage(expenses, { go("expense") }, { go("expense", it.id) })
                    "expense" -> ExpenseForm(model, expenses.firstOrNull { it.id == recordId }, visits, expenses, busy, { go("expenses") }, ::attachments)
                    "faults", "parts" -> NotesPage(route.removeSuffix("s"), notes, { go(route.removeSuffix("s")) }, { go(route.removeSuffix("s"), it.id) })
                    "fault", "part" -> NoteForm(model, route, notes.firstOrNull { it.id == recordId }, visits, notes, busy, { go("${route}s") }, ::attachments)
                    "documents" -> DocumentsPage(model, documents, recordId) { go("viewer", it.id) }
                    "viewer" -> documents.firstOrNull { it.id == recordId }?.let { DocumentViewer(it) { go("documents", libraryLink) } }
                    "inbox" -> InboxPage(inbox) { go("task", it) }
                    "settings" -> SettingsPage(model, settings, busy)
                    else -> when (tab) {
                        "home" -> HomePage(tasks, readings, notes, { go("reading") }, { go("visit") }, { go("task", it.rule.id) })
                        "plan" -> PlanPage(tasks, model.today(), readings.firstOrNull()?.km) { go("task", it.rule.id) }
                        "history" -> HistoryPage(visits, notes, documents, { go("visit") }, { go("visit", it.id) }, ::attachments)
                        else -> MorePage { go(it) }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun HomePage(tasks: List<Task>, readings: List<Reading>, notes: List<Note>, reading: () -> Unit, visit: () -> Unit, task: (Task) -> Unit) {
    Page {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Range Rover P38", style = MaterialTheme.typography.headlineSmall)
                Hint("Autobiography · 4.6 V8 · Automático · 2000")
                val current = readings.firstOrNull()
                Text(current?.let { kmLabel(it.km) } ?: "Lectura actual sin registrar", style = MaterialTheme.typography.titleMedium)
                Hint(current?.let { "Leído el ${dateLabel(it.date)}" } ?: "175.000 km en la compra; no es la lectura actual.")
                TextButton(onClick = reading) { Text("Actualizar kilómetros") }
            }
        }
        Button(onClick = visit, modifier = Modifier.fillMaxWidth()) { Text("Registrar trabajo") }
        listOf("Hacer ahora" to setOf("INITIAL", "DUE"), "Próximamente" to setOf("SOON"), "Programado" to setOf("PLANNED")).forEach { (title, statuses) ->
            Heading(title)
            val group = tasks.filter { it.status in statuses }
            if (group.isEmpty()) Hint("No hay tareas en este grupo.")
            group.forEach { TaskCard(it) { task(it) } }
        }
        val appointments = notes.filter { it.kind == "appointment" }
        if (appointments.isNotEmpty()) {
            Heading("Citas de taller")
            appointments.sortedBy { it.date }.forEach { Hint("${dateLabel(it.date)} · ${it.title}. Una cita no completa el mantenimiento.") }
        }
    }
}

@Composable
fun TaskCard(task: Task, open: () -> Unit) {
    ItemCard(if (task.status == "INITIAL") task.rule.initialAction else task.rule.title, "${systemLabel(task.rule.system)} · ${statusLabel(task.status)}", open) {
        if (task.dueDate != null) Text("Fecha límite: ${dateLabel(task.dueDate)}")
        if (task.dueKm != null) Text("Kilómetros límite: ${kmLabel(task.dueKm)}")
        if (task.reasons.isNotEmpty()) Hint(task.reasons.joinToString(" · "))
    }
}

@Composable
fun PlanPage(tasks: List<Task>, today: String, currentKm: Int?, open: (Task) -> Unit) {
    var system by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var horizon by rememberSaveable { mutableStateOf("") }
    Page {
        Field(search, { search = it }, "Buscar tarea")
        Choice("Sistema", system, listOf("" to "Todos los sistemas") + tasks.map { it.rule.system }.distinct().sorted().map { it to systemLabel(it) }) { system = it }
        Choice("Estado", status, listOf("" to "Todos los estados", "INITIAL" to "Mantenimiento inicial", "DUE" to "Vencido", "SOON" to "Próximamente", "PLANNED" to "Programado")) { status = it }
        Choice("Horizonte", horizon, listOf("" to "Sin límite", "30" to "Hasta 30 días", "90" to "Hasta 90 días", "1000km" to "Hasta 1.000 km desde la lectura real")) { horizon = it }
        if (horizon == "1000km" && currentKm == null) Hint("Registra la lectura actual para filtrar por kilómetros.")
        val filtered = tasks.filter {
            val withinHorizon = when (horizon) {
                "30", "90" -> it.status in setOf("INITIAL", "DUE") || it.dueDate?.let { date -> !LocalDate.parse(date).isAfter(LocalDate.parse(today).plusDays(horizon.toLong())) } == true
                "1000km" -> currentKm != null && (it.status == "INITIAL" || it.dueKm?.let { limit -> limit.toLong() <= currentKm.toLong() + 1000 } == true)
                else -> true
            }
            withinHorizon && (system.isEmpty() || it.rule.system == system) && (status.isEmpty() || it.status == status) && (it.rule.title + it.rule.initialAction).contains(search, true)
        }
        Hint("${filtered.size} tareas")
        filtered.forEach { TaskCard(it) { open(it) } }
        if (filtered.isEmpty()) Hint("No hay tareas que coincidan. Cambia los filtros.")
    }
}

@Composable
fun TaskDetail(task: Task?, notes: List<Note>, record: () -> Unit, appointment: () -> Unit, customize: () -> Unit, profile: () -> Unit, adopt: (String) -> Unit) {
    val context = LocalContext.current
    var confirmAnchor by rememberSaveable { mutableStateOf(false) }
    Page {
        if (task == null) { Hint("Esta tarea ya no está disponible."); return@Page }
        Heading(task.rule.title)
        Text("${systemLabel(task.rule.system)} · ${statusLabel(task.status)}")
        Heading("Acción")
        Text(if (task.status == "INITIAL") task.rule.initialAction else task.rule.title)
        task.reasons.forEach { Text(it) }
        if (task.rule.condition.isNotBlank()) { Heading("Aplicabilidad y condición"); Text(task.rule.condition) }
        Heading("Próximos límites")
        Text(task.dueDate?.let { dateLabel(it) } ?: "Sin fecha de referencia exacta")
        Text(task.dueKm?.let { kmLabel(it) } ?: "Sin kilómetros de referencia exactos")
        Heading("Última operación válida")
        task.lastEvent?.let { Text("${dateLabel(it.date)}${if (!it.dateExact && it.date != null) " (aproximada)" else ""} · ${kmLabel(it.km)}${if (!it.kmExact && it.km != null) " (aproximados)" else ""}"); Hint("Solo se usan referencias exactas para cada límite.") }
            ?: Hint("Incluido en la revisión inicial porque no consta una intervención posterior válida.")
        Heading("Fuente")
        Text(task.rule.source)
        Hint("Regla revisión ${task.rule.revision}. Páginas de PDF e impresas se distinguen en la referencia.")
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.rule.sourceUrl))) }.onFailure {
                android.widget.Toast.makeText(context, "No hay una aplicación disponible para abrir el enlace.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, enabled = task.rule.sourceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Abrir fuente del fabricante") }
        Button(onClick = record, modifier = Modifier.fillMaxWidth()) { Text("Registrar operación realizada") }
        OutlinedButton(onClick = appointment, modifier = Modifier.fillMaxWidth()) { Text("Programar cita de taller") }
        if (task.rule.recurrence == "component" && !task.rule.id.endsWith("-active")) OutlinedButton(onClick = customize, modifier = Modifier.fillMaxWidth()) { Text("Personalizar intervalo") }
        if (task.rule.id.endsWith("-active") || ConditionalProfiles.proposed(task.rule) != null) {
            OutlinedButton(onClick = profile, modifier = Modifier.fillMaxWidth()) { Text(if (task.rule.id.endsWith("-active")) "Gestionar perfil condicionado" else "Confirmar aplicabilidad y activar intervalo") }
        }
        val event = task.lastEvent
        if (task.rule.recurrence in setOf("phase", "phase_b", "milestone") && event != null && !event.operationalAnchor) {
            OutlinedButton(onClick = { confirmAnchor = true }, modifier = Modifier.fillMaxWidth()) { Text("Adoptar operación como referencia del próximo ciclo") }
        }
        notes.filter { it.kind == "appointment" && it.relatedId == task.rule.id }.forEach { Hint("Cita: ${dateLabel(it.date)} · ${it.body}") }
    }
    if (confirmAnchor && task?.lastEvent != null) Confirm("Adoptar referencia operativa", "La operación registrada será la referencia del próximo ciclo de esta tarea (${task.rule.months?.let { "$it meses" } ?: "sin intervalo temporal"}; ${task.rule.km?.let(::kmLabel) ?: "sin intervalo kilométrico"}). Esta elección no demuestra cuál era la fase original del fabricante ni completa otras operaciones.", "Adoptar", { confirmAnchor = false }) {
        confirmAnchor = false; adopt(task.lastEvent.id)
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
fun HistoryPage(visits: List<Visit>, notes: List<Note>, documents: List<Document>, add: () -> Unit, edit: (Visit) -> Unit, attachments: (String) -> Unit) {
    Page {
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Registrar trabajo") }
        Heading("Trabajos registrados")
        if (visits.isEmpty()) Hint("Aquí se conservarán los trabajos que registres.")
        visits.sortedByDescending { it.date }.forEach { visit ->
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
fun MorePage(go: (String) -> Unit) {
    Page {
        listOf("faults" to "Averías", "documents" to "Manuales y documentos", "expenses" to "Gastos", "parts" to "Piezas", "inbox" to "Bandeja de avisos", "settings" to "Ajustes y copias").forEach { (id, title) ->
            ItemCard(title, onClick = { go(id) })
        }
        Hint("Datos privados en este teléfono. Las funciones principales trabajan sin Internet.")
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
