package dev.p38journal.review

import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.p38journal.domain.Engine
import dev.p38journal.domain.Event
import dev.p38journal.domain.PlanningReference
import dev.p38journal.domain.Rule
import dev.p38journal.ui.maintenanceGroups
import dev.p38journal.ui.projectedMaintenanceGroups
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ProjectedMaintenanceGroupsTest {
    private val rules = JsonParser.parseString(File("src/main/assets/maintenance-catalogue.json").readText()).asJsonObject
        .getAsJsonArray("rules").map { element ->
            val json = element.asJsonObject.deepCopy()
            if (!json.has("origin")) json.addProperty("origin", "manufacturer")
            if (!json.has("notes")) json.addProperty("notes", "")
            if (!json.has("enabled")) json.addProperty("enabled", true)
            Gson().fromJson(json, Rule::class.java)
        }
    private val plan = PlanningReference(baselineDate = "2026-10-01")
    private val today = LocalDate.parse(plan.baselineDate)
    private val common = setOf(
        "seats-belts", "lights-horn", "wipers-washers", "handbrake", "headlight-alignment",
        "brake-mechanisms", "abs-sensor-wiring", "radiator-fan", "locks-hinges", "cooling-hoses",
        "pollen-filters", "auxiliary-belt-inspection", "battery-condition", "engine-oil-filter",
        "propshaft-lubrication", "underbody-lines", "steering-suspension-lines", "powertrain-leaks",
        "exhaust-inspection", "chassis-fasteners", "steering-balljoints", "air-springs-lines",
        "dampers-height-sensors", "road-test", "service-report", "tyre-service-inspection",
        "fluid-reservoirs-service"
    )
    private val b = setOf("transfer-level", "axle-level")
    private val c = setOf("remote-batteries", "eas-compressor-service", "air-filter", "steering-shaft", "automatic-fluid", "transfer-fluid")
    private fun tasks(inputRules: List<Rule> = rules, events: List<Event> = emptyList(), date: LocalDate = today, km: Int = 175000) =
        Engine.evaluate(inputRules, events, date, km, plan = plan)

    @Test fun mainScreenGroupsContainExactCompleteSetsAndOnlyRealRegistrationSubsets() {
        val real = tasks()
        val snapshot = real.toList()
        val groups = projectedMaintenanceGroups(real)
        assertEquals(listOf(195000, 215000, 235000, 255000), groups.map { it.dueKm })
        assertEquals(listOf(29, 33, 31, 33), groups.map { it.occurrences.size })
        val expected = listOf(common + b, common + c, common + b + setOf("coolant-renewal", "brake-fluid"), common + c)
        groups.zip(expected).forEach { (group, ids) ->
            assertEquals(ids, group.occurrences.map { it.rule.id }.toSet())
            assertEquals(ids.size, group.occurrences.size)
            assertTrue(group.occurrences.all { it.dueKm == group.dueKm && it.dueDate == group.dueDate })
            assertEquals(group, groups.single { it.id == group.id })
        }
        assertEquals(listOf("2027-10-01", "2028-10-01", "2029-10-01", "2030-10-01"), groups.map { it.dueDate })
        assertEquals(common + b, groups[0].actualTasks(real).map { it.rule.id }.toSet())
        assertEquals(c, groups[1].actualTasks(real).map { it.rule.id }.toSet())
        assertEquals(setOf("coolant-renewal", "brake-fluid"), groups[2].actualTasks(real).map { it.rule.id }.toSet())
        assertTrue(groups[3].actualTasks(real).isEmpty())
        assertTrue(groups[1].occurrences.filter { it.rule.id in common }.all { it.assumesPrevious })
        assertTrue(groups[2].occurrences.filter { it.rule.id in b }.all { it.sequence == 1 })
        assertEquals(snapshot, real)
        assertTrue(real.all { it.lastEvent == null })
    }

    @Test fun partialOilAt195200KeepsExactSeparateGroupAndLeavesOtherOperationsPending() {
        val work = Event("fixture-oil-v5", "fixture-visit-v5", "engine-oil-filter", "2027-10-15", 195200)
        val real = tasks(events = listOf(work), date = LocalDate.parse(work.date), km = 195200)
        val groups = projectedMaintenanceGroups(real)
        val original = groups.single { it.dueKm == 195000 }
        assertEquals(common + b - "engine-oil-filter", original.occurrences.map { it.rule.id }.toSet())
        assertEquals(28, original.actualTasks(real).size)
        assertTrue(original.actualTasks(real).all { it.status == "DUE" })
        val forty = groups.single { it.dueKm == 215000 }
        assertEquals(common + c - "engine-oil-filter", forty.occurrences.map { it.rule.id }.toSet())
        assertEquals(32, forty.occurrences.size)
        val oil = groups.single { it.dueKm == 215200 }
        assertEquals("2028-10-15", oil.dueDate)
        assertEquals(listOf("engine-oil-filter"), oil.occurrences.map { it.rule.id })
        assertEquals(listOf("engine-oil-filter"), oil.actualTasks(real).map { it.rule.id })
        assertEquals(1, real.count { it.lastEvent != null })
        assertEquals(work, real.single { it.rule.id == "engine-oil-filter" }.lastEvent)
        assertEquals(175000, plan.baselineKm)
    }

    @Test fun ownDateOnlyAndUniqueRemainAccessibleOutsideHorizonAndCompletedUniqueDoesNotRepeat() {
        val recurring = Rule("fixture-date-only", "Owner calendar operation", "owner", "check", months = 1,
            origin = "custom", enabled = false)
        val unique = Rule("fixture-unique", "Owner unique operation", "owner", "check", recurrence = "once",
            origin = "custom", dueKm = 330000)
        val input = rules + recurring + unique
        val real = tasks(input)
        val groups = projectedMaintenanceGroups(real)
        val calendarGroup = groups.single { group -> group.occurrences.any { it.rule.id == recurring.id } }
        assertNull(calendarGroup.dueKm)
        assertEquals("2026-11-01", calendarGroup.dueDate)
        assertEquals(1, calendarGroup.occurrences.size)
        assertEquals(0, calendarGroup.occurrences.single().sequence)
        assertFalse(calendarGroup.actualTasks(real).single().rule.enabled)
        val uniqueGroup = groups.single { group -> group.occurrences.any { it.rule.id == unique.id } }
        assertEquals(330000, uniqueGroup.dueKm)
        assertEquals(1, uniqueGroup.occurrences.size)
        val completed = tasks(input, listOf(Event("fixture-once-work", "fixture-once-visit", unique.id, plan.baselineDate, null)))
        assertTrue(projectedMaintenanceGroups(completed).none { group -> group.occurrences.any { it.rule.id == unique.id } })
        assertNull(completed.single { it.rule.id == unique.id }.dueKm)
    }

    @Test fun farPersonalizedWorkshopTaskDoesNotAppendAnIncompleteFutureRevision() {
        val real = tasks().map { task -> if (task.rule.id == "transfer-level") task.copy(
            rule = task.rule.copy(origin = "personalized"), dueKm = 315000, dueDate = "2033-10-01"
        ) else task }
        val groups = projectedMaintenanceGroups(real)
        assertEquals(listOf(195000, 215000, 235000, 255000), groups.map { it.dueKm })
        assertTrue(groups.none { it.dueKm == 315000 })
        assertTrue(maintenanceGroups(real).any { group -> group.tasks.any { it.rule.id == "transfer-level" && it.dueKm == 315000 } })
        assertEquals(common + c, groups.single { it.dueKm == 255000 }.occurrences.map { it.rule.id }.toSet())
    }

    @Test fun registrationSubsetRejectsSameRuleWithDifferentCurrentDeadline() {
        val real = tasks()
        val group = projectedMaintenanceGroups(real).single { it.dueKm == 215000 }
        val changed = real.map { if (it.rule.id == "air-filter") it.copy(dueKm = 225000) else it }
        assertEquals(c - "air-filter", group.actualTasks(changed).map { it.rule.id }.toSet())
        assertEquals(33, group.occurrences.size)
        assertEquals(common + c, group.occurrences.map { it.rule.id }.toSet())
    }
}
