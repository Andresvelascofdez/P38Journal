package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.p38journal.data.*
import java.time.LocalDate

private fun structuredWorkNotes(raw: String?): JsonObject? = runCatching {
    val value = JsonParser.parseString(raw ?: "").asJsonObject
    value.takeIf { it.get("schema")?.asInt == 1 && it.get("lines")?.isJsonArray == true && it.get("notes")?.isJsonPrimitive == true }
}.getOrNull()

private fun visibleWorkNotes(raw: String?): String = structuredWorkNotes(raw)?.get("notes")?.asString ?: raw ?: ""
private fun storedWorkNotes(original: String?, notes: String): String = structuredWorkNotes(original)?.apply { addProperty("notes", notes) }?.toString() ?: notes

@Composable
fun WorksPage(works: List<Visit>, status: String, changeStatus: (String) -> Unit, add: () -> Unit, edit: (Visit) -> Unit) {
    Page {
        Heading("Trabajos y mejoras")
        Choice("Ver", status, listOf("planned" to "Previstos", "done" to "Realizados"), changeStatus)
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Añadir trabajo o mejora") }
        val entries = works.filter { it.active && it.status == status }
        if (entries.isEmpty()) Hint(if (status == "planned") "Añade lo que quieras reparar, montar o mejorar." else "Los trabajos realizados se conservan también en Historial, sin duplicarlos.")
        entries.sortedByDescending { if (status == "planned") it.targetDate ?: "" else it.date }.forEach { work ->
            ItemCard(work.title, if (status == "planned") "Objetivo: ${dateLabel(work.targetDate)}" else "${dateLabel(work.date)} · ${kmLabel(work.km)}", { edit(work) }) {
                if (status == "planned") work.estimatedCents?.let { Hint("Coste previsto: ${money(it)}") }
                else work.paidCents?.let { Hint("Pago real: ${money(it)}") }
            }
        }
    }
}

