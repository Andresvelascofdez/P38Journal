package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.p38journal.domain.Forecast
import dev.p38journal.domain.ForecastOccurrence
import dev.p38journal.domain.Task

@Composable
fun ForecastPage(tasks: List<Task>) {
    Heading("Primeros cuatro hitos previstos")
    Hint("Previsión si las operaciones anteriores se realizan en sus fechas y kilómetros previstos. No representa trabajos hechos y no genera avisos. Las fechas y kilómetros reales que registres pueden cambiar estos hitos.")
    val groups = Forecast.project(tasks).groupBy {
        "${it.rule.serviceGroup.ifBlank { it.rule.id }}|${it.dueDate}|${it.dueKm}"
    }.values.sortedWith(compareBy<List<ForecastOccurrence>> { it.first().dueKm ?: Int.MAX_VALUE }.thenBy { it.first().dueDate ?: "9999" })
    groups.take(4).forEach { entries -> ForecastCard(entries) }
    if (groups.isEmpty()) Hint("No hay una previsión disponible para las operaciones actuales.")
}

@Composable
private fun ForecastCard(entries: List<ForecastOccurrence>) {
    val first = entries.first()
    var expanded by rememberSaveable(first.rule.id, first.dueKm, first.dueDate) { mutableStateOf(false) }
    val title = if (entries.size > 1) first.dueKm?.let { "Revisión prevista a los ${kmLabel(it)}" } ?: "Revisión prevista del ${dateLabel(first.dueDate)}" else first.rule.title
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(title)
            first.dueKm?.let { Text(kmLabel(it)) }
            first.dueDate?.let { Text(dateLabel(it)) }
            if (first.dueKm != null && first.dueDate != null) Hint("Lo que ocurra antes")
            Hint("${entries.size} operaciones previstas")
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar operaciones previstas" else "Consultar operaciones previstas") }
            if (expanded) entries.forEach { Text("• ${it.rule.title}") }
        }
    }
}
