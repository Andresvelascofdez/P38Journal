package dev.p38journal.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Rule(
    val id: String, val title: String, val system: String, val operation: String,
    val days: Int? = null, val months: Int? = null, val km: Int? = null,
    val source: String = "", val sourceUrl: String = "", val condition: String = "",
    val initialAction: String = title, val priority: Int = 2,
    val recurrence: String = "component", val revision: Int = 1,
    val scheduleVisibility: String = "maintenance", val serviceGroup: String = "",
    val firstKm: Int? = null, val firstMonths: Int? = null,
    val origin: String = "manufacturer", val notes: String = "", val enabled: Boolean = true,
    val dueDate: String? = null, val dueKm: Int? = null, val manufacturerRevision: Int? = null
)

data class PlanningReference(val id: String = "main", val baselineKm: Int = 175000,
    val baselineDate: String, val provenance: String = "user_selected_planning_baseline", val policyVersion: Int = 3)

data class Event(
    val id: String, val visitId: String, val ruleId: String, val date: String?, val km: Int?,
    val dateExact: Boolean = true, val kmExact: Boolean = true, val active: Boolean = true,
    val revision: Int = 1, val workLineId: String? = null,
    val provenance: String = "owner_recorded", val operationalAnchor: Boolean = false
)

data class Task(
    val rule: Rule, val status: String, val dueDate: String?, val dueKm: Int?,
    val reasons: List<String>, val generation: String, val lastEvent: Event?, val legacyGeneration: String? = null
)