@Composable
fun ManualWorkForm(model: JournalViewModel, original: Visit?, expenses: List<Expense>, busy: Boolean, after: (Visit) -> Unit, expense: (String) -> Unit, maintenance: (String) -> Unit, attachments: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: freshId() }
    var name by rememberSaveable { mutableStateOf(original?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(visibleWorkNotes(original?.notes)) }
    var status by rememberSaveable { mutableStateOf(original?.status ?: "planned") }
    var date by rememberSaveable { mutableStateOf(original?.date ?: "") }
    var km by rememberSaveable { mutableStateOf(original?.km?.toString() ?: "") }
    var target by rememberSaveable { mutableStateOf(original?.targetDate ?: "") }
    var estimate by rememberSaveable { mutableStateOf(moneyInput(original?.estimatedCents)) }
    val linkedPayment = expenses.firstOrNull { it.active && it.visitId == id }
    var linked by rememberSaveable { mutableStateOf(linkedPayment?.id ?: "") }
    var paid by rememberSaveable { mutableStateOf(moneyInput(original?.paidCents)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var cancel by rememberSaveable { mutableStateOf(false) }
    val canonicalPayment = "${original?.paidCents}/${linkedPayment?.id}/${linkedPayment?.cents}"
    var loadedPayment by rememberSaveable { mutableStateOf(canonicalPayment) }
    LaunchedEffect(canonicalPayment) {
        if (canonicalPayment != loadedPayment) {
            paid = moneyInput(linkedPayment?.cents ?: original?.paidCents)
            linked = linkedPayment?.id ?: ""; loadedPayment = canonicalPayment
        }
    }
    fun value(): Visit {
        require(name.isNotBlank()) { "Escribe un nombre para el trabajo." }
        val actualDate = if (status == "done" && date.isNotBlank()) validateDate(date) else ""
        require(actualDate.isBlank() || LocalDate.parse(actualDate) <= LocalDate.parse(model.today())) { "Un trabajo realizado no puede tener fecha futura." }
        val realPayment = if (status == "done") parseMoney(paid, true) else expenses.firstOrNull { it.active && it.id == linked }?.cents ?: linkedPayment?.cents
        require(realPayment == null || actualDate.isNotBlank() || linked.isNotBlank() || linkedPayment != null) { "Para pagar un trabajo de fecha desconocida, vincula un gasto real que tenga su propia fecha." }
        return (original ?: Visit(id = id)).copy(title = name.trim(), notes = storedWorkNotes(original?.notes, notes.trim()), status = status, manual = true, active = true,
            date = actualDate, dateExact = actualDate.isNotBlank(), km = if (status == "done") parseKm(km) else null,
            targetDate = target.takeIf { it.isNotBlank() }?.let(::validateDate), estimatedCents = parseMoney(estimate, true), paidCents = realPayment)
    }
    Page {
        Field(name, { name = it }, "Nombre del trabajo o mejora", "work_name")
        Box(Modifier.testTag("work_status")) {
            Choice("Estado", status, if (original?.status == "done") listOf("done" to "Realizado") else listOf("planned" to "Previsto", "done" to "Realizado")) { status = it }
        }
        Field(notes, { notes = it }, "Notas (opcional)", "work_notes", multiline = true)
        if (status == "planned") {
            Field(target, { target = it }, "Fecha objetivo (AAAA-MM-DD, opcional)", "work_target")
            Field(estimate, { estimate = it }, "Coste previsto EUR (opcional)", "work_estimate", numeric = true)
            Hint("El coste previsto no es un gasto. Este trabajo no cambia el mantenimiento del fabricante.")
        } else {
            Field(date, { date = it }, "Fecha de realización (AAAA-MM-DD, si se conoce)", "work_date")
            Field(km, { km = it }, "Kilómetros de realización (si se conocen)", "work_km", numeric = true)
            Hint("Deja vacíos los datos históricos que no conozcas. Registrar este trabajo libre no reinicia mantenimientos ni actualiza el odómetro.")
            if (original?.status == "planned") Hint("Se conservará el mismo trabajo al pasar a realizado. El coste previsto no se convierte en un pago.")
            Field(paid, { paid = it }, "Importe real pagado EUR (opcional)", "work_paid", numeric = true)
        }
        val candidates = expenses.filter { it.active && it.fuelId == null && (it.visitId == null || it.visitId == id) }
        Choice("Gasto real vinculado (opcional)", linked, listOf("" to "Sin nuevo gasto elegido") + candidates.map { it.id to "${it.concept} · ${money(it.cents)}" }) {
            linked = it
            candidates.firstOrNull { entry -> entry.id == it }?.let { entry -> paid = moneyInput(entry.cents) }
        }
        if (status == "planned") Hint("Puedes vincular un pago ya registrado. El coste previsto permanece separado y no crea un gasto.")
        linkedPayment?.let { payment -> OutlinedButton(onClick = { expense(payment.id) }, modifier = Modifier.fillMaxWidth()) { Text("Consultar gasto vinculado") } }
        if (original != null) {
            OutlinedButton(onClick = { attachments(id) }, modifier = Modifier.fillMaxWidth()) { Text("Fotos y documentos del trabajo") }
            if (original.status == "done") OutlinedButton(onClick = { maintenance(id) }, modifier = Modifier.fillMaxWidth()) { Text("Vincular operación de mantenimiento") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { runCatching { value() }.onSuccess { error = null; confirm = true }.onFailure { error = it.message } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar trabajo o mejora") }
        if (original?.active == true) OutlinedButton(onClick = { cancel = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Anular trabajo o mejora") }
    }
    if (confirm) Confirm("Confirmar trabajo o mejora", "$name\n${if (status == "planned") "Previsto; no crea un gasto ni una operación realizada." else "Realizado · ${dateLabel(date)} · ${kmLabel(parseKm(km))}"}", "Guardar", { confirm = false }) {
        confirm = false
        val work = value()
        model.saveManualWork(work, linked.ifBlank { null }) { after(work) }
    }
    if (cancel) Confirm("Anular trabajo o mejora", "Se conservará la auditoría y se retirará del historial activo y de los gastos su pago vinculado, si existe.", "Anular", { cancel = false }) {
        cancel = false; model.voidVisit(id) { after(requireNotNull(original)) }
    }
}
