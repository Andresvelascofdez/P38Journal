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

private data class LineDraft(val id: String = freshId(), val title: String = "", val ruleId: String = "", val cost: String = "", val pieces: String = "", val category: String = "maintenance")
private data class VisitDetail(val schema: Int = 1, val notes: String = "", val budget: String = "", val lines: List<LineDraft> = emptyList())
private val gson = Gson()

@Composable
fun VisitForm(model: JournalViewModel, original: Visit?, tasks: List<Task>, expenses: List<Expense>, preselected: String?, busy: Boolean, after: () -> Unit, attachments: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: freshId() }
    val detail = remember(original?.id) { original?.notes?.let { runCatching { gson.fromJson(it, VisitDetail::class.java) }.getOrNull() } }
    var date by rememberSaveable { mutableStateOf(original?.date ?: model.today()) }
    var title by rememberSaveable { mutableStateOf(original?.title ?: "") }
    var km by rememberSaveable { mutableStateOf(original?.km?.toString() ?: "") }
    var exactKm by rememberSaveable { mutableStateOf(original?.kmExact ?: true) }
    var exactDate by rememberSaveable { mutableStateOf(original?.dateExact ?: true) }
    var anchor by rememberSaveable { mutableStateOf(original?.operationalAnchor ?: false) }
    var workshop by rememberSaveable { mutableStateOf(original?.workshop ?: "") }
    var notes by rememberSaveable { mutableStateOf(detail?.notes ?: original?.notes ?: "") }
    var budget by rememberSaveable { mutableStateOf(detail?.budget ?: "") }
    var paid by rememberSaveable { mutableStateOf(moneyInput(original?.paidCents)) }
    var linked by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(original == null) }
    var lineJson by rememberSaveable {
        val rule = tasks.firstOrNull { it.rule.id == preselected }?.rule
        mutableStateOf(gson.toJson(listOf(LineDraft(title = rule?.title ?: "", ruleId = preselected ?: ""))))
    }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var voidConfirm by rememberSaveable { mutableStateOf(false) }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    val lines = runCatching { gson.fromJson(lineJson, Array<LineDraft>::class.java).toList() }.getOrDefault(listOf(LineDraft()))
    fun replace(index: Int, value: LineDraft) { lineJson = gson.toJson(lines.toMutableList().also { it[index] = value }) }
    LaunchedEffect(original?.id) {
        if (original != null && !loaded) {
            val oldLines = model.visitLines(original.id)
            val operationIds = model.eventsForVisit(original.id)
            val drafts = oldLines.map { line ->
                val meta = detail?.lines?.firstOrNull { it.id == line.id }
                LineDraft(line.id, line.title, line.ruleId ?: "", moneyInput(line.costCents), line.parts.ifBlank { meta?.pieces ?: "" }, line.category.ifBlank { meta?.category ?: "maintenance" })
            }.toMutableList()
            operationIds.filter { rule -> drafts.none { it.ruleId == rule } }.forEach { rule ->
                drafts.add(LineDraft(title = tasks.firstOrNull { it.rule.id == rule }?.rule?.title ?: "Operación", ruleId = rule))
            }
            lineJson = gson.toJson(drafts.ifEmpty { listOf(LineDraft()) })
            loaded = true
        }
    }
    fun validate() {
        validateDate(date)
        require(LocalDate.parse(date) <= LocalDate.parse(model.today())) { "Un trabajo realizado no puede tener fecha futura. Programa una cita desde la tarea." }
        require(title.isNotBlank()) { "Introduce una descripción del trabajo." }
        parseKm(km)
        parseMoney(budget, true)
        val payment = parseMoney(paid, true)
        require(lines.isNotEmpty() && lines.all { it.title.isNotBlank() }) { "Escribe la descripción de cada línea de trabajo." }
        val costs = lines.map { parseMoney(it.cost, true) }
        if (payment != null && costs.any { it != null }) {
            require(costs.all { it != null } && costs.filterNotNull().fold(0L, Math::addExact) == payment) { "El reparto entre líneas debe informar todos los importes y sumar el importe pagado." }
        }
        if (linked.isNotEmpty()) require(expenses.first { it.id == linked }.cents == payment) { "El importe pagado debe coincidir con el gasto que vas a vincular." }
    }
    Page {
        if (!loaded) { CircularProgressIndicator(); return@Page }
        if (original?.active == false) Hint("Trabajo anulado. Guardar una corrección lo reactivará y quedará auditado.")
        Field(title, { title = it }, "Descripción del trabajo", "visit_title")
        Field(date, { date = it }, "Fecha (AAAA-MM-DD)", "visit_date")
        Check("Fecha exacta; si es aproximada no servirá como límite exacto", exactDate) { exactDate = it }
        Field(km, { km = it }, "Kilómetros del trabajo (opcional)", "visit_km", numeric = true)
        Check("Kilómetros exactos", exactKm) { exactKm = it }
        Field(workshop, { workshop = it }, "Taller o contacto (opcional)")
        Heading("Líneas de trabajo")
        Hint("Selecciona solo la operación realmente realizada. Comprobar un nivel no equivale a sustituir el líquido.")
        lines.forEachIndexed { index, line ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Trabajo ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    Field(line.title, { replace(index, line.copy(title = it)) }, "Trabajo realizado", "line_title_$index")
                    Choice("Operación satisfecha", line.ruleId, listOf("" to "Sin operación de mantenimiento") + tasks.map { it.rule.id to it.rule.title }) { ruleId ->
                        replace(index, line.copy(ruleId = ruleId, title = line.title.ifBlank { tasks.firstOrNull { it.rule.id == ruleId }?.rule?.title ?: "" }))
                    }
                    Choice("Categoría", line.category, listOf("maintenance" to "Mantenimiento", "repair" to "Reparación", "inspection" to "Revisión", "other" to "Otro")) { replace(index, line.copy(category = it)) }
                    Field(line.pieces, { replace(index, line.copy(pieces = it)) }, "Piezas, referencias y cantidades (opcional)", multiline = true)
                    Field(line.cost, { replace(index, line.copy(cost = it)) }, "Reparto del coste EUR (opcional)", numeric = true)
                    if (lines.size > 1) TextButton(onClick = { lineJson = gson.toJson(lines.filterIndexed { i, _ -> i != index }) }) { Text("Quitar línea") }
                }
            }
        }
        OutlinedButton(onClick = { lineJson = gson.toJson(lines + LineDraft()) }, modifier = Modifier.fillMaxWidth()) { Text("Añadir línea de trabajo") }
        Heading("Costes")
        Field(budget, { budget = it }, "Presupuesto EUR (no se suma a gastos)", numeric = true)
        Field(paid, { paid = it }, "Importe pagado EUR (opcional)", "visit_paid", numeric = true)
        Hint("Vacío significa desconocido. 0 significa trabajo gratuito. La factura se cuenta una vez.")
        val candidates = expenses.filter { it.active && (it.visitId == null || it.visitId == id) }
        Choice("Pago ya registrado", linked, listOf("" to "Crear o mantener el pago de esta visita") + candidates.map { it.id to "${it.concept} · ${money(it.cents)}" }) { expenseId ->
            linked = expenseId
            candidates.firstOrNull { it.id == expenseId }?.let { paid = moneyInput(it.cents) }
        }
        Field(notes, { notes = it }, "Notas", multiline = true)
        val phaseSelected = lines.any { line -> tasks.any { it.rule.id == line.ruleId && it.rule.recurrence in setOf("phase", "phase_b") } }
        if (phaseSelected) {
            Check("Adoptar las operaciones de esta visita como referencia del próximo ciclo", anchor) { anchor = it }
            if (anchor) Hint("Solo para las operaciones realizadas de la revisión. Es una referencia elegida; no prueba cuál era la fase original del fabricante ni completa otras operaciones.")
        }
        if (original != null) OutlinedButton(onClick = { attachments(id) }, modifier = Modifier.fillMaxWidth()) { Text("Fotos y documentos de este trabajo") }
        else Hint("Después de guardar, abre el trabajo en Historial para añadir fotos o PDF.")
        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { runCatching { validate() }.onSuccess { validation = null; confirm = true }.onFailure { validation = it.message ?: "Revisa los datos." } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar trabajo") }
        if (original?.active == true) OutlinedButton(onClick = { voidConfirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Anular trabajo") }
    }
    if (confirm) Confirm("Confirmar trabajo realizado", "Confirma que estos ${lines.size} trabajos se han realizado. Solo las operaciones seleccionadas establecerán referencias de mantenimiento.", "Sí, se ha realizado", { confirm = false }) {
        confirm = false
        model.saveVisit(
            Visit(id = id, date = date, km = parseKm(km), kmExact = exactKm, title = title.trim(), workshop = workshop.trim(),
                notes = gson.toJson(VisitDetail(notes = notes, budget = budget, lines = lines)), paidCents = parseMoney(paid, true), active = true, dateExact = exactDate, operationalAnchor = anchor),
            lines.map { WorkLine(id = it.id, visitId = id, title = it.title.trim(), ruleId = it.ruleId.ifBlank { null }, costCents = parseMoney(it.cost, true), category = it.category, parts = it.pieces) },
            lines.map { it.ruleId }.filter { it.isNotBlank() }.distinct(), linked.ifBlank { null }, after)
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
        Hint("175.000 km fueron los kilómetros de compra. No se estima conducción ni se marca mantenimiento al añadir una lectura.")
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
