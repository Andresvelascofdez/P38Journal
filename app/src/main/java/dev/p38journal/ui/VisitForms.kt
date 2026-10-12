package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import dev.p38journal.data.*
import dev.p38journal.domain.Task
import java.time.LocalDate

private data class LineDraft(val id: String = freshId(), val title: String = "", val ruleId: String = "", val cost: String = "", val pieces: String = "", val category: String = "maintenance", val completed: Boolean = false)
private data class VisitDetail(val schema: Int = 1, val notes: String = "", val budget: String = "", val lines: List<LineDraft> = emptyList())
private val gson = Gson()
private fun parseKmOrNull(value: String): Int? = runCatching { parseKm(value) }.getOrNull()

@Composable
fun VisitForm(model: JournalViewModel, original: Visit?, tasks: List<Task>, expenses: List<Expense>, preselected: List<String>, currentKm: Int, busy: Boolean, after: () -> Unit, attachments: (String) -> Unit, expense: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: freshId() }
    val detail = remember(original?.id) { original?.notes?.let { runCatching { gson.fromJson(it, VisitDetail::class.java) }.getOrNull() } }
    var date by rememberSaveable { mutableStateOf(original?.date ?: model.today()) }
    val selectedTasks = tasks.filter { it.rule.id in preselected }
    var title by rememberSaveable { mutableStateOf(original?.title ?: if (selectedTasks.size > 1) "Revisión de taller" else selectedTasks.firstOrNull()?.let(::friendlyTitle) ?: "") }
    var km by rememberSaveable { mutableStateOf(if (original != null) original.km?.toString() ?: "" else currentKm.toString()) }
    var exactKm by rememberSaveable { mutableStateOf(original?.kmExact ?: true) }
    var exactDate by rememberSaveable { mutableStateOf(original?.dateExact ?: true) }
    var anchor by rememberSaveable { mutableStateOf(original?.operationalAnchor ?: false) }
    var workshop by rememberSaveable { mutableStateOf(original?.workshop ?: "") }
    var notes by rememberSaveable { mutableStateOf(detail?.notes ?: original?.notes ?: "") }
    var budget by rememberSaveable { mutableStateOf(detail?.budget ?: "") }
    var paid by rememberSaveable { mutableStateOf(moneyInput(original?.paidCents)) }
    var linked by rememberSaveable { mutableStateOf("") }
    var optional by rememberSaveable { mutableStateOf(original != null) }
    var updateOdometer by rememberSaveable { mutableStateOf(false) }
    val readingId = rememberSaveable { freshId() }
    var loaded by rememberSaveable { mutableStateOf(original == null) }
    var originalAllocation by rememberSaveable { mutableStateOf<String?>(null) }
    var lineJson by rememberSaveable {
        mutableStateOf(gson.toJson(selectedTasks.map { LineDraft(title = friendlyTitle(it), ruleId = it.rule.id) }.ifEmpty { listOf(LineDraft()) }))
    }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var voidConfirm by rememberSaveable { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val canonicalPayment = "${original?.paidCents}/${expenses.firstOrNull { it.active && it.visitId == id }?.cents}"
    var loadedPayment by rememberSaveable { mutableStateOf(canonicalPayment) }
    LaunchedEffect(canonicalPayment) {
        if (canonicalPayment != loadedPayment) {
            paid = moneyInput(expenses.firstOrNull { it.active && it.visitId == id }?.cents ?: original?.paidCents)
            loadedPayment = canonicalPayment
        }
    }
    val lines = runCatching { gson.fromJson(lineJson, Array<LineDraft>::class.java).toList() }.getOrDefault(listOf(LineDraft()))
    val completedLines = lines.filter { it.completed }
    fun replace(index: Int, value: LineDraft) { lineJson = gson.toJson(lines.toMutableList().also { it[index] = value }) }
    LaunchedEffect(original?.id) {
        if (original != null && !loaded) {
            val oldLines = model.visitLines(original.id)
            originalAllocation = gson.toJson(oldLines.sortedBy { it.id }.map { "${it.id}:${it.costCents}" })
            val operationIds = model.eventsForVisit(original.id)
            val drafts = oldLines.map { line ->
                val meta = detail?.lines?.firstOrNull { it.id == line.id }
                LineDraft(line.id, line.title, line.ruleId ?: "", moneyInput(line.costCents), line.parts.ifBlank { meta?.pieces ?: "" }, line.category.ifBlank { meta?.category ?: "maintenance" }, true)
            }.toMutableList()
            operationIds.filter { rule -> drafts.none { it.ruleId == rule } }.forEach { rule ->
                drafts.add(LineDraft(title = tasks.firstOrNull { it.rule.id == rule }?.rule?.title ?: "Operación", ruleId = rule, completed = true))
            }
            lineJson = gson.toJson(drafts.ifEmpty { listOf(LineDraft()) })
            loaded = true
        }
    }
    fun validate() {
        if (date.isBlank()) require(original?.manual == true) { "Introduce la fecha del trabajo." }
        else {
            validateDate(date)
            require(LocalDate.parse(date) <= LocalDate.parse(model.today())) { "Un trabajo realizado no puede tener fecha futura." }
        }
        require(title.isNotBlank()) { "Introduce una descripción del trabajo." }
        val actualKm = parseKm(km)
        parseMoney(budget, true)
        val payment = parseMoney(paid, true)
        require(completedLines.isNotEmpty() && completedLines.all { it.title.isNotBlank() }) { "Selecciona al menos un trabajo realizado y escribe su descripción." }
        if (completedLines.any { it.ruleId.isNotBlank() }) require(date.isNotBlank() && exactDate || actualKm != null && exactKm) { "Para vincular mantenimiento, indica una fecha o unos kilómetros exactos que conozcas." }
        require(payment == null || date.isNotBlank() || linked.isNotBlank() || expenses.any { it.active && it.visitId == id }) { "Para un trabajo de fecha desconocida, vincula un gasto real con su propia fecha." }
        val costs = completedLines.map { parseMoney(it.cost, true) }
        val allocation = gson.toJson(completedLines.zip(costs).sortedBy { it.first.id }.map { "${it.first.id}:${it.second}" })
        val unchangedHistoricalAllocation = original != null && payment == original.paidCents && allocation == originalAllocation &&
            expenses.any { it.active && it.visitId == original.id && it.cents == payment }
        if (payment != null && costs.any { it != null } && !unchangedHistoricalAllocation) {
            require(costs.all { it != null } && costs.filterNotNull().fold(0L, Math::addExact) == payment) { "El reparto entre líneas debe informar todos los importes y sumar el importe pagado." }
        }
        if (linked.isNotEmpty()) require(expenses.first { it.id == linked }.cents == payment) { "El importe pagado debe coincidir con el gasto que vas a vincular." }
    }
    Page {
        if (!loaded) { CircularProgressIndicator(); return@Page }
        if (original?.active == false) Hint("Trabajo anulado. Guardar una corrección lo reactivará y quedará auditado.")
        Field(title, { title = it }, "Descripción del trabajo", "visit_title")
        Field(date, { date = it; if (original?.manual == true && original.date.isBlank() && it.isNotBlank()) exactDate = true }, if (original?.manual == true) "Fecha (AAAA-MM-DD, si se conoce)" else "Fecha (AAAA-MM-DD)", "visit_date")
        if (original?.manual == true) Hint("Puedes conservar la fecha desconocida. Para vincular un mantenimiento aporta fecha o kilómetros exactos; se usará solo la dimensión conocida.")
        Field(km, { km = it }, "Kilómetros del trabajo (opcional)", "visit_km", numeric = true)
        if (date == model.today() && exactKm && (parseKmOrNull(km) ?: -1) >= currentKm) Check("Actualizar también el odómetro con estos kilómetros", updateOdometer) { updateOdometer = it }
        Heading("Trabajos realizados")
        Hint("Marca solo lo que se ha realizado. Las operaciones sin marcar conservan su próximo mantenimiento.")
        TextButton(onClick = { lineJson = gson.toJson(lines.map { it.copy(completed = true) }) }) { Text("Seleccionar todas") }
        TextButton(onClick = { lineJson = gson.toJson(lines.map { it.copy(completed = false) }) }) { Text("Desmarcar todas") }
        lines.forEachIndexed { index, line ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Check(if (line.title.isBlank()) "Trabajo ${index + 1} realizado" else line.title, line.completed, "line_completed_$index") { replace(index, line.copy(completed = it)) }
                    if (line.ruleId.isBlank() || optional || line.completed) Field(line.title, { replace(index, line.copy(title = it)) }, "Trabajo realizado", "line_title_$index")
                    if (optional || line.ruleId.isBlank()) Choice("Operación realizada", line.ruleId, listOf("" to "Sin operación de mantenimiento") + tasks.filter { it.rule.scheduleVisibility in setOf("maintenance", "service") || it.rule.id == line.ruleId }.map { it.rule.id to friendlyTitle(it) }) { ruleId ->
                        replace(index, line.copy(ruleId = ruleId, title = line.title.ifBlank { tasks.firstOrNull { it.rule.id == ruleId }?.rule?.title ?: "" }))
                    }
                    if (optional) {
                    Choice("Categoría", line.category, listOf("maintenance" to "Mantenimiento", "repair" to "Reparación", "inspection" to "Revisión", "other" to "Otro")) { replace(index, line.copy(category = it)) }
                    Field(line.pieces, { replace(index, line.copy(pieces = it)) }, "Piezas, referencias y cantidades (opcional)", multiline = true)
                    Field(line.cost, { replace(index, line.copy(cost = it)) }, "Reparto del coste EUR (opcional)", numeric = true)
                    }
                    if (lines.size > 1) TextButton(onClick = { lineJson = gson.toJson(lines.filterIndexed { i, _ -> i != index }) }) { Text("Quitar línea") }
                }
            }
        }
        OutlinedButton(onClick = { lineJson = gson.toJson(lines + LineDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("Añadir línea de trabajo") }
        Field(paid, { paid = it }, "Importe pagado EUR (opcional)", "visit_paid", numeric = true)
        TextButton(onClick = { optional = !optional }) { Text(if (optional) "Ocultar datos opcionales" else "Taller, notas y más opciones") }
        if (optional) {
        Field(workshop, { workshop = it }, "Taller o contacto (opcional)")
        Field(notes, { notes = it }, "Notas", multiline = true)
        if (date.isNotBlank()) Check("Fecha exacta", exactDate) { exactDate = it }
        Check("Kilómetros exactos", exactKm) { exactKm = it }
        Field(budget, { budget = it }, "Presupuesto EUR (no se suma a gastos)", numeric = true)
        Hint("Vacío significa importe desconocido. La factura se cuenta una vez.")
        val candidates = expenses.filter { it.active && it.fuelId == null && (it.visitId == null || it.visitId == id) }
        Choice("Pago ya registrado", linked, listOf("" to "Crear o mantener el pago de esta visita") + candidates.map { it.id to "${it.concept} · ${money(it.cents)}" }) { expenseId ->
            linked = expenseId
            candidates.firstOrNull { it.id == expenseId }?.let { paid = moneyInput(it.cents) }
        }
        expenses.firstOrNull { it.active && it.visitId == id }?.let { payment -> OutlinedButton(onClick = { expense(payment.id) }, modifier = Modifier.fillMaxWidth()) { Text("Consultar gasto vinculado") } }
        }
        if (original != null) OutlinedButton(onClick = { attachments(id) }, modifier = Modifier.fillMaxWidth()) { Text("Fotos y documentos de este trabajo") }
        else Hint("Después de guardar, abre el trabajo en Historial para añadir fotos o PDF.")
        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { runCatching { validate() }.onSuccess { validation = null; confirm = true }.onFailure { validation = it.message ?: "Revisa los datos." } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar trabajo") }
        if (original?.active == true) OutlinedButton(onClick = { voidConfirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Anular trabajo") }
    }
    if (confirm) Confirm("Confirmar trabajo realizado", "${dateLabel(date)} · ${kmLabel(parseKm(km))}\n\n${completedLines.joinToString("\n") { "• ${it.title}" }}\n\nSolo se actualizarán estas operaciones${if (updateOdometer && date == model.today() && exactKm && (parseKmOrNull(km) ?: -1) >= currentKm) " y el odómetro" else ""}.", "Sí, se ha realizado", { confirm = false }) {
        confirm = false
        model.saveVisit(
            Visit(id = id, date = date, km = parseKm(km), kmExact = exactKm, title = title.trim(), workshop = workshop.trim(),
                notes = gson.toJson(VisitDetail(notes = notes, budget = budget, lines = lines)), paidCents = parseMoney(paid, true), active = true, dateExact = date.isNotBlank() && exactDate, operationalAnchor = anchor,
                status = "done", manual = original?.manual ?: false, targetDate = original?.targetDate, estimatedCents = original?.estimatedCents),
            completedLines.map { WorkLine(id = it.id, visitId = id, title = it.title.trim(), ruleId = it.ruleId.ifBlank { null }, costCents = parseMoney(it.cost, true), category = it.category, parts = it.pieces) },
            completedLines.map { it.ruleId }.filter { it.isNotBlank() }.distinct(), linked.ifBlank { null },
            if (updateOdometer && date == model.today() && exactKm && (parseKmOrNull(km) ?: -1) >= currentKm) Reading(id = readingId, date = date, km = requireNotNull(parseKm(km))) else null, after)
    }
    if (voidConfirm) Confirm("Anular trabajo", "Se conservará la auditoría, se retirará su pago del total y se recalcularán las operaciones satisfechas.", "Anular", { voidConfirm = false }) { voidConfirm = false; model.voidVisit(id, after) }
}

@Composable
fun ReadingForm(model: JournalViewModel, readings: List<Reading>, busy: Boolean, after: () -> Unit) {
    val id = rememberSaveable { freshId() }
    var date by rememberSaveable { mutableStateOf(model.today()) }
    var km by rememberSaveable { mutableStateOf("") }
    var correction by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        Heading("Registrar lectura real")
        Hint("Introduce lo que indica el odómetro. La lectura actualiza los próximos avisos; no registra trabajos realizados ni cambia la referencia inicial del plan.")
        Field(km, { km = it }, "Kilómetros leídos", "reading_km", numeric = true)
        Field(date, { date = it }, "Fecha (AAAA-MM-DD)", "reading_date")
        Check("Es una corrección explícita del odómetro", correction) { correction = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                val value = requireNotNull(parseKm(km)) { "Introduce los kilómetros leídos." }
                val validDate = validateDate(date)
                require(LocalDate.parse(validDate) <= LocalDate.parse(model.today())) { "La lectura no puede tener una fecha futura." }
                Reading(id = id, date = validDate, km = value)
            }.onSuccess { model.saveReading(it, correction, after) }.onFailure { error = it.message ?: "Revisa la lectura." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar lectura") }
        Heading("Lecturas anteriores")
        readings.sortedByDescending { it.date }.forEach { Text("${dateLabel(it.date)} · ${kmLabel(it.km)}") }
    }
}