object Engine {
    fun evaluate(
        rules: List<Rule>, events: List<Event>, today: LocalDate, currentKm: Int?,
        leadDays: Int = 30, leadKm: Int = 1000, plan: PlanningReference? = null
    ): List<Task> = rules.map { rule ->
        val reference = plan ?: PlanningReference(baselineDate = today.toString())
        val baseDate = LocalDate.parse(reference.baselineDate)
        val hidden = rule.scheduleVisibility in listOf("manual", "conditional", "inactive") || rule.recurrence in listOf("inactive", "condition", "age")
        if (hidden) return@map Task(rule, "PLANNED", null, null,
            if (rule.recurrence == "inactive" || rule.scheduleVisibility == "inactive") listOf("Perfil desactivado") else emptyList(), hash(schedule(rule) + reference), null)
        require(rule.days == null || rule.days > 0)
        require(rule.months == null || rule.months > 0)
        require(rule.km == null || rule.km > 0)
        val customOnce = rule.origin in listOf("custom", "personalized") && rule.recurrence == "once" && (rule.dueDate != null || rule.dueKm != null)
        val matching = events.filter { event -> event.ruleId == rule.id && event.active &&
            !event.provenance.contains("prepurchase", true) && !event.provenance.contains("recovered", true) &&
            if (customOnce) true else if (event.kmExact && event.km != null) event.km >= reference.baselineKm
            else event.dateExact && event.date != null && LocalDate.parse(event.date) >= baseDate }
        val ordered = matching.sortedWith(compareBy<Event> { it.date ?: "" }.thenBy { it.km ?: -1 }.thenBy { it.id })
        val exactDate = ordered.lastOrNull { it.dateExact && it.date != null }
        val orderedKm = ordered.lastOrNull { it.kmExact && it.km != null }
        val undatedKm = ordered.lastOrNull { it.kmExact && it.km != null && it.date == null }
        val exactKm = if (undatedKm != null && (orderedKm == null || undatedKm.km!! > orderedKm.km!!)) undatedKm else orderedKm
        val anchor = exactKm?.takeIf { it.date == null } ?: ordered.lastOrNull()
        val phase = rule.recurrence in listOf("phase", "phase_b")
        val oneOff = rule.recurrence in listOf("initial", "once")
        if (oneOff && (matching.isNotEmpty() || !customOnce && (rule.firstKm == null || rule.firstKm <= reference.baselineKm))) {
            return@map Task(rule, "PLANNED", null, null, emptyList(), hash(schedule(rule) + reference), anchor)
        }
        val initialMonths = if (phase && exactDate == null) rule.firstMonths ?: if(rule.recurrence == "phase_b") 12 else 24 else rule.months
        val initialKm = if (phase && exactKm == null) rule.firstKm ?: if(rule.recurrence == "phase_b") 20000 else 40000 else rule.km
        val dateAnchor = exactDate?.date?.let(LocalDate::parse) ?: baseDate
        val dueDate = if (customOnce) rule.dueDate?.let(LocalDate::parse) else if (oneOff) null else when {
            initialMonths != null -> dateAnchor.plusMonths(initialMonths.toLong())
            rule.days != null -> dateAnchor.plusDays(rule.days.toLong())
            else -> null
        }
        val dueKm = if (customOnce) rule.dueKm else if (oneOff) rule.firstKm else initialKm?.let { Math.addExact(exactKm?.km ?: reference.baselineKm, it) }
        val reasons = mutableListOf<String>()
        if (dueDate != null && !today.isBefore(dueDate)) reasons += "Vence por fecha"
        if (dueKm != null && currentKm != null && currentKm >= dueKm) reasons += "Vence por kilómetros"
        val frequent = (rule.days ?: Int.MAX_VALUE) <= 7 || (rule.months ?: Int.MAX_VALUE) <= 1
        val timeInterval = dueDate?.let { ChronoUnit.DAYS.between(dateAnchor, it).toInt() }
        val proportionalDays = minOf(leadDays.coerceAtLeast(0), ((timeInterval ?: 120) / 4).coerceAtLeast(0))
        val proportionalKm = minOf(leadKm.coerceAtLeast(0), (rule.km ?: 4000) / 4)
        val soonDate = dueDate != null && today.isBefore(dueDate) && !today.plusDays(proportionalDays.toLong()).isBefore(dueDate)
        val soonKm = dueKm != null && currentKm != null && currentKm < dueKm && dueKm.toLong() - currentKm <= proportionalKm
        val status = when {
            reasons.isNotEmpty() -> "DUE"
            (soonDate || soonKm) && !frequent -> "SOON"
            else -> "PLANNED"
        }
        if (status == "SOON") {
            if (soonDate) reasons += "Próxima por fecha"
            if (soonKm) reasons += "Próxima por kilómetros"
        }
        val generationContent = listOf(schedule(rule), reference, dueDate, dueKm, exactDate?.date, exactKm?.km).joinToString("|")
        val generation = hash(generationContent)
        val legacyGeneration = hash(listOf(legacyV3Rule(rule), reference, dueDate, dueKm, exactDate?.date, exactKm?.km).joinToString("|"))
        Task(rule, status, dueDate?.toString(), dueKm, reasons, generation, anchor, legacyGeneration)
    }.sortedWith(compareBy<Task> { when(it.status) { "DUE" -> 0; "SOON" -> 1; else -> 2 } }
        .thenBy { it.rule.priority }.thenBy { it.dueDate ?: "9999" }.thenBy { it.rule.title })
    private fun schedule(rule: Rule) = listOf(rule.id, rule.days, rule.months, rule.km, rule.recurrence,
        rule.scheduleVisibility, rule.firstKm, rule.firstMonths, rule.enabled, rule.dueDate, rule.dueKm,
        rule.origin in listOf("custom", "personalized") && rule.recurrence == "once" && (rule.dueDate != null || rule.dueKm != null)).joinToString("|")
    private fun legacyV3Rule(rule: Rule) = "Rule(id=${rule.id}, title=${rule.title}, system=${rule.system}, operation=${rule.operation}, days=${rule.days}, months=${rule.months}, km=${rule.km}, source=${rule.source}, sourceUrl=${rule.sourceUrl}, condition=${rule.condition}, initialAction=${rule.initialAction}, priority=${rule.priority}, recurrence=${rule.recurrence}, revision=${rule.revision}, scheduleVisibility=${rule.scheduleVisibility}, serviceGroup=${rule.serviceGroup}, firstKm=${rule.firstKm}, firstMonths=${rule.firstMonths})"
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

object Money {
    fun parseCents(input: String): Long {
        val value = input.trim()
        require(value.matches(Regex("\\d+([.,]\\d{1,2})?"))) { "Introduce un importe válido con hasta dos decimales" }
        return BigDecimal(value.replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }
    fun format(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',') + " €"
}
