package dev.p38journal.review

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.p38journal.domain.Engine
import dev.p38journal.domain.Event
import dev.p38journal.domain.ExpenseFilter
import dev.p38journal.domain.ExpenseFilters
import dev.p38journal.domain.Forecast
import dev.p38journal.domain.PlanningReference
import dev.p38journal.domain.Rule
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class IndependentV4Test {
    private val rules = JsonParser.parseString(File("src/main/assets/maintenance-catalogue.json").readText()).asJsonObject
        .getAsJsonArray("rules").map { Gson().fromJson(it, Rule::class.java) }
    private val plan = PlanningReference(baselineDate = "2026-10-01")
    private val today = LocalDate.parse(plan.baselineDate)
    private fun tasks(events: List<Event> = emptyList()) = Engine.evaluate(rules, events, today, 175000, plan = plan)
    private val firstIds get() = tasks().filter { it.dueKm == 195000 && it.dueDate == "2027-10-01" }.map { it.rule.id }.toSet()
    private val bIds = setOf("transfer-level", "axle-level")
    private val cIds = setOf("remote-batteries", "eas-compressor-service", "air-filter", "steering-shaft", "automatic-fluid", "transfer-fluid")

    @Test fun futureFortyThousandServiceContainsCommonOperationsAndCButNotB() {
        val real = tasks()
        val snapshot = real.toList()
        val forecast = Forecast.project(real, stepsPerRule = 4)
        val common = firstIds - bIds
        assertEquals(29, firstIds.size)
        assertEquals(27, common.size)
        assertEquals(6, real.count { it.dueKm == 215000 && it.dueDate == "2028-10-01" })
        val forty = forecast.filter { it.dueKm == 215000 && it.dueDate == "2028-10-01" }
        assertEquals(common + cIds, forty.map { it.rule.id }.toSet())
        assertEquals(33, forty.size)
        assertTrue(forty.filter { it.rule.id in common }.all { it.assumesPrevious })
        assertTrue(forty.filter { it.rule.id in cIds }.all { !it.assumesPrevious })
        assertEquals(snapshot, real)
        assertTrue(real.all { it.lastEvent == null })
        assertTrue(forecast.none { it.rule.scheduleVisibility in setOf("manual", "conditional", "inactive") })
    }

    @Test fun forecastAlternatesPhasesAndKeepsSixtyThousandAdditionalOperations() {
        val forecast = Forecast.project(tasks(), stepsPerRule = 4)
        val common = firstIds - bIds
        val sixty = forecast.filter { it.dueKm == 235000 && it.dueDate == "2029-10-01" }
        assertEquals(common + bIds + setOf("coolant-renewal", "brake-fluid"), sixty.map { it.rule.id }.toSet())
        assertEquals(31, sixty.size)
        val eighty = forecast.filter { it.dueKm == 255000 && it.dueDate == "2030-10-01" }
        assertEquals(common + cIds, eighty.map { it.rule.id }.toSet())
        assertTrue(forecast.filter { it.rule.id in bIds }.all { it.dueKm in setOf(195000, 235000, 275000, 315000) })
        assertTrue(forecast.filter { it.rule.id in cIds }.all { it.dueKm in setOf(215000, 255000, 295000, 335000) })
    }

    @Test fun partialLateOilWorkKeepsUnperformedTasksAndTheirForecastSeparate() {
        val event = Event("fixture-oil", "fixture-visit", "engine-oil-filter", "2027-10-15", 195200)
        val real = tasks(listOf(event))
        val oil = real.single { it.rule.id == "engine-oil-filter" }
        assertEquals(215200, oil.dueKm)
        assertEquals("2028-10-15", oil.dueDate)
        assertEquals(firstIds - "engine-oil-filter", real.filter { it.dueKm == 195000 && it.dueDate == "2027-10-01" }.map { it.rule.id }.toSet())
        val forecast = Forecast.project(real)
        assertEquals(32, forecast.count { it.dueKm == 215000 && it.dueDate == "2028-10-01" })
        assertEquals(setOf("engine-oil-filter"), forecast.filter { it.dueKm == 215200 && it.dueDate == "2028-10-15" }.map { it.rule.id }.toSet())
        assertEquals(1, real.count { it.lastEvent != null })
    }

    @Test fun actualBWorkAt195200RepeatsAt235200WithoutMovingOtherOperations() {
        val event = Event("fixture-b", "fixture-visit", "transfer-level", "2027-10-15", 195200)
        val real = tasks(listOf(event))
        val b = real.single { it.rule.id == "transfer-level" }
        assertEquals(235200, b.dueKm)
        assertEquals("2029-10-15", b.dueDate)
        assertEquals(195000, real.single { it.rule.id == "axle-level" }.dueKm)
        assertEquals(195000, real.single { it.rule.id == "engine-oil-filter" }.dueKm)
        assertEquals(215000, real.single { it.rule.id == "transfer-fluid" }.dueKm)
        val ownProjection = Forecast.project(real).filter { it.rule.id == "transfer-level" }
        assertEquals(listOf(235200, 275200, 315200, 355200), ownProjection.map { it.dueKm })
    }

    @Test fun timeAndKilometresCanIndependentlyExpireWithoutChangingForecastOrGeneration() {
        val oil = rules.single { it.id == "engine-oil-filter" }
        val dateDue = Engine.evaluate(listOf(oil), emptyList(), LocalDate.parse("2027-10-01"), 184500, plan = plan).single()
        val kmDue = Engine.evaluate(listOf(oil), emptyList(), today, 195000, plan = plan).single()
        assertEquals("DUE", dateDue.status)
        assertEquals(listOf("Vence por fecha"), dateDue.reasons)
        assertEquals("DUE", kmDue.status)
        assertEquals(1, kmDue.reasons.size)
        assertEquals(dateDue.generation, kmDue.generation)
        assertEquals(Forecast.project(listOf(dateDue)), Forecast.project(listOf(kmDue)))
    }

    private data class Cost(val date: String, val cents: Long, val category: String, val active: Boolean = true)
    private val costs = listOf(Cost("2025-01-31", 10000, "workshop"), Cost("2026-01-01", 2500, "workshop"),
        Cost("2026-01-15", 60, "fuel"), Cost("2026-02-01", 3000, "workshop"),
        Cost("2026-01-20", 9999, "workshop", false), Cost("2024-03-01", 100, "other", false))
    private fun filtered(filter: ExpenseFilter, items: List<Cost> = costs) = ExpenseFilters.apply(
        items, filter, { it.date }, { it.cents }, { it.category }, { it.active })

    @Test fun monthAcrossAllYearsIncludesCalendarDatesAndExcludesInactiveExpenses() {
        val result = filtered(ExpenseFilter(month = 1))
        assertEquals(costs.take(3), result.rows)
        assertEquals(12560L, result.subtotalCents)
        assertEquals(15560L, result.totalCents)
        assertEquals(listOf(2026, 2025), result.years)
    }

    @Test fun combinableExpenseFiltersAndClearingFiltersKeepAccumulatedTotal() {
        val combined = filtered(ExpenseFilter(month = 1, year = 2026, category = "workshop"))
        assertEquals(listOf(costs[1]), combined.rows)
        assertEquals(2500L, combined.subtotalCents)
        assertEquals(15560L, combined.totalCents)
        val empty = filtered(ExpenseFilter(month = 2, year = 2025))
        assertTrue(empty.rows.isEmpty())
        assertEquals(0L, empty.subtotalCents)
        assertEquals(15560L, empty.totalCents)
        val cleared = filtered(ExpenseFilter())
        assertEquals(costs.filter { it.active }, cleared.rows)
        assertEquals(cleared.totalCents, cleared.subtotalCents)
    }

    @Test fun editingDateCategoryAndVoidingExpenseImmediatelyChangesRowsAndExactTotals() {
        val edited = costs.mapIndexed { index, cost -> if (index == 1) cost.copy(date = "2026-02-10", category = "other", cents = 555) else cost }
        val result = filtered(ExpenseFilter(month = 1, year = 2026, category = "workshop"), edited)
        assertTrue(result.rows.isEmpty())
        assertEquals(0L, result.subtotalCents)
        assertEquals(13615L, result.totalCents)
        val voided = filtered(ExpenseFilter(month = 1), edited.mapIndexed { i, cost -> if (i == 0) cost.copy(active = false) else cost })
        assertEquals(60L, voided.subtotalCents)
        assertEquals(3615L, voided.totalCents)
    }
}
