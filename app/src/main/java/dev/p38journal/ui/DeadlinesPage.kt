package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.p38journal.data.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun DeadlinesPage(entries: List<Deadline>, today: String, add: () -> Unit, edit: (Deadline) -> Unit) {
    var inactive by rememberSaveable { mutableStateOf(false) }
    Page {
        Heading("Documentación y vencimientos")
        Hint("Fechas que eliges tú. Renovar requiere registrar y confirmar la nueva fecha real.")
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Añadir documentación") }
        Check("Mostrar entradas desactivadas", inactive) { inactive = it }
        val shown = entries.filter { it.active || inactive }.sortedBy { it.dueDate }
        if (shown.isEmpty()) Hint("Puedes añadir cualquier nombre; Seguro, Road tax y MOT son solo sugerencias.")
        shown.forEach { entry ->
            val days = ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(entry.dueDate))
            val status = when {
                !entry.active -> "Desactivada"
                days < 0 -> "Vencida"
                days == 0L -> "Vence hoy"
                days <= entry.leadDays -> "Próxima a vencer · faltan $days días"
                else -> "Faltan $days días"
            }
            ItemCard(entry.name, "${dateLabel(entry.dueDate)} · $status", { edit(entry) }) {
                if (!entry.enabled) Hint("Aviso desactivado")
            }
        }
    }
}

@Composable
fun DeadlineForm(model: JournalViewModel, original: Deadline?, renewals: List<DeadlineRenewal>, busy: Boolean, after: () -> Unit) {
    val id = rememberSaveable { original?.id ?: freshId() }
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var due by rememberSaveable { mutableStateOf(original?.dueDate ?: "") }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var enabled by rememberSaveable { mutableStateOf(original?.enabled ?: true) }
    var lead by rememberSaveable { mutableStateOf((original?.leadDays ?: 30).toString()) }
    var repeat by rememberSaveable { mutableStateOf(original?.repeatMonths?.toString() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var deactivate by rememberSaveable { mutableStateOf(false) }
    var renew by rememberSaveable { mutableStateOf(false) }
    var newDate by rememberSaveable(original?.dueDate) { mutableStateOf(original?.repeatMonths?.let { runCatching { LocalDate.parse(original.dueDate).plusMonths(it.toLong()).toString() }.getOrNull() } ?: "") }
    var renewError by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        Field(name, { name = it }, "Nombre de la documentación", "deadline_name")
        if (original == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Seguro", "Road tax", "MOT").forEach { suggestion -> TextButton(onClick = { name = suggestion }) { Text(suggestion) } }
        }
        Field(due, { due = it }, "Fecha de vencimiento (AAAA-MM-DD)", "deadline_due")
        Field(notes, { notes = it }, "Notas (opcional)", "deadline_notes", multiline = true)
        Check("Aviso activado", enabled, "deadline_enabled") { enabled = it }
        Field(lead, { lead = it }, "Días de antelación", "deadline_lead", numeric = true)
        Field(repeat, { repeat = it }, "Repetición en meses (vacío: puntual)", "deadline_repeat", numeric = true)
        Hint("La repetición solo sugiere la próxima fecha al renovar. No renueva ni genera gastos automáticamente.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                require(name.isNotBlank()) { "Escribe el nombre de la documentación." }
                val days = requireNotNull(lead.toIntOrNull()?.takeIf { it in 0..3650 }) { "La antelación debe ser de 0 a 3650 días." }
                val months = if (repeat.isBlank()) null else requireNotNull(repeat.toIntOrNull()?.takeIf { it in 1..1200 }) { "Indica una repetición de 1 a 1200 meses." }
                Deadline(id = id, name = name.trim(), dueDate = validateDate(due), notes = notes.trim(), enabled = enabled, leadDays = days, repeatMonths = months, active = original?.active ?: true)
            }.onSuccess { model.saveDeadline(it, after) }.onFailure { error = it.message }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar documentación") }
        if (original?.active == true) {
            OutlinedButton(onClick = { renew = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Registrar renovación") }
            OutlinedButton(onClick = { deactivate = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Desactivar entrada") }
        } else if (original != null) OutlinedButton(onClick = { model.saveDeadline(original.copy(active = true), after) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reactivar entrada") }
        val history = renewals.filter { it.deadlineId == id }.sortedByDescending { it.renewedAt }
        if (history.isNotEmpty()) {
            Heading("Historial de renovaciones")
            history.forEach { Text("${dateLabel(it.previousDueDate)} → ${dateLabel(it.newDueDate)}") }
        }
    }
    if (deactivate && original != null) Confirm("Desactivar documentación", "Se conservarán la entrada y sus renovaciones y se cancelará su aviso.", "Desactivar", { deactivate = false }) {
        deactivate = false; model.saveDeadline(original.copy(active = false), after)
    }
    if (renew && original != null) AlertDialog(onDismissRequest = { renew = false }, title = { Text("Confirmar renovación") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vencimiento anterior: ${dateLabel(original.dueDate)}")
            Field(newDate, { newDate = it }, "Nueva fecha real (AAAA-MM-DD)", "deadline_renew_date")
            if (original.repeatMonths != null) Hint("Fecha sugerida por repetición; confirma únicamente la fecha real de la renovación.")
            renewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            val date = validateDate(newDate)
            require(date != original.dueDate) { "Introduce una fecha real distinta del vencimiento anterior." }
            date
        }.onSuccess { renew = false; model.renewDeadline(id, it, after) }.onFailure { renewError = it.message }
    }, enabled = !busy) { Text("Confirmar renovación") } }, dismissButton = { TextButton(onClick = { renew = false }) { Text("Cancelar") } })
}
