package dev.p38journal.review

import dev.p38journal.data.FuelEntry
import dev.p38journal.data.TyreEvent
import dev.p38journal.domain.Consumption
import dev.p38journal.domain.DecimalInput
import dev.p38journal.domain.Engine
import dev.p38journal.domain.Event
import dev.p38journal.domain.PlanningReference
import dev.p38journal.domain.Rule
import dev.p38journal.domain.Tyres
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ExpansionIndependentTest {
    private fun fuel(id: String, day: Int, km: Int?, litres: Long?, full: Boolean) =
        FuelEntry(id, "2026-10-${day.toString().padStart(2, '0')}", km, litres, 1000, full)

    private fun tyre(id: String, kind: String, km: Int, sequence: Long, axis: String = "front") =
        TyreEvent(id, axis, kind, null, km, 10000, sequence = sequence)

    @Test fun threeClosedIntervalsUseExactTotalDistanceAndExcludeOpenLitres() {
        val entries = listOf(fuel("start", 1, 175000, 999000, true),
            fuel("partial", 2, 175200, 20000, false), fuel("close1", 3, 175500, 50000, true),
            fuel("close2", 4, 176500, 60000, true), fuel("close3", 5, 176750, 35000, true),
            fuel("open", 6, 176800, 999000, false))
        val result = Consumption.calculate(entries)
        assertEquals(BigDecimal("9.43"), result.average)
        assertEquals(165000L, result.totalLitresMilli)
        assertEquals(1750L, result.totalKm)
        assertEquals(3, result.intervals.size)
        assertEquals(1, result.pendingCount)
        assertEquals("2026-10-01", result.intervals.first().startDate)
        assertEquals("2026-10-05", result.intervals.last().endDate)
    }

    @Test fun unknownKilometresInvalidateTheirClosedIntervalButAllowLaterRecovery() {
        val result = Consumption.calculate(listOf(fuel("start", 1, 175000, null, true),
            fuel("missing", 2, null, 20000, false), fuel("close", 3, 175500, 50000, true),
            fuel("recovered", 4, 176000, 50000, true)))
        assertEquals(1, result.invalidIntervals)
        assertEquals(1, result.intervals.size)
        assertEquals("2026-10-03", result.intervals.single().startDate)
        assertEquals(BigDecimal("10.00"), result.average)
        assertEquals(50000L, result.totalLitresMilli)
    }

    @Test fun sameDayKnownKilometresProduceTheSameResultRegardlessOfInputOrder() {
        val entries = listOf(fuel("start", 1, 1000, 90000, true),
            fuel("partial", 1, 1200, 20000, false), fuel("close", 1, 1500, 50000, true),
            fuel("next", 1, 1700, 20000, true))
        assertEquals(Consumption.calculate(entries), Consumption.calculate(entries.reversed()))
        assertEquals(BigDecimal("12.86"), Consumption.calculate(entries).average)
    }

    @Test fun decimalPrecisionIsPreservedBeforeConsumptionRounding() {
        val millilitres = DecimalInput.parseLitresMilli(" 20,125 ")
        assertEquals(20125L, millilitres)
        assertEquals(millilitres, DecimalInput.parseLitresMilli("20.125"))
        val result = Consumption.calculate(listOf(fuel("start", 1, 1000, null, true),
            fuel("close", 2, 1500, millilitres, true)))
        assertEquals(BigDecimal("4.03"), result.average)
        assertEquals(20125L, result.totalLitresMilli)
    }

    @Test fun correctingAnEarlierSetAndReviewNeverReplacesTheLaterSet() {
        val old = tyre("old", "mount", 175000, 1)
        val oldReview = tyre("old-review", "review", 185200, 2)
        val replacement = tyre("current", "change", 190000, 3)
        val currentReview = tyre("current-review", "review", 192000, 4)
        val history = listOf(old, oldReview, replacement, currentReview, tyre("rear", "mount", 175000, 1, "rear"))
        val expected = Tyres.evaluate(history, 195000)
        val corrected = Tyres.evaluate(history.map { when (it.id) {
            "old" -> it.copy(km = 191000, date = "2026-10-11")
            "old-review" -> it.copy(km = 194000, date = "2026-10-12")
            else -> it
        } }, 195000)
        assertEquals(expected, corrected)
        assertEquals(5000, corrected.first().usedKm)
        assertEquals(202000, corrected.first().dueKm)
        assertEquals(20000, corrected.last().usedKm)
    }

    @Test fun VoidingCurrentReplacementRecoversPreviousSetAndItsReview() {
        val old = tyre("old", "mount", 175000, 1)
        val review = tyre("review", "review", 185200, 2)
        val changed = tyre("changed", "change", 190000, 3)
        val recovered = Tyres.evaluate(listOf(old, review, changed.copy(active = false)), 195200).first()
        assertEquals("old", recovered.mount!!.id)
        assertEquals("review", recovered.review!!.id)
        assertEquals(20200, recovered.usedKm)
        assertEquals(195200, recovered.dueKm)
        assertEquals(0, recovered.remainingKm)
    }

    @Test fun tyreNotesAndUnknownDatesDoNotCreateAnotherCycle() {
        val mount = tyre("mount", "mount", 175000, 1)
        val review = tyre("review", "review", 185200, 2)
        val before = Tyres.evaluate(listOf(mount, review), 185200).first()
        val after = Tyres.evaluate(listOf(mount.copy(notes = "FIXTURE mount"),
            review.copy(notes = "FIXTURE review", date = "2026-10-12")), 185200).first()
        assertEquals(before.generation, after.generation)
        assertEquals(before.usedKm, after.usedKm)
        assertEquals(before.dueKm, after.dueKm)
        assertNull(Tyres.evaluate(emptyList(), 175000).first().mount)
    }

    @Test fun explicitlyLinkedUnknownDateWorkAdvancesKnownKmWithoutInventingCalendarDate() {
        val rule = Rule("review-partial-oil", "FIXTURE oil", "engine", "change", km = 10000, months = 12)
        val plan = PlanningReference(baselineDate = "2026-10-01")
        val known = Event("known", "known-work", rule.id, "2026-10-02", 178000)
        val unknown = Event("unknown", "manual-work", rule.id, null, 180000, dateExact = false)
        val evaluated = Engine.evaluate(listOf(rule), listOf(known, unknown), LocalDate.parse("2026-10-12"), 182000, plan = plan).single()
        assertEquals(190000, evaluated.dueKm)
        assertEquals("2027-10-02", evaluated.dueDate)
        assertNull(unknown.date)
        assertFalse(unknown.dateExact)
        val approximate = unknown.copy(date = "2026-10-01")
        val approximateResult = Engine.evaluate(listOf(rule), listOf(known, approximate), LocalDate.parse("2026-10-12"), 182000, plan = plan).single()
        assertEquals(188000, approximateResult.dueKm)
        assertEquals("2027-10-02", approximateResult.dueDate)
        val older = unknown.copy(id = "older", km = 177000)
        assertEquals(188000, Engine.evaluate(listOf(rule), listOf(known, older), LocalDate.parse("2026-10-12"), 182000, plan = plan).single().dueKm)
    }
}
