package dev.p38journal.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ConditionalProfilesTest {
    private fun rule(id: String) = Rule(id, "Comprobar aplicabilidad", "fixture-system", "professional_inspection",
        source = "M01 edición y páginas de fixture", sourceUrl = "https://example.invalid/manual.pdf",
        condition = "Condición original", recurrence = "condition")

    @Test fun allThreeProfilesKeepSourceAndDefineOnlyTheirVerifiedIntervals() {
        val fuel = ConditionalProfiles.proposed(rule("fuel-filter-applicability"))!!
        assertEquals("fuel-filter-applicability-active", fuel.id)
        assertEquals("replace", fuel.operation); assertEquals(48, fuel.months); assertEquals(80000, fuel.km); assertNull(fuel.days)
        val brakes = ConditionalProfiles.proposed(rule("offroad-brakes"))!!
        assertEquals("professional_inspection", brakes.operation); assertEquals(1500, brakes.km); assertNull(brakes.days); assertNull(brakes.months)
        val tyres = ConditionalProfiles.proposed(rule("offroad-tyres"))!!
        assertEquals("check", tyres.operation); assertEquals(1, tyres.days); assertNull(tyres.km); assertNull(tyres.months)
        listOf(fuel, brakes, tyres).forEach { profile ->
            assertEquals("M01 edición y páginas de fixture", profile.source)
            assertEquals("https://example.invalid/manual.pdf", profile.sourceUrl)
            assertEquals("component", profile.recurrence)
            assertTrue(profile.condition.contains("Condición original")); assertEquals(profile.title, profile.initialAction)
        }
        assertNull(ConditionalProfiles.proposed(rule("ordinary-rule")))
    }

    @Test fun applicabilityInspectionNeverSatisfiesActivatedReplacement() {
        val sourceRule = rule("fuel-filter-applicability")
        val active = ConditionalProfiles.proposed(sourceRule)!!
        val inspection = Event("inspection", "visit", sourceRule.id, "2026-10-10", 176000)
        val task = Engine.evaluate(listOf(active), listOf(inspection), LocalDate.parse("2026-10-10"), 176000).single()
        assertEquals("INITIAL", task.status); assertNull(task.lastEvent); assertNull(task.dueDate); assertNull(task.dueKm)
        val replacement = inspection.copy(id = "replacement", ruleId = active.id)
        val performed = Engine.evaluate(listOf(active), listOf(inspection, replacement), LocalDate.parse("2026-10-10"), 176000).single()
        assertEquals("2030-10-10", performed.dueDate); assertEquals(256000, performed.dueKm)
    }

    @Test fun inactiveProfileNeverCalculatesDueLimitsAndReactivationPreservesRealEvents() {
        val profile = ConditionalProfiles.proposed(rule("offroad-brakes"))!!
        val completed = Event("real-work", "visit", profile.id, "2026-10-10", 176000)
        val inactive = Engine.evaluate(listOf(profile.copy(recurrence = "inactive")), listOf(completed), LocalDate.parse("2027-10-10"), 200000).single()
        assertEquals("PLANNED", inactive.status); assertNull(inactive.dueDate); assertNull(inactive.dueKm)
        assertEquals(listOf("Perfil desactivado"), inactive.reasons)
        val active = Engine.evaluate(listOf(profile), listOf(completed), LocalDate.parse("2027-10-10"), 200000).single()
        assertEquals("DUE", active.status); assertEquals(177500, active.dueKm)
        assertNotEquals(inactive.generation, active.generation)
    }
}
