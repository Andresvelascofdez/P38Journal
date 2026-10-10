package dev.p38journal.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class EngineTest {
    private val oil = Rule("oil", "Cambiar aceite", "engine", "replace", months = 12, km = 20000, source = "fixture", sourceUrl = "https://example.invalid")
    private fun event(date: String? = "2026-10-15", km: Int? = 176000, dateExact: Boolean = true, kmExact: Boolean = true, active: Boolean = true) = Event("fixture", "visit", "oil", date, km, dateExact, kmExact, active)
    private fun evaluate(today: String, km: Int?, events: List<Event> = listOf(event()), rule: Rule = oil) = Engine.evaluate(listOf(rule), events, LocalDate.parse(today), km).single()

    @Test fun intervalHasIndependentDateAndMileage() {
        val task = evaluate("2027-01-01", 180000)
        assertEquals("2027-10-15", task.dueDate); assertEquals(196000, task.dueKm); assertEquals("PLANNED", task.status)
    }
    @Test fun dateOrMileageEachTriggersAndBothStayOneTask() {
        assertEquals("DUE", evaluate("2027-10-15", 180000).status)
        assertEquals("DUE", evaluate("2027-01-01", 196000).status)
        assertEquals(2, evaluate("2027-10-15", 196000).reasons.size)
    }
    @Test fun noCurrentMileageDoesNotDisableTime() { assertEquals("DUE", evaluate("2027-10-15", null).status) }
    @Test fun seedDoesNotStartRecurrence() {
        val task = evaluate("2026-10-10", null, emptyList())
        assertEquals("INITIAL", task.status); assertNull(task.dueDate); assertNull(task.dueKm)
    }
    @Test fun unknownAndApproximateReferencesDoNotBecomeExact() {
        val task = evaluate("2028-10-15", 200000, listOf(event(dateExact = false, kmExact = false)))
        assertEquals("INITIAL", task.status); assertNull(task.dueDate); assertNull(task.dueKm)
    }
    @Test fun preciseDimensionIsStillAvailable() {
        val task = evaluate("2027-10-15", 200000, listOf(event(kmExact = false)))
        assertEquals("DUE", task.status); assertEquals("2027-10-15", task.dueDate); assertNull(task.dueKm)
    }
    @Test fun laterApproximateServiceDoesNotReuseEarlierExactLimits() {
        val old = event("2025-01-01", 150000).copy(id = "old")
        val later = event("2026-10-15", 176000, dateExact = false, kmExact = false)
        val task = evaluate("2027-10-15", 196000, listOf(old, later))
        assertNull(task.dueDate); assertNull(task.dueKm); assertEquals("INITIAL", task.status)
        val partial = evaluate("2027-10-15", 196000, listOf(old, later.copy(dateExact = true)))
        assertEquals("2027-10-15", partial.dueDate); assertNull(partial.dueKm)
    }
    @Test fun endOfMonthAndLeapYearClamp() {
        val monthly = oil.copy(months = 1, km = null)
        assertEquals("2028-02-29", evaluate("2028-02-01", null, listOf(event("2028-01-31")), monthly).dueDate)
        assertEquals("2027-02-28", evaluate("2027-02-01", null, listOf(event("2027-01-31")), monthly).dueDate)
        assertEquals("2028-03-29", evaluate("2028-03-01", null, listOf(event("2028-02-29")), monthly).dueDate)
    }
    @Test fun localNicosiaDateAcrossDstUsesCalendarDayRatherThanUtcDay() {
        val fixed = Clock.fixed(Instant.parse("2027-03-27T22:30:00Z"), ZoneId.of("Asia/Nicosia"))
        val localToday = LocalDate.now(fixed)
        assertEquals(LocalDate.parse("2027-03-28"), localToday)
        val daily = oil.copy(days = 1, months = null, km = null)
        val task = Engine.evaluate(listOf(daily), listOf(event("2027-03-27", null)), localToday, null).single()
        assertEquals("2027-03-28", task.dueDate); assertEquals("DUE", task.status)
        val utcToday = LocalDate.now(fixed.withZone(ZoneId.of("UTC")))
        assertEquals("PLANNED", Engine.evaluate(listOf(daily), listOf(event("2027-03-27", null)), utcToday, null).single().status)
    }
    @Test fun olderEntryDoesNotDisplaceNewerAndVoidRecoversOld() {
        val old = event("2025-01-01", 150000).copy(id = "old")
        assertEquals("2027-10-15", evaluate("2026-10-16", 176000, listOf(event(), old)).dueDate)
        assertEquals("2026-01-01", evaluate("2026-10-16", 176000, listOf(event(active = false), old)).dueDate)
    }
    @Test fun checkingLevelCannotSatisfyOilReplacement() {
        assertEquals("INITIAL", evaluate("2026-10-15", 176000, listOf(event().copy(ruleId = "oil-level"))).status)
    }
    @Test fun eventCorrectionChangesGenerationAndStatusDoesNot() {
        val a = evaluate("2026-10-15", 176000)
        val b = evaluate("2027-10-15", 196000)
        assertEquals(a.generation, b.generation)
        assertNotEquals(a.generation, evaluate("2026-10-15", 176000, listOf(event("2026-10-16"))).generation)
    }
    @Test fun weeklyChecksDoNotHavePermanentSoonNotifications() {
        val check = oil.copy(days = 7, months = null, km = 400)
        assertEquals("PLANNED", evaluate("2026-10-21", 176399, rule = check).status)
        assertEquals("DUE", evaluate("2026-10-22", 176400, rule = check).status)
    }
    @Test fun monthlyChecksAreGroupedWithoutPermanentSoon() {
        assertEquals("PLANNED", evaluate("2026-11-14", 176000, rule = oil.copy(months = 1)).status)
    }
    @Test fun phasesRequireExpressOperationalReference() {
        val phase = oil.copy(recurrence = "phase", months = 24, km = 40000)
        val recorded = evaluate("2026-10-16", 176000, rule = phase)
        assertEquals("PLANNED", recorded.status); assertNull(recorded.dueDate); assertNull(recorded.dueKm)
        assertEquals("INITIAL", evaluate("2026-10-16", 176000, emptyList(), phase).status)
        val adopted = event().copy(operationalAnchor = true)
        assertEquals("2028-10-15", evaluate("2026-10-16", 176000, listOf(adopted), phase).dueDate)
    }
    @Test fun milestoneRequiresOperationalAdoptionThenUsesVerifiedRepeatInterval() {
        val milestone = oil.copy(recurrence = "milestone", months = 60, km = 100000)
        assertEquals("INITIAL", evaluate("2026-10-16", 175000, listOf(event(km = 175000)), milestone).status)
        val adopted = event(km = 175000).copy(operationalAnchor = true)
        val task = evaluate("2026-10-16", 175000, listOf(adopted), milestone)
        assertEquals(275000, task.dueKm); assertEquals("2031-10-15", task.dueDate)
    }
    @Test fun knownReplacementDoesNotDemandAnotherReplacementWithoutReference() {
        val known = oil.copy(condition = "known_pre_purchase_replaced; evidence approximate")
        assertEquals("PLANNED", evaluate("2026-10-16", null, emptyList(), known).status)
    }
    @Test fun monetaryValuesAreExactAndAcceptSpanishDecimal() {
        assertEquals(1250L, Money.parseCents("12,50")); assertEquals(1250L, Money.parseCents("12.50")); assertEquals(0L, Money.parseCents("0"))
        assertEquals("12,50 €", Money.format(1250)); assertEquals(1L, Money.parseCents("0,01"))
    }
    @Test fun malformedMoneyIsRejected() {
        listOf("-1", "1,234", "NaN", "1.234,56", "").forEach { value ->
            try { Money.parseCents(value); fail("Accepted $value") } catch (_: IllegalArgumentException) { }
        }
    }
}
