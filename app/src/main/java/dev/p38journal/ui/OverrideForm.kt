package dev.p38journal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.p38journal.domain.Task
import dev.p38journal.domain.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OverrideForm(model: JournalViewModel, task: Task?, busy: Boolean, after: (String) -> Unit) {
    val id = rememberSaveable { task?.rule?.id ?: freshId() }
    val custom = task == null || task.rule.origin == "custom" || task.rule.id.endsWith("-active")
    val canUseOnce = task == null || task.rule.recurrence == "once"
    var title by rememberSaveable { mutableStateOf(task?.rule?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(task?.rule?.notes ?: "") }
    var days by rememberSaveable { mutableStateOf(task?.rule?.days?.toString() ?: "") }
    var months by rememberSaveable { mutableStateOf(task?.rule?.months?.toString() ?: "") }
    var km by rememberSaveable { mutableStateOf(task?.rule?.km?.toString() ?: "") }
    var dueDate by rememberSaveable { mutableStateOf(task?.rule?.dueDate ?: "") }
    var dueKm by rememberSaveable { mutableStateOf(task?.rule?.dueKm?.toString() ?: "") }
    var mode by rememberSaveable { mutableStateOf(when (task?.rule?.recurrence) { "once" -> "once"; "component", null -> "component"; else -> "manufacturer" }) }
    var enabled by rememberSaveable { mutableStateOf(task?.rule?.enabled ?: true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var restore by rememberSaveable { mutableStateOf(false) }
    var saveConfirm by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Rule?>(null) }
    LaunchedEffect(id) { if (!custom) original = withContext(Dispatchers.IO) { model.repository.originalRule(id) } }
    fun positive(text: String): Int? {
        if (text.isBlank()) return null
        return requireNotNull(text.toIntOrNull()?.takeIf { it > 0 }) { "Introduce intervalos enteros mayores que cero o deja el campo vacío." }
    }
    fun value(): Rule {
        require(title.isNotBlank()) { "Introduce un nombre para el mantenimiento." }
        val base = task?.rule ?: Rule(id = id, title = title.trim(), system = "custom", operation = "custom", origin = "custom")
        if (mode == "manufacturer") return base.copy(title = title.trim(), notes = notes.trim(), enabled = enabled)
        if (mode == "once") {
            require(canUseOnce) { "Para una tarea única, crea un mantenimiento nuevo." }
            val targetDate = dueDate.takeIf { it.isNotBlank() }?.let(::validateDate)
            val targetKm = parseKm(dueKm)
            require(targetDate != null || targetKm != null) { "Indica una fecha o unos kilómetros para esta tarea única." }
            return base.copy(title = title.trim(), notes = notes.trim(), enabled = enabled, recurrence = "once", days = null, months = null, km = null,
                firstKm = null, firstMonths = null, dueDate = targetDate, dueKm = targetKm)
        }
        val newDays = positive(days); val newMonths = positive(months); val newKm = positive(km)
        require(newDays != null || newMonths != null || newKm != null) { "Mantén al menos un intervalo." }
        require(newDays == null || newMonths == null) { "Usa días o meses para el intervalo de calendario." }
        require((newDays == null || newDays <= 36500) && (newMonths == null || newMonths <= 1200) && (newKm == null || newKm <= 1000000)) { "Intervalo máximo: 100 años o 1.000.000 km." }
        return base.copy(title = title.trim(), notes = notes.trim(), enabled = enabled, recurrence = "component", days = newDays, months = newMonths, km = newKm,
            firstKm = null, firstMonths = null, dueDate = null, dueKm = null)
    }
    Page {
        Heading(if (task == null) "Crear mantenimiento" else "Editar mantenimiento")
        Hint(if (custom) "Operación propia. No necesita una fuente ni un enlace del fabricante." else "Tus cambios se guardan como Personalizado. La regla original del fabricante y el historial se conservan.")
        Field(title, { title = it }, "Nombre", "rule_title")
        Field(notes, { notes = it }, "Notas (opcional)", "rule_notes", multiline = true)
        Choice("Tipo de mantenimiento", mode, (if (!custom) listOf("manufacturer" to "Mantener modelo del fabricante") else emptyList()) + listOf("component" to "Recurrente") + (if (canUseOnce) listOf("once" to "Tarea única") else emptyList())) { mode = it }
        if (!canUseOnce) Hint("Para una tarea única, crea un mantenimiento nuevo.")
        if (mode != "once") {
            Field(km, { km = it; mode = "component" }, "Intervalo en kilómetros (opcional)", "rule_km", numeric = true)
            Field(months, { months = it; mode = "component" }, "Intervalo en meses (opcional)", "rule_months", numeric = true)
            Field(days, { days = it; mode = "component" }, "Intervalo en días (opcional)", "rule_days", numeric = true)
        } else {
            Field(dueDate, { dueDate = it }, "Fecha prevista (AAAA-MM-DD, opcional)", "rule_due_date")
            Field(dueKm, { dueKm = it }, "Kilómetros previstos (opcional)", "rule_due_km", numeric = true)
            Hint("Una tarea única se completa una vez y no vuelve a repetirse.")
        }
        if (task?.rule?.recurrence in setOf("phase", "phase_b") && mode != "manufacturer") Hint("Esta personalización sustituye la fase por el intervalo indicado. Se elimina el primer desfase de la fase; puedes restaurar el fabricante después.")
        Check("Recordatorios de esta operación", enabled) { enabled = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching { value() }.onSuccess { error = null; saveConfirm = true }.onFailure { error = it.message ?: "Revisa el mantenimiento." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar mantenimiento") }
        if (original != null) OutlinedButton(onClick = { restore = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restaurar fabricante") }
    }
    if (saveConfirm) Confirm("Guardar mantenimiento", "$title\n${if (mode == "once") "Una vez: ${dateLabel(dueDate.ifBlank { null })} · ${kmLabel(parseKm(dueKm))}" else if (mode == "manufacturer") "Se conserva el modelo del fabricante." else listOfNotNull(km.takeIf { it.isNotBlank() }?.let { "Cada $it km" }, months.takeIf { it.isNotBlank() }?.let { "Cada $it meses" }, days.takeIf { it.isNotBlank() }?.let { "Cada $it días" }).joinToString(" o ")}\n${if (enabled) "Recordatorios activados" else "Recordatorios desactivados"}", "Guardar", { saveConfirm = false }) {
        saveConfirm = false
        model.saveMaintenance(value(), custom) { after(id) }
    }
    if (restore) Confirm("Restaurar fabricante", "Se recuperará la regla original. Los trabajos y las revisiones históricas se conservarán.", "Restaurar", { restore = false }) {
        restore = false
        model.action("Regla del fabricante restaurada", { after(id) }, "change") { model.repository.resetRule(id) }
    }
}
