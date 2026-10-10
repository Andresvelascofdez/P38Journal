package dev.p38journal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.p38journal.domain.Task

@Composable
fun OverrideForm(model: JournalViewModel, task: Task?, busy: Boolean, after: () -> Unit) {
    if (task == null) { Page { Hint("Tarea no disponible.") }; return }
    var days by rememberSaveable { mutableStateOf(task.rule.days?.toString() ?: "") }
    var months by rememberSaveable { mutableStateOf(task.rule.months?.toString() ?: "") }
    var km by rememberSaveable { mutableStateOf(task.rule.km?.toString() ?: "") }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    fun positive(text: String): Int? {
        if (text.isBlank()) return null
        return requireNotNull(text.toIntOrNull()?.takeIf { it > 0 }) { "Introduce intervalos enteros mayores que cero o deja el campo vacío." }
    }
    Page {
        Heading(task.rule.title)
        Hint("Esta modificación es una preferencia del propietario. Conserva la fuente original y el historial de revisiones; no cambia lo que prescribe el fabricante.")
        Field(days, { days = it }, "Intervalo en días (opcional)", numeric = true)
        Field(months, { months = it }, "Intervalo en meses (opcional)", numeric = true)
        Field(km, { km = it }, "Intervalo en kilómetros (opcional)", numeric = true)
        Field(reason, { reason = it }, "Motivo de la personalización", multiline = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                val newDays = positive(days); val newMonths = positive(months); val newKm = positive(km)
                require(newDays != null || newMonths != null || newKm != null) { "Mantén al menos un intervalo." }
                require(newDays == null || newMonths == null) { "Usa días o meses para el intervalo de calendario." }
                require((newDays == null || newDays <= 36500) && (newMonths == null || newMonths <= 1200) && (newKm == null || newKm <= 1000000)) { "Intervalo máximo: 100 años o 1.000.000 km." }
                require(reason.isNotBlank()) { "Indica por qué eliges este intervalo." }
                task.rule.copy(days = newDays, months = newMonths, km = newKm,
                    source = "Personalizado por propietario: ${reason.trim()}. Base fabricante: ${task.rule.source}", revision = task.rule.revision + 1)
            }.onSuccess { rule -> model.action("Intervalo personalizado guardado", after) { model.repository.saveRule(rule) } }
                .onFailure { error = it.message ?: "Revisa los intervalos." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar intervalo personalizado") }
        OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restaurar regla del catálogo") }
    }
    if (confirm) Confirm("Restaurar regla", "Se recuperará la regla incluida en el catálogo. Los trabajos y las revisiones históricas se conservarán.", "Restaurar", { confirm = false }) {
        confirm = false
        model.action("Regla del catálogo restaurada", after) { model.repository.resetRule(task.rule.id) }
    }
}
