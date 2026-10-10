package dev.p38journal.review

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.p38journal.domain.Engine
import dev.p38journal.domain.Event
import dev.p38journal.domain.Rule
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class IndependentCatalogueTest {
    private val root = JsonParser.parseString(File("src/main/assets/maintenance-catalogue.json").readText()).asJsonObject
    private val rules = root.getAsJsonArray("rules").map { Gson().fromJson(it, Rule::class.java) }.associateBy { it.id }

    @Test fun publishedMatrixHasCompleteCoverageAndExplicitExclusions() {
        val coverage = root.getAsJsonArray("coverage").map { it.asJsonObject }
        assertEquals((1..59).toList(), coverage.map { it.get("row").asInt }.sorted())
        assertEquals(setOf(1, 17, 18, 29, 36, 38, 39), coverage.filter { it.get("decision").asString == "excluded" }.map { it.get("row").asInt }.toSet())
        assertTrue(coverage.flatMap { it.getAsJsonArray("rule_ids").map { id -> id.asString } }.all { it in rules })
    }

    @Test fun glossaryIntervalsRunFromActualReplacementWithoutFactoryCounterRounding() {
        val verified = mapOf("coolant-renewal" to (36 to 60000), "spark-plugs" to (72 to 120000),
            "auxiliary-belt-renewal" to (60 to 100000), "evap-service" to (96 to 160000), "axle-fluid" to (96 to 160000))
        for ((id, interval) in verified) {
            val rule = rules.getValue(id)
            assertEquals("Source calendar interval $id", interval.first, rule.months)
            assertEquals("Source mileage interval $id", interval.second, rule.km)
            val event = Event("isolated-$id", "isolated-visit", id, "2026-10-15", 176000)
            val task = Engine.evaluate(listOf(rule), listOf(event), LocalDate.parse("2026-10-16"), 176000).single()
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

    @Test fun exactNewDateWithUnknownMileageDoesNotRecyclePreviousReplacementMileage() {
        val oil = rules.getValue("engine-oil-filter")
        val old = Event("old-isolated", "old-visit", oil.id, "2025-10-15", 150000)
        val latest = Event("latest-isolated", "latest-visit", oil.id, "2026-10-15", null)
        val task = Engine.evaluate(listOf(oil), listOf(old, latest), LocalDate.parse("2027-10-15"), null).single()
        assertEquals("2027-10-15", task.dueDate); assertNull(task.dueKm); assertEquals("DUE", task.status)
    }
}
