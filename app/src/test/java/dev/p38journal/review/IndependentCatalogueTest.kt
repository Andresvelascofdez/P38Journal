package dev.p38journal.review

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.p38journal.domain.Engine
import dev.p38journal.domain.Event
import dev.p38journal.domain.Rule
import dev.p38journal.domain.PlanningReference
import dev.p38journal.ui.maintenanceGroups
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class IndependentCatalogueTest {
    private val root = JsonParser.parseString(File("src/main/assets/maintenance-catalogue.json").readText()).asJsonObject
    private val rules = root.getAsJsonArray("rules").map { Gson().fromJson(it, Rule::class.java) }.associateBy { it.id }
    private val plan = PlanningReference(baselineDate = "2026-10-01")

    @Test fun publishedMatrixHasCompleteCoverageAndExplicitExclusions() {
        val coverage = root.getAsJsonArray("coverage").map { it.asJsonObject }
        assertEquals((1..59).toList(), coverage.map { it.get("row").asInt }.sorted())
        assertEquals(setOf(1, 17, 18, 29, 36, 38, 39), coverage.filter { it.get("decision").asString == "excluded" }.map { it.get("row").asInt }.toSet())
        assertTrue(coverage.flatMap { it.getAsJsonArray("rule_ids").map { id -> id.asString } }.all { it in rules })
    }

    @Test fun secondaryManufacturerConditionsKeepReadableNumbers() {
        val splitDigits = Regex("(?<=\\d)\\s+(?=\\d)")
        for (rule in rules.values) assertFalse("Split numeric source text in ${rule.id}", splitDigits.containsMatchIn(rule.condition))
    }

    @Test fun glossaryIntervalsRunFromActualReplacementWithoutFactoryCounterRounding() {
        val verified = mapOf("coolant-renewal" to (36 to 60000), "spark-plugs" to (72 to 120000),
            "auxiliary-belt-renewal" to (60 to 100000), "evap-service" to (96 to 160000), "axle-fluid" to (96 to 160000))
        for ((id, interval) in verified) {
            val rule = rules.getValue(id)
            assertEquals("Source calendar interval $id", interval.first, rule.months)
            assertEquals("Source mileage interval $id", interval.second, rule.km)
            val event = Event("isolated-$id", "isolated-visit", id, "2026-10-15", 176000)
            val task = Engine.evaluate(listOf(rule), listOf(event), LocalDate.parse("2026-10-16"), 176000, plan = plan).single()
            assertEquals("Next limit $id", 176000 + interval.second, task.dueKm)
            assertEquals(LocalDate.parse("2026-10-15").plusMonths(interval.first.toLong()).toString(), task.dueDate)
        }
    }

    @Test fun manufacturerFirstOnlyAutomaticFilterDoesNotBecomeRepeatedReplacement() {
        val filter = rules.getValue("automatic-filter-history")
        assertNull(filter.months); assertNull(filter.km)
        assertTrue(filter.recurrence in setOf("initial", "once"))
        val event = Event("isolated-filter", "isolated-visit", filter.id, "2026-10-15", 176000)
        val task = Engine.evaluate(listOf(filter), listOf(event), LocalDate.parse("2030-10-15"), 300000).single()
        assertEquals("PLANNED", task.status); assertNull(task.dueDate); assertNull(task.dueKm)
    }

    @Test fun partialExecutionKeepsExplicitBaselineWithoutInventingMissingMileage() {
        val oil = rules.getValue("engine-oil-filter")
        val old = Event("old-isolated", "old-visit", oil.id, "2025-10-15", 150000)
        val latest = Event("latest-isolated", "latest-visit", oil.id, "2026-10-15", null)
        val task = Engine.evaluate(listOf(oil), listOf(old, latest), LocalDate.parse("2027-10-15"), null, plan = plan).single()
        assertEquals("2027-10-15", task.dueDate); assertEquals(195000, task.dueKm); assertEquals("DUE", task.status)
    }

    @Test fun phaseOffsetsPreserveFirstBThenCAndTheirOwnCompletedCycles() {
        val b = rules.getValue("transfer-level")
        val c = rules.getValue("transfer-fluid")
        assertEquals(12, b.firstMonths); assertEquals(20000, b.firstKm)
        assertEquals(24, c.firstMonths); assertEquals(40000, c.firstKm)
        assertEquals(24, b.months); assertEquals(40000, b.km)
        assertEquals(24, c.months); assertEquals(40000, c.km)
        val initial = Engine.evaluate(listOf(b, c), emptyList(), LocalDate.parse("2026-10-15"), 175000, plan = plan).associateBy { it.rule.id }
        assertEquals(195000, initial.getValue(b.id).dueKm)
        assertEquals("2027-10-01", initial.getValue(b.id).dueDate)
        assertEquals(215000, initial.getValue(c.id).dueKm)
        assertEquals("2028-10-01", initial.getValue(c.id).dueDate)
        val completed = Event("real-b", "visit-b", b.id, "2027-10-05", 195200)
        val after = Engine.evaluate(listOf(b, c), listOf(completed), LocalDate.parse("2027-10-06"), 195200, plan = plan).associateBy { it.rule.id }
        assertEquals(235200, after.getValue(b.id).dueKm)
        assertEquals("2029-10-05", after.getValue(b.id).dueDate)
        assertEquals(215000, after.getValue(c.id).dueKm)
    }

    @Test fun routineManualControlsDoNotRemoveTheirWorkshopOperations() {
        assertEquals("manual", rules.getValue("tyre-check").scheduleVisibility)
        val tyre = rules.getValue("tyre-service-inspection")
        val fluids = rules.getValue("fluid-reservoirs-service")
        assertEquals("service", tyre.scheduleVisibility); assertEquals("service", fluids.scheduleVisibility)
        assertEquals("workshop", tyre.serviceGroup); assertEquals("workshop", fluids.serviceGroup)
        assertEquals(12, tyre.months); assertEquals(20000, tyre.km)
        assertEquals(12, fluids.months); assertEquals(20000, fluids.km)
        val tasks = Engine.evaluate(listOf(tyre, fluids, rules.getValue("tyre-check")), emptyList(), LocalDate.parse("2027-10-01"), 195000, plan = plan).associateBy { it.rule.id }
        assertEquals("DUE", tasks.getValue(tyre.id).status)
        assertEquals("DUE", tasks.getValue(fluids.id).status)
        assertNull(tasks.getValue("tyre-check").dueDate); assertNull(tasks.getValue("tyre-check").dueKm)
    }

    @Test fun originalSrsAgeDoesNotUseMigrationDateAsManufactureDate() {
        for (id in listOf("front-airbag-service", "side-airbag-service", "pretensioner-applicability")) {
            val rule = rules.getValue(id)
            assertEquals("conditional", rule.scheduleVisibility)
            val task = Engine.evaluate(listOf(rule), emptyList(), LocalDate.parse("2047-10-01"), 300000, plan = plan).single()
            assertEquals("PLANNED", task.status); assertNull(task.dueDate); assertNull(task.dueKm)
        }
    }

    @Test fun sourceReplacementIntervalsUseOperationalBaseAtExactBoundary() {
        val oil = rules.getValue("engine-oil-filter")
        val before = Engine.evaluate(listOf(oil), emptyList(), LocalDate.parse("2026-10-15"), 194999, plan = plan).single()
        assertEquals(195000, before.dueKm); assertEquals("2027-10-01", before.dueDate)
        assertEquals("SOON", before.status)
        val reached = Engine.evaluate(listOf(oil), emptyList(), LocalDate.parse("2026-10-15"), 195000, plan = plan).single()
        assertEquals("DUE", reached.status)
        assertEquals(before.generation, reached.generation)
        assertEquals(listOf("Vence por kilómetros"), reached.reasons)
    }

    @Test fun prepurchaseEvidenceNeverBecomesACompletedServiceAtOperationalBase() {
        val oil = rules.getValue("engine-oil-filter")
        val recovered = Event("recovered", "recovered-visit", oil.id, "2026-10-05", 182000, provenance = "prepurchase_recovered")
        val task = Engine.evaluate(listOf(oil), listOf(recovered), LocalDate.parse("2026-10-15"), 182000, plan = plan).single()
        assertEquals(195000, task.dueKm); assertEquals("2027-10-01", task.dueDate)
        assertNull(task.lastEvent); assertEquals("PLANNED", task.status)
    }

    @Test fun coincidentWorkshopLimitsGroupAllOperationsAndPartialWorkSplitsOnlyItsOperation() {
        val initial = Engine.evaluate(rules.values.toList(), emptyList(), LocalDate.parse("2026-10-15"), 175000, plan = plan)
        val groups = maintenanceGroups(initial)
        assertEquals(6, groups.size)
        assertEquals(42, groups.sumOf { it.tasks.size })
        val first = groups.single { it.dueKm == 195000 }
        assertEquals(29, first.tasks.size)
        assertTrue(first.tasks.any { it.rule.id == "engine-oil-filter" })
        assertTrue(first.tasks.any { it.rule.id == "transfer-level" })
        val oilWork = Event("real-oil", "visit-oil", "engine-oil-filter", "2026-10-15", 175200)
        val after = maintenanceGroups(Engine.evaluate(rules.values.toList(), listOf(oilWork), LocalDate.parse("2026-10-16"), 175200, plan = plan))
        assertEquals(42, after.sumOf { it.tasks.size })
        assertEquals(28, after.single { it.dueKm == 195000 }.tasks.size)
        assertEquals(listOf("engine-oil-filter"), after.single { it.dueKm == 195200 }.tasks.map { it.rule.id })
        assertEquals(groups.flatMap { it.tasks }.map { it.rule.id }.toSet(), after.flatMap { it.tasks }.map { it.rule.id }.toSet())
    }
}
