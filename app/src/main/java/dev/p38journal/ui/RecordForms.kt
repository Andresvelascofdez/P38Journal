package dev.p38journal.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import dev.p38journal.data.*

private val categories = listOf("workshop" to "Taller", "extras" to "Extras", "fuel" to "Gasolina", "other" to "Otros")

@Composable
fun ExpensesPage(expenses: List<Expense>, add: () -> Unit, edit: (Expense) -> Unit) {
    val active = expenses.filter { it.active }
    Page {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lo que llevo gastado", style = MaterialTheme.typography.titleMedium)
                Text(money(active.fold(0L) { sum, expense -> Math.addExact(sum, expense.cents) }), style = MaterialTheme.typography.headlineMedium)
                Hint("Importes reales registrados. Los pagos de visitas se cuentan una vez.")
            }
        }
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Añadir gasto") }
        if (expenses.isEmpty()) Hint("Registra un pago de taller, extra, gasolina u otro gasto.")
        expenses.sortedByDescending { it.date }.forEach { expense ->
            ItemCard(expense.concept, "${dateLabel(expense.date)} · ${categories.firstOrNull { it.first == expense.category }?.second ?: expense.category}", { edit(expense) }) {
                Text(money(expense.cents), style = MaterialTheme.typography.titleMedium)
                if (!expense.active) Hint("Anulado; no incluido en el total")
                if (expense.visitId != null) Hint("Pago vinculado a una visita")
            }
        }
    }
}

