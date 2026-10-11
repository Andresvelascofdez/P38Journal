package dev.p38journal.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class EngineTest {
    private val oil = Rule("oil", "Cambio de aceite y filtro", "engine", "replace", months = 12, km = 20000, source = "fixture", sourceUrl = "https://example.invalid")
    private val plan = PlanningReference(baselineDate = "2026-10-15")
    private fun event(date: String? = "2026-10-16", km: Int? = 195200, dateExact: Boolean = true, kmExact: Boolean = true, active: Boolean = true) = Event("fixture", "visit", "oil", date, km, dateExact, kmExact, active)
    private fun evaluate(today: String, km: Int?, events: List<Event> = emptyList(), rule: Rule = oil, reference: PlanningReference = plan) = Engine.evaluate(listOf(rule), events, LocalDate.parse(today), km, plan = reference).single()

    @Test fun ownerBaselineSchedulesWithoutCreatingCompletedWork() {
        val task = evaluate("2026-10-15", 184500)
        assertEquals(195000, task.dueKm); assertEquals("2027-10-15", task.dueDate)
        assertEquals(10500, task.dueKm!! - 184500); assertEquals("PLANNED", task.status); assertNull(task.lastEvent)
    }
    @Test fun exactBoundaryAndOvershootRemainDueWithoutRoundingOdometry() {
        assertNotEquals("DUE", evaluate("2026-10-15", 194999).status)
        assertEquals("DUE", evaluate("2026-10-15", 195000).status)
        assertEquals("DUE", evaluate("2026-10-15", 197000).status)
    }
    @Test fun completedReplacementRestartsOnlyItsOwnCycle() {
        val completed = evaluate("2026-10-16", 195200, listOf(event()))
        assertEquals(215200, completed.dueKm); assertEquals("2027-10-16", completed.dueDate)
        assertEquals(195000, evaluate("2026-10-16", 195200, listOf(event().copy(ruleId = "oil-level"))).dueKm)
    }
    @Test fun calendarOrMileageEachTriggersAndBothAreOneTask() {
        assertEquals("DUE", evaluate("2027-10-15", 180000).status)
        assertEquals("DUE", evaluate("2026-11-01", 195000).status)
        assertEquals(2, evaluate("2027-10-15", 195000).reasons.size)
    }
    @Test fun absentCurrentReadingDoesNotDisableCalendar() { assertEquals("DUE", evaluate("2027-10-15", null).status) }
    @Test fun approximateWorkFallsBackToPlanningDimensions() {
        val task = evaluate("2026-10-16", 180000, listOf(event(dateExact = false, kmExact = false)))
        assertEquals(195000, task.dueKm); assertEquals("2027-10-15", task.dueDate); assertNotEquals("INITIAL", task.status)
    }
    @Test fun partiallyPreciseWorkKeepsPreviousOrPlanningReferenceForOtherDimension() {
        val old = event("2026-10-16", 180000).copy(id = "old")
        val partial = event("2026-11-01", 190000, kmExact = false)
        val task = evaluate("2026-11-01", 190000, listOf(old, partial))
        assertEquals("2027-11-01", task.dueDate); assertEquals(200000, task.dueKm)
        assertEquals(195000, evaluate("2026-11-01", 190000, listOf(partial)).dueKm)
    }
    @Test fun historicalWorkBeforeBaseAndRecoveredEvidenceDoNotMovePlan() {
        val beforeBase = event("2026-10-10", 173500)
        val recovered = event("2026-10-16", 180000).copy(provenance = "recovered_conversation_summary")
        val task = evaluate("2026-10-16", 180000, listOf(beforeBase, recovered))
        assertEquals(195000, task.dueKm); assertEquals("2027-10-15", task.dueDate); assertNull(task.lastEvent)
    }
    @Test fun realPostPurchaseWorkBeforeV3ActivationDateIsPreserved() {
        val task = evaluate("2026-10-15", 180000, listOf(event("2026-09-15", 176000)))
        assertEquals(196000, task.dueKm); assertEquals("2027-09-15", task.dueDate)
    }
    @Test fun editingAndVoidingRecoverPreviousReferenceOrBaseline() {
        val old = event("2026-10-16", 180000).copy(id = "old")
        val latest = event("2026-11-01", 190000)
        assertEquals(210000, evaluate("2026-11-01", 190000, listOf(latest, old)).dueKm)
        assertEquals(200000, evaluate("2026-11-01", 190000, listOf(latest.copy(active = false), old)).dueKm)
        assertEquals(195000, evaluate("2026-11-01", 190000, listOf(latest.copy(active = false))).dueKm)
    }
    @Test fun oldEntryAndLaterEvaluationDoNotChangeGenerationWhenLimitsStayTheSame() {
        val latest = event("2026-11-01", 190000)
        val old = event("2026-10-16", 180000).copy(id = "old")
        val before = evaluate("2026-11-01", 190000, listOf(latest))
        assertEquals(before.generation, evaluate("2027-11-01", 210000, listOf(latest, old)).generation)
        assertNotEquals(before.generation, evaluate("2026-11-01", 190000, listOf(latest.copy(km = 190100))).generation)
    }
    @Test fun metadataEditWithNewEventIdentifierPreservesNotificationCycle() {
        val performed = event("2026-10-16", 195200)
        val before = evaluate("2027-11-01", 215200, listOf(performed))
        val edited = evaluate("2027-11-01", 215200, listOf(performed.copy(id = "new-revision-id")))
        assertEquals(before.generation, edited.generation); assertEquals(before.dueDate, edited.dueDate); assertEquals(before.dueKm, edited.dueKm)
    }
    @Test fun monthsClampToCalendarMonthIncludingLeapYear() {
        val monthly = oil.copy(months = 1, km = null)
        assertEquals("2028-02-29", evaluate("2028-02-01", null, rule = monthly, reference = plan.copy(baselineDate = "2028-01-31")).dueDate)
        assertEquals("2027-02-28", evaluate("2027-02-01", null, rule = monthly, reference = plan.copy(baselineDate = "2027-01-31")).dueDate)
        assertEquals("2028-03-29", evaluate("2028-03-01", null, rule = monthly, reference = plan.copy(baselineDate = "2028-02-29")).dueDate)
    }
    @Test fun nicosiaLocalDateAcrossDstIsIndependentOfUtcDate() {
        val clock = Clock.fixed(Instant.parse("2027-03-27T22:30:00Z"), ZoneId.of("Asia/Nicosia"))
        val daily = oil.copy(days = 1, months = null, km = null)
        val reference = plan.copy(baselineDate = "2027-03-27")
        assertEquals("DUE", Engine.evaluate(listOf(daily), emptyList(), LocalDate.now(clock), null, plan = reference).single().status)
        assertEquals("PLANNED", Engine.evaluate(listOf(daily), emptyList(), LocalDate.now(clock.withZone(ZoneId.of("UTC"))), null, plan = reference).single().status)
    }
    @Test fun phasesArePlannedFromBaseWithBFirstAndCSecondWithoutQuestionnaire() {
        val b = oil.copy(recurrence = "phase_b", months = 24, km = 40000, firstMonths = 12, firstKm = 20000)
        val c = b.copy(recurrence = "phase", firstMonths = 24, firstKm = 40000)
        assertEquals(195000, evaluate("2026-10-15", 175000, rule = b).dueKm)
        assertEquals("2027-10-15", evaluate("2026-10-15", 175000, rule = b).dueDate)
        assertEquals(215000, evaluate("2026-10-15", 175000, rule = c).dueKm)
        assertEquals("2028-10-15", evaluate("2026-10-15", 175000, rule = c).dueDate)
        assertEquals(235200, evaluate("2026-10-16", 195200, listOf(event()), b).dueKm)
    }
    @Test fun pastUniqueMilestonesAndUnconfirmedConditionsDoNotCreateUrgency() {
        val past = oil.copy(recurrence = "once", firstKm = 40000)
        val conditional = oil.copy(recurrence = "condition", scheduleVisibility = "conditional")
        val manual = oil.copy(days = 1, months = null, scheduleVisibility = "manual")
        listOf(past, conditional, manual).forEach { rule ->
            val task = evaluate("2030-10-15", 250000, rule = rule)
            assertEquals("PLANNED", task.status); assertNull(task.dueDate); assertNull(task.dueKm)
        }
    }
    @Test fun futureUniqueMilestoneDoesNotRepeatAfterCompletion() {
        val once = oil.copy(recurrence = "once", firstKm = 180000)
        assertEquals(180000, evaluate("2026-10-15", 175000, rule = once).dueKm)
        assertNull(evaluate("2026-10-16", 195200, listOf(event()), once).dueKm)
    }
    @Test fun exactMoneySupportsBothDecimalSeparatorsAndRejectsInvalidPrecision() {
        assertEquals(1250L, Money.parseCents("12,50")); assertEquals(1250L, Money.parseCents("12.50")); assertEquals(0L, Money.parseCents("0"))
        assertEquals("12,50 €", Money.format(1250)); assertEquals(1L, Money.parseCents("0,01"))
        listOf("-1", "1,234", "NaN", "1.234,56", "").forEach { value ->
            try { Money.parseCents(value); fail("Accepted $value") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun customOnceUsesAbsoluteCalendarAndMileageAndNeverRepeatsAfterExplicitCompletion() {
        val once = Rule("oil", "Cita taller", "custom", "custom", recurrence = "once", origin = "custom",
            dueDate = "2026-10-20", dueKm = 176000)
        assertEquals("DUE", evaluate("2026-10-20", 175000, rule = once).status)
        assertEquals("DUE", evaluate("2026-10-16", 176000, rule = once).status)
        val completed = event("2026-10-10", null, dateExact = false, kmExact = false)
        val task = evaluate("2027-10-20", 190000, listOf(completed), once)
        assertNull(task.dueDate); assertNull(task.dueKm)
        assertEquals("2026-10-20", evaluate("2026-10-16", 175000, listOf(completed.copy(active = false)), once).dueDate)
        assertEquals("DUE", evaluate("2026-10-16", 175000, rule = once.copy(dueKm = 174000, dueDate = null)).status)
    }
    @Test fun disabledRemindersKeepActualCalendarAndMetadataDoesNotStartANewCycle() {
        val before = evaluate("2027-10-15", 195000)
        val metadata = oil.copy(title = "Nombre editado", notes = "Nota propia", revision = 99, source = "Otra etiqueta")
        assertEquals(before.generation, evaluate("2027-10-15", 195000, rule = metadata).generation)
        val disabled = evaluate("2027-10-15", 195000, rule = oil.copy(enabled = false))
        assertEquals(before.dueKm, disabled.dueKm); assertEquals(before.dueDate, disabled.dueDate)
        assertEquals("DUE", disabled.status); assertFalse(disabled.rule.enabled)
        assertNotEquals(before.generation, disabled.generation)
        assertNotEquals(before.generation, evaluate("2027-10-15", 195000, rule = oil.copy(km = 10000)).generation)
    }
    @Test fun partialServiceLeavesUnperformedCommonAndPhaseOperationsPending() {
        val b = oil.copy(id = "b", recurrence = "phase_b", km = 40000, months = 24, firstKm = 20000, firstMonths = 12)
        val pending = oil.copy(id = "other")
        val tasks = Engine.evaluate(listOf(oil, b, pending), listOf(event()), LocalDate.parse("2026-10-16"), 195200, plan = plan).associateBy { it.rule.id }
        assertEquals(215200, tasks.getValue("oil").dueKm)
        assertEquals(195000, tasks.getValue("b").dueKm); assertEquals("DUE", tasks.getValue("b").status)
        assertEquals(195000, tasks.getValue("other").dueKm); assertEquals("DUE", tasks.getValue("other").status)
    }
    @Test fun upgradeExposesExactV3HashAliasWithoutReplacingTheEffectiveScheduleGeneration() {
        val task = evaluate("2026-10-16", 195200, listOf(event()))
        assertEquals("ac238792412b9848553e8ec3", task.legacyGeneration)
        assertNotEquals(task.legacyGeneration, task.generation)
        assertNotEquals(task.legacyGeneration, evaluate("2026-10-16", 195200, listOf(event()), oil.copy(km = 10000)).legacyGeneration)
        assertNull(evaluate("2026-10-16", 195200, rule = oil.copy(scheduleVisibility = "manual")).legacyGeneration)
    }
}
