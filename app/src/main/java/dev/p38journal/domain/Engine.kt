package dev.p38journal.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Rule(
    val id: String, val title: String, val system: String, val operation: String,
    val days: Int? = null, val months: Int? = null, val km: Int? = null,
    val source: String, val sourceUrl: String, val condition: String = "",
    val initialAction: String = title, val priority: Int = 2,
    val recurrence: String = "component", val revision: Int = 1
)

data class Event(
    val id: String, val visitId: String, val ruleId: String, val date: String?, val km: Int?,
    val dateExact: Boolean = true, val kmExact: Boolean = true, val active: Boolean = true,
    val revision: Int = 1, val workLineId: String? = null,
    val provenance: String = "owner_recorded", val operationalAnchor: Boolean = false
)

data class Task(
    val rule: Rule, val status: String, val dueDate: String?, val dueKm: Int?,
    val reasons: List<String>, val generation: String, val lastEvent: Event?
)

object Engine {
    fun evaluate(
        rules: List<Rule>, events: List<Event>, today: LocalDate, currentKm: Int?,
        leadDays: Int = 30, leadKm: Int = 1000
    ): List<Task> = rules.map { rule ->
        if (rule.recurrence == "inactive") {
            val generation = MessageDigest.getInstance("SHA-256").digest(rule.toString().toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            return@map Task(rule, "PLANNED", null, null, listOf("Perfil desactivado"), generation, null)
        }
        require(rule.days == null || rule.days > 0)
        require(rule.months == null || rule.months > 0)
        require(rule.km == null || rule.km > 0)
        val matching = events.filter { it.ruleId == rule.id && it.active }
        val ordered = matching.sortedWith(compareBy<Event> { it.date ?: "" }.thenBy { it.km ?: -1 }.thenBy { it.id })
        val anchor = ordered.lastOrNull()
        val exactDate = anchor?.takeIf { it.dateExact && it.date != null }
        val exactKm = anchor?.takeIf { it.kmExact && it.km != null }
        val phase = rule.recurrence in listOf("phase", "phase_b")
        val milestone = rule.recurrence == "milestone"
        val hasOperationalPhase = !(phase || milestone) || anchor?.operationalAnchor == true
        val dueDate = if (hasOperationalPhase) exactDate?.date?.let { LocalDate.parse(it) }?.let { date ->
            when { rule.months != null -> date.plusMonths(rule.months.toLong()); rule.days != null -> date.plusDays(rule.days.toLong()); else -> null }
        } else null
        val dueKm = if (hasOperationalPhase) exactKm?.km?.let { reading -> rule.km?.let { interval ->
            Math.addExact(reading, interval)
        } } else null
        val reasons = mutableListOf<String>()
        if (dueDate != null && !today.isBefore(dueDate)) reasons += "Vence por fecha"
        if (dueKm != null && currentKm != null && currentKm >= dueKm) reasons += "Vence por kilómetros"
        val frequent = (rule.days ?: Int.MAX_VALUE) <= 7 || (rule.months ?: Int.MAX_VALUE) <= 1
        val timeInterval = exactDate?.date?.let { base -> dueDate?.let { ChronoUnit.DAYS.between(LocalDate.parse(base), it).toInt() } }
        val proportionalDays = minOf(leadDays.coerceAtLeast(0), ((timeInterval ?: 120) / 4).coerceAtLeast(0))
        val proportionalKm = minOf(leadKm.coerceAtLeast(0), (rule.km ?: 4000) / 4)
        val soonDate = dueDate != null && today.isBefore(dueDate) && !today.plusDays(proportionalDays.toLong()).isBefore(dueDate)
        val soonKm = dueKm != null && currentKm != null && currentKm < dueKm && dueKm.toLong() - currentKm <= proportionalKm
        val finishedOneOff = rule.recurrence in listOf("initial", "once") && matching.isNotEmpty()
        val missingReference = !finishedOneOff && (anchor == null || !hasOperationalPhase || (dueDate == null && dueKm == null))
        val status = when {
            finishedOneOff -> "PLANNED"
            rule.recurrence == "condition" -> if (matching.isEmpty()) "INITIAL" else "PLANNED"
            phase && anchor != null && ((anchor.dateExact && anchor.date != null) || (anchor.kmExact && anchor.km != null)) && !hasOperationalPhase -> "PLANNED"
            missingReference && rule.condition.contains("known_pre_purchase_replaced") -> "PLANNED"
            missingReference -> "INITIAL"
            reasons.isNotEmpty() -> "DUE"
            (soonDate || soonKm) && !frequent -> "SOON"
            else -> "PLANNED"
        }
        if (status == "INITIAL") reasons += if (phase || milestone) "Revisar contenido de fase o hito y establecer referencia operativa explícita" else "Realizar la acción inicial; no consta una referencia precisa"
        if (phase && status == "PLANNED" && anchor != null && !hasOperationalPhase) reasons += "Operación registrada; adoptar referencia operativa para programar la siguiente fase"
        if (status == "SOON") {
            if (soonDate) reasons += "Próxima por fecha"
            if (soonKm) reasons += "Próxima por kilómetros"
        }
        val generationContent = listOf(rule, dueDate, dueKm,
            ordered.joinToString { "${it.id}:${it.date}:${it.km}:${it.dateExact}:${it.kmExact}:${it.operationalAnchor}:${it.revision}" }).joinToString("|")
        val generation = MessageDigest.getInstance("SHA-256").digest(generationContent.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        Task(rule, status, dueDate?.toString(), dueKm, reasons, generation, anchor)
    }.sortedWith(compareBy<Task> { when(it.status) { "DUE" -> 0; "INITIAL" -> 1; "SOON" -> 2; else -> 3 } }
        .thenBy { it.rule.priority }.thenBy { it.dueDate ?: "9999" }.thenBy { it.rule.title })
}

object Money {
    fun parseCents(input: String): Long {
        val value = input.trim()
        require(value.matches(Regex("\\d+([.,]\\d{1,2})?"))) { "Introduce un importe válido con hasta dos decimales" }
        return BigDecimal(value.replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }
    fun format(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',') + " €"
}