@Composable
fun ExpenseForm(model: JournalViewModel, original: Expense?, visits: List<Visit>, expenses: List<Expense>, busy: Boolean, after: () -> Unit, attachments: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: freshId() }
    var date by rememberSaveable { mutableStateOf(original?.date ?: model.today()) }
    var concept by rememberSaveable { mutableStateOf(original?.concept ?: "") }
    var amount by rememberSaveable { mutableStateOf(moneyInput(original?.cents)) }
    var category by rememberSaveable { mutableStateOf(original?.category ?: "other") }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var visit by rememberSaveable { mutableStateOf(original?.visitId ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Page {
        Field(amount, { amount = it }, "Importe EUR", "expense_amount", numeric = true)
        Field(concept, { concept = it }, "Concepto", "expense_concept")
        Choice("Categoría", category, categories) { category = it }
        Field(date, { date = it }, "Fecha (AAAA-MM-DD)", "expense_date")
        Field(notes, { notes = it }, "Notas (opcional)", multiline = true)
        val availableVisits = visits.filter { it.active && expenses.none { expense -> expense.visitId == it.id && expense.id != original?.id } }
        Choice("Vincular a visita (opcional)", visit, listOf("" to "Gasto independiente") + availableVisits.map { it.id to "${dateLabel(it.date)} · ${it.title}" }) { visit = it }
        if (visit.isNotEmpty()) Hint("El pago se contará una vez como gasto de esta visita. No crea ni completa operaciones.")
        Hint("Si la visita ya tiene un pago, abre el trabajo en Historial para vincular allí este gasto existente.")
        if (original != null) OutlinedButton(onClick = { attachments(id) }, modifier = Modifier.fillMaxWidth()) { Text("Recibo o foto") }
        else Hint("Guarda el gasto y vuelve a abrirlo para añadir un recibo o una foto, si quieres.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                require(concept.isNotBlank()) { "Introduce el concepto." }
                Expense(id = id, date = validateDate(date), cents = requireNotNull(parseMoney(amount)), concept = concept.trim(), category = category, notes = notes, visitId = visit.ifBlank { null }, active = true)
            }.onSuccess { model.saveExpense(it, after) }.onFailure { error = it.message ?: "Revisa el importe y la fecha." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar gasto") }
        if (original?.active == true) OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Eliminar gasto") }
    }
    if (confirm) Confirm("Eliminar gasto", "El pago se anulará y dejará de formar parte del total. Se conservará la auditoría.", "Eliminar", { confirm = false }) { confirm = false; model.voidExpense(id, after) }
}

private data class StructuredNote(
    val schema: Int = 1,
    val symptoms: String = "", val hypotheses: String = "", val diagnosis: String = "", val system: String = "general",
    val priority: String = "normal", val km: String = "", val workshop: String = "", val budget: String = "", val followup: String = "",
    val reference: String = "", val manufacturer: String = "", val supplier: String = "", val url: String = "", val quantity: String = "1",
    val cost: String = "", val compatibility: String = "pending", val evidence: String = ""
)

@Composable
fun NotesPage(kind: String, notes: List<Note>, add: () -> Unit, edit: (Note) -> Unit) {
    val entries = notes.filter { it.kind == kind }
    Page {
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text(if (kind == "fault") "Registrar avería" else "Añadir pieza") }
        if (entries.isEmpty()) Hint(if (kind == "fault") "Registra un síntoma que hayas observado. Las notas antiguas no se consideran averías activas." else "Guarda referencias y evidencia de compatibilidad antes de comprar una pieza.")
        entries.sortedByDescending { it.date }.forEach { note ->
            val structured = remember(note.body) { runCatching { Gson().fromJson(note.body, StructuredNote::class.java) }.getOrNull() }
            ItemCard(note.title, "${dateLabel(note.date)} · ${noteStatus(note.status)}", { edit(note) }) {
                if (kind == "fault") structured?.symptoms?.takeIf { it.isNotBlank() }?.let { Text(it) }
                else structured?.reference?.takeIf { it.isNotBlank() }?.let { Text("Referencia: $it") }
            }
        }
    }
}

private fun noteStatus(status: String): String = mapOf("open" to "Abierta", "scheduled" to "Programada", "workshop" to "En taller", "resolved" to "Resuelta", "considered" to "Considerada", "ordered" to "Pedida", "received" to "Recibida", "installed" to "Montada")[status] ?: status

@Composable
fun NoteForm(model: JournalViewModel, kind: String, original: Note?, visits: List<Visit>, allNotes: List<Note>, busy: Boolean, after: () -> Unit, attachments: (String) -> Unit) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val id = rememberSaveable { original?.id ?: freshId() }
    val initial = remember(original?.id) { original?.body?.let { runCatching { gson.fromJson(it, StructuredNote::class.java) }.getOrNull() } ?: StructuredNote() }
    var json by rememberSaveable { mutableStateOf(gson.toJson(initial)) }
    val detail = gson.fromJson(json, StructuredNote::class.java)
    fun update(value: StructuredNote) { json = gson.toJson(value) }
    var title by rememberSaveable { mutableStateOf(original?.title ?: "") }
    var date by rememberSaveable { mutableStateOf(original?.date ?: model.today()) }
    var status by rememberSaveable { mutableStateOf(original?.status ?: if (kind == "fault") "open" else "considered") }
    var related by rememberSaveable { mutableStateOf(original?.relatedId ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    Page {
        Field(title, { title = it }, if (kind == "fault") "Título de la avería" else "Nombre de la pieza", "note_title")
        Field(date, { date = it }, "Fecha (AAAA-MM-DD)")
        if (kind == "fault") {
            Field(detail.symptoms, { update(detail.copy(symptoms = it)) }, "Síntomas observados", multiline = true)
            Choice("Sistema", detail.system, listOf("general", "engine", "cooling", "transmission", "brakes", "suspension", "electrical", "body", "steering", "tyres").map { it to systemLabel(it) }) { update(detail.copy(system = it)) }
            Field(detail.km, { update(detail.copy(km = it)) }, "Kilómetros (opcional)", numeric = true)
            Field(detail.hypotheses, { update(detail.copy(hypotheses = it)) }, "Hipótesis (sin confirmar)", multiline = true)
            Field(detail.diagnosis, { update(detail.copy(diagnosis = it)) }, "Diagnóstico confirmado (si existe)", multiline = true)
            Choice("Prioridad", detail.priority, listOf("normal" to "Normal", "high" to "Alta", "urgent" to "Seguridad: revisión profesional prioritaria")) { update(detail.copy(priority = it)) }
            Choice("Estado", status, listOf("open" to "Abierta", "scheduled" to "Programada", "workshop" to "En taller", "resolved" to "Resuelta")) { status = it }
            Field(detail.workshop, { update(detail.copy(workshop = it)) }, "Taller (opcional)")
            Field(detail.budget, { update(detail.copy(budget = it)) }, "Presupuesto EUR (opcional)", numeric = true)
            Field(detail.followup, { update(detail.copy(followup = it)) }, "Seguimiento", multiline = true)
        } else {
            Field(detail.reference, { update(detail.copy(reference = it)) }, "Referencia")
            Field(detail.manufacturer, { update(detail.copy(manufacturer = it)) }, "Fabricante (opcional)")
            Field(detail.supplier, { update(detail.copy(supplier = it)) }, "Proveedor (opcional)")
            Field(detail.url, { update(detail.copy(url = it)) }, "Enlace (opcional)")
            if (detail.url.isNotBlank()) OutlinedButton(onClick = {
                runCatching {
                    val uri = Uri.parse(detail.url.trim())
                    require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) { "Introduce un enlace web válido http o https." }
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }.onFailure { model.announce(it.message ?: "No se pudo abrir el enlace.") }
            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir enlace del proveedor") }
            Field(detail.quantity, { update(detail.copy(quantity = it)) }, "Unidades", numeric = true)
            Field(detail.cost, { update(detail.copy(cost = it)) }, "Coste EUR (opcional)", numeric = true)
            Choice("Estado", status, listOf("considered" to "Considerada", "ordered" to "Pedida", "received" to "Recibida", "installed" to "Montada")) { status = it }
            Choice("Compatibilidad", detail.compatibility, listOf("pending" to "Pendiente de comprobar", "confirmed" to "Conocida con evidencia")) { update(detail.copy(compatibility = it)) }
            Field(detail.evidence, { update(detail.copy(evidence = it)) }, "Evidencia de compatibilidad / versión / VIN", multiline = true)
            Hint("El coste de una pieza no se añade a Gastos. Registra el pago cuando se produzca.")
        }
        val links = visits.filter { it.active }.map { it.id to "Trabajo: ${it.title}" } + if (kind == "part") allNotes.filter { it.kind == "fault" }.map { it.id to "Avería: ${it.title}" } else emptyList()
        Choice(if (kind == "fault") "Reparación relacionada (opcional)" else "Trabajo o avería relacionados (opcional)", related, listOf("" to "Sin vínculo") + links) { related = it }
        if (original != null) OutlinedButton(onClick = { attachments(id) }, modifier = Modifier.fillMaxWidth()) { Text("Fotos y documentos") }
        else Hint("Después de guardar puedes abrir el registro y añadir fotos o documentos.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                require(title.isNotBlank()) { "Escribe un título." }
                validateDate(date)
                if (kind == "fault") {
                    require(detail.symptoms.isNotBlank()) { "Describe el síntoma observado." }
                    parseKm(detail.km); parseMoney(detail.budget, true)
                } else {
                    require(detail.quantity.toIntOrNull()?.let { it > 0 } == true) { "Introduce una cantidad entera mayor que cero." }
                    parseMoney(detail.cost, true)
                    require(detail.compatibility != "confirmed" || detail.evidence.isNotBlank()) { "Indica la evidencia para confirmar compatibilidad." }
                }
                Note(id = id, kind = kind, title = title.trim(), body = json, status = status, date = date, relatedId = related.ifBlank { null })
            }.onSuccess { model.saveNote(it, after) }.onFailure { error = it.message ?: "Revisa los datos." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        if (original != null) OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Eliminar registro") }
    }
    if (confirm) Confirm("Eliminar registro", "Se eliminará este registro y quedará una entrada de auditoría. Los documentos importados se conservarán en la biblioteca.", "Eliminar", { confirm = false }) { confirm = false; model.deleteNote(id, after) }
}
