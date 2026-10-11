package dev.p38journal.ui

import dev.p38journal.domain.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class MaintenanceGroup(val tasks: List<Task>) {
    val representative get() = tasks.first()
    val title: String get() = if (tasks.size == 1) friendlyTitle(representative) else dueKm?.let { "Revisión a los ${kmLabel(it)}" } ?: "Revisión del ${dateLabel(dueDate)}"
    val dueDate get() = representative.dueDate
    val dueKm get() = representative.dueKm
    val status get() = if (tasks.any { it.status == "DUE" }) "DUE" else if (tasks.any { it.status == "SOON" }) "SOON" else "PLANNED"
}

fun maintenanceGroups(tasks: List<Task>): List<MaintenanceGroup> = tasks
    .filter { it.rule.scheduleVisibility in setOf("maintenance", "service") && it.rule.recurrence != "inactive" && (it.dueDate != null || it.dueKm != null) }
    .groupBy { task ->
        if (task.rule.serviceGroup.isNotBlank()) "${task.rule.serviceGroup}|${task.dueDate}|${task.dueKm}" else "operation:${task.rule.id}"
    }.values.map(::MaintenanceGroup)
    .sortedWith(compareBy<MaintenanceGroup> { when (it.status) { "DUE" -> 0; "SOON" -> 1; else -> 2 } }.thenBy { it.dueDate ?: "9999" }.thenBy { it.dueKm ?: Int.MAX_VALUE })

fun friendlyTitle(task: Task): String = if (task.rule.origin != "manufacturer") task.rule.title else when (task.rule.id.removeSuffix("-active")) {
    "engine-oil-filter" -> "Cambio de aceite y filtro"
    "brake-fluid" -> "Cambio de líquido de frenos"
    "coolant-renewal" -> "Cambio de refrigerante"
    "spark-plugs" -> "Cambio de bujías"
    "axle-fluid" -> "Cambio de aceite de diferenciales"
    "auxiliary-belt-renewal" -> "Cambio de correa auxiliar"
    else -> task.rule.title.replace(Regex("^(Cambiar|Sustituir)\\s+", RegexOption.IGNORE_CASE), "Cambio de ")
        .replace(Regex("^Comprobar\\s+", RegexOption.IGNORE_CASE), "Comprobación de ")
        .replace(Regex("^Inspeccionar\\s+", RegexOption.IGNORE_CASE), "Revisión de ")
        .replace(Regex("^Lubricar\\s+", RegexOption.IGNORE_CASE), "Lubricación de ")
        .replace(Regex("^Ajustar\\s+", RegexOption.IGNORE_CASE), "Ajuste de ")
}

fun intervalLabel(task: Task): String {
    val time = task.rule.months?.let { "$it ${if (it == 1) "mes" else "meses"}" }
        ?: task.rule.days?.let { "$it ${if (it == 1) "día" else "días"}" }
    val distance = task.rule.km?.let(::kmLabel)
    val limits = listOfNotNull(distance, time)
    return if (limits.isEmpty()) "Según condición" else "Cada " + limits.joinToString(" o ")
}

fun operationInterval(task: Task): String {
    if (task.rule.recurrence == "once") return "Tarea única"
    if (task.rule.recurrence in setOf("phase", "phase_b") && task.lastEvent == null) {
        val first = listOfNotNull(task.rule.firstKm?.let(::kmLabel), task.rule.firstMonths?.let { "$it meses" }).joinToString(" o ")
        if (first.isNotBlank()) return "Primera en este plan: $first; después ${intervalLabel(task).replaceFirst("Cada ", "cada ")}"
    }
    return intervalLabel(task)
}

fun remainingLabels(group: MaintenanceGroup, currentKm: Int, today: String): List<String> = buildList {
    group.dueKm?.let { limit -> add(if (currentKm >= limit) "Pendiente por kilometraje" else "Te faltan ${kmLabel(limit - currentKm)}") }
    group.dueDate?.let { limit ->
        val remaining = ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(limit))
        add(if (remaining <= 0) "Pendiente por fecha" else "Faltan $remaining ${if (remaining == 1L) "día" else "días"}")
    }
}

fun manufacturerReference(source: String): String = source
    .replace("glossary", "notas").replace("row", "operación").replace("printed", "impresa").replace("supplement", "suplemento")
