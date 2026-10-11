package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.p38journal.domain.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun projectedRemaining(group: ProjectedMaintenanceGroup, currentKm: Int, today: String): List<String> = buildList {
    group.dueKm?.let { limit -> add(if (currentKm >= limit) "Límite previsto alcanzado por kilometraje" else "Te faltan ${kmLabel(limit - currentKm)}") }
    group.dueDate?.let { limit ->
        val days = ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(limit))
        add(if (days <= 0) "Fecha prevista alcanzada" else "Faltan $days ${if (days == 1L) "día" else "días"}")
    }
}

@Composable
fun ProjectedPlanPage(groups: List<ProjectedMaintenanceGroup>, currentKm: Int, today: String, add: () -> Unit, realPlan: () -> Unit, open: (ProjectedMaintenanceGroup) -> Unit) {
    Page {
        Hint("Revisiones previstas si las operaciones anteriores se realizan en sus límites. Los trabajos que registres pueden cambiar estas revisiones.")
        groups.forEach { group ->
            Card(onClick = { open(group) }, modifier = Modifier.fillMaxWidth().testTag("projected_revision_${group.dueKm}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium)
                    group.dueKm?.let { if (!group.title.contains(kmLabel(it))) Text("Kilómetros previstos: ${kmLabel(it)}") }
                    group.dueDate?.let { Text(dateLabel(it)) }
                    if (group.dueDate != null && group.dueKm != null) Hint("Lo que ocurra antes")
                    projectedRemaining(group, currentKm, today).forEach { Hint(it) }
                    Text("${group.occurrences.size} operaciones incluidas", modifier = Modifier.testTag("projected_count_${group.dueKm}"))
                }
            }
        }
        if (groups.isEmpty()) Hint("No hay revisiones previstas con los datos actuales.")
        OutlinedButton(onClick = realPlan, modifier = Modifier.fillMaxWidth()) { Text("Vencimientos reales y pendientes") }
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Crear mantenimiento") }
    }
}

@Composable
fun ProjectedRevisionDetail(group: ProjectedMaintenanceGroup?, tasks: List<Task>, currentKm: Int, today: String, record: (List<String>) -> Unit, operation: (String) -> Unit) {
    var expanded by rememberSaveable(group?.id) { mutableStateOf(false) }
    Page {
        if (group == null) {
            Hint("Esta revisión ha cambiado. Vuelve a Mantenimiento para consultar el plan actualizado.")
            return@Page
        }
        Heading(group.title)
        group.dueKm?.let { if (!group.title.contains(kmLabel(it))) Text("Kilómetros previstos: ${kmLabel(it)}") }
        group.dueDate?.let { Text("Fecha prevista: ${dateLabel(it)}") }
        if (group.dueKm != null && group.dueDate != null) Hint("Lo que ocurra antes")
        projectedRemaining(group, currentKm, today).forEach { Text(it) }
        Text("${group.occurrences.size} operaciones incluidas", modifier = Modifier.testTag("projected_detail_count"), style = MaterialTheme.typography.titleMedium)
        val actual = group.actualTasks(tasks)
        if (group.occurrences.any { it.assumesPrevious }) Hint("Esta revisión supone que se han realizado las operaciones anteriores en sus límites previstos.")
        if (actual.isNotEmpty()) {
            val fullyCurrent = actual.size == group.occurrences.size
            if (!fullyCurrent) Hint("${actual.size} operaciones tienen aquí su próximo vencimiento real. El resto corresponde a ciclos futuros previstos.")
            Button(onClick = { record(actual.map { it.rule.id }) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (fullyCurrent) "Registrar realizado" else "Registrar ${actual.size} operaciones pendientes")
            }
        } else Hint("Las operaciones de esta revisión pertenecen a ciclos futuros. Registra los trabajos de su próximo vencimiento real.")
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "Ocultar qué incluye" else "Qué incluye") }
        if (expanded) {
            group.occurrences.forEach { occurrence ->
                Card(onClick = { operation(occurrence.rule.id) }, modifier = Modifier.fillMaxWidth().testTag("occurrence_row_${occurrence.rule.id}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(occurrence.rule.title, style = MaterialTheme.typography.titleMedium)
                        val realTask = tasks.firstOrNull { it.rule.id == occurrence.rule.id }
                        realTask?.let { Hint(operationInterval(it)) }
                        if (occurrence.assumesPrevious) Hint("Ciclo futuro previsto")
                        Hint("Consultar y editar operación")
                    }
                }
            }
        }
    }
}
