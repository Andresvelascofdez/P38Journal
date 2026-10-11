package dev.p38journal.domain

import java.time.LocalDate

data class ForecastOccurrence(
    val rule: Rule,
    val dueDate: String?,
    val dueKm: Int?,
    val sequence: Int
) {
    val assumesPrevious: Boolean get() = sequence > 0
}

object Forecast {
    fun project(tasks: List<Task>, stepsPerRule: Int = 4): List<ForecastOccurrence> {
        require(stepsPerRule in 1..12)
        return tasks.flatMap { task ->
            val rule = task.rule
            if (rule.scheduleVisibility !in setOf("maintenance", "service") ||
                rule.recurrence in setOf("inactive", "condition", "age") ||
                task.dueDate == null && task.dueKm == null) return@flatMap emptyList()
            buildList {
                var date = task.dueDate?.let(LocalDate::parse)
                var km = task.dueKm
                add(ForecastOccurrence(rule, date?.toString(), km, 0))
                if (rule.recurrence in setOf("once", "initial")) return@buildList
                for (sequence in 1 until stepsPerRule) {
                    val nextDate = date?.let { previous ->
                        when {
                            rule.months != null -> runCatching { previous.plusMonths(rule.months.toLong()) }.getOrNull()
                            rule.days != null -> runCatching { previous.plusDays(rule.days.toLong()) }.getOrNull()
                            else -> null
                        }
                    }
                    val nextKm = km?.let { previous -> rule.km?.let { interval ->
                        runCatching { Math.addExact(previous, interval) }.getOrNull()
                    } }
                    if (date != null && nextDate == null || km != null && nextKm == null) break
                    if (nextDate == null && nextKm == null) break
                    date = nextDate
                    km = nextKm
                    add(ForecastOccurrence(rule, date?.toString(), km, sequence))
                }
            }
        }.sortedWith(compareBy<ForecastOccurrence> { it.dueDate ?: "9999" }
            .thenBy { it.dueKm ?: Int.MAX_VALUE }.thenBy { it.rule.title })
    }
}
