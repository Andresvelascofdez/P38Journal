package dev.p38journal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.p38journal.domain.ConditionalProfiles
import dev.p38journal.domain.Task

@Composable
fun ConditionalProfileForm(model: JournalViewModel, task: Task?, busy: Boolean, after: () -> Unit) {
    if (task == null) { Page { Hint("Tarea no disponible.") }; return }
    val derived = task.rule.id.endsWith("-active")
    val proposal = if (derived) task.rule.copy(recurrence = "component") else ConditionalProfiles.proposed(task.rule)
    if (proposal == null) { Page { Hint("No hay un intervalo condicionado verificado para esta operación.") }; return }
    var evidence by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var disable by rememberSaveable { mutableStateOf(false) }
    Page {
        Heading(proposal.title)
        Heading("Condición que debes confirmar")
        Text(task.rule.condition)
        Heading("Intervalo de la fuente")
        proposal.months?.let { Text("Cada $it meses") }
        proposal.days?.let { Text("Cada $it días") }
        proposal.km?.let { Text("Cada ${kmLabel(it)}") }
        Text(manufacturerReference(proposal.source))
        Hint("Solo activa este intervalo si has comprobado que la condición se aplica a tu coche o al uso real. Conserva la evidencia de VIN, taller o uso; no se presupone por el año del vehículo.")
        Field(evidence, { evidence = it }, "Evidencia de aplicabilidad comprobada", "profile_evidence", multiline = true)
        Hint("Activar el perfil no registra ningún trabajo. La referencia inicial del plan permite calcular el primer vencimiento; las operaciones reales posteriores actualizan el siguiente.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            if (evidence.isBlank()) error = "Escribe la evidencia que confirma que la condición se aplica."
            else { error = null; confirm = true }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (task.rule.recurrence == "inactive") "Confirmar y reactivar perfil" else "Confirmar y activar perfil")
        }
        if (derived && task.rule.recurrence != "inactive") OutlinedButton(onClick = { disable = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Desactivar perfil condicionado") }
    }
    if (confirm) Confirm("Confirmar aplicabilidad", "Confirmas que has comprobado esta condición y que la evidencia introducida es real. Se activará el intervalo documentado; no se creará una operación realizada.", "Activar perfil", { confirm = false }) {
        confirm = false
        val rule = proposal.copy(condition = "${proposal.condition}\nConfirmación del propietario: ${evidence.trim()}", revision = proposal.revision + 1)
        model.action("Perfil condicionado activado", after) { model.repository.saveRule(rule) }
    }
    if (disable) Confirm("Desactivar perfil", "Este perfil dejará de producir vencimientos. Se conservarán sus operaciones y la evidencia; podrás reactivarlo tras confirmar nuevamente la condición.", "Desactivar", { disable = false }) {
        disable = false
        model.action("Perfil condicionado desactivado", after) { model.repository.saveRule(task.rule.copy(recurrence = "inactive", revision = task.rule.revision + 1)) }
    }
}
