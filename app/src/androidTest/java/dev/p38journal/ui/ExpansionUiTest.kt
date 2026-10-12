package dev.p38journal.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import dev.p38journal.MainActivity
import dev.p38journal.data.*
import dev.p38journal.domain.Consumption
import dev.p38journal.domain.Tyres
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExpansionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[JournalViewModel::class.java]
    private fun ready() = compose.waitUntil(30_000) { compose.onAllNodesWithText("Actualizar kilómetros").fetchSemanticsNodes().isNotEmpty() }
    private fun saved() = compose.waitUntil(15_000) { !model.busy.value && model.message.value == null }
    private fun reveal(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        if (runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo()
        return node
    }
    private fun click(text: String) = reveal(compose.onNodeWithText(text)).performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun choose(current: String, selected: String) { click(current); click(selected) }
    private fun fill(tag: String, value: String) = reveal(compose.onNodeWithTag(tag)).performTextReplacement(value)
    private fun assertBlank(tag: String) = compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    private fun section(name: String) { compose.onNodeWithContentDescription("Más opciones").performClick(); click(name) }
    private fun waitFor(text: String) {
        try { compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) { throw AssertionError("Esperando $text\n${compose.onRoot(useUnmergedTree = true).printToString()}", error) }
    }

    @Test fun quickManualWorkConvertsSameRecordWithoutHistoricalDateExpenseOrManufacturerEvent(): Unit = runBlocking {
        ready()
        val repo = Repository(compose.activity)
        val token = UUID.randomUUID().toString()
        val title = "FIXTURE mejora libre $token"
        val events = repo.allEvents()
        val readings = repo.dao.allReadings()
        val expenseIds = repo.dao.allExpenses().map { it.id }.toSet()
        var id: String? = null
        try {
            compose.onNodeWithTag("quick_expense").assertExists()
            compose.onNodeWithTag("quick_fuel").assertExists()
            compose.onNodeWithTag("quick_manual_work").performScrollTo().performClick()
            fill("work_name", title)
            fill("work_estimate", "300,50")
            click("Guardar trabajo o mejora"); click("Guardar")
            saved(); waitFor(title)
            val planned = repo.dao.allVisits().single { it.title == title }
            id = planned.id
            assertEquals("planned", planned.status)
            assertEquals(30050L, planned.estimatedCents)
            assertFalse(repo.dao.allExpenses().any { it.visitId == planned.id })
            click(title)
            choose("Previsto", "Realizado")
            assertBlank("work_date")
            assertBlank("work_km")
            click("Guardar trabajo o mejora"); click("Guardar")
            saved(); waitFor(title)
            val done = repo.dao.allVisits().single { it.id == planned.id }
            assertEquals("done", done.status)
            assertEquals("", done.date)
            assertFalse(done.dateExact)
            assertNull(done.km)
            assertNull(done.paidCents)
            assertEquals(1, repo.dao.allVisits().count { it.title == title })
            compose.onNodeWithText("Historial").performClick()
            fill("history_search", title)
            waitFor(title)
            compose.onAllNodes(hasText(title) and !hasSetTextAction()).assertCountEquals(1)
            assertEquals(events, repo.allEvents())
            assertEquals(readings, repo.dao.allReadings())
            assertEquals(expenseIds, repo.dao.allExpenses().map { it.id }.toSet())
        } finally { id?.let { repo.voidVisit(it) } }
    }

    @Test fun manualPlannedWorkLinksExistingExpenseAndRelationOpensBothWays(): Unit = runBlocking {
        ready()
        val repo = Repository(compose.activity)
        val token = UUID.randomUUID().toString()
        val payment = Expense(id = "ui-v6-payment-$token", date = "2025-01-10", cents = 1250, concept = "FIXTURE pago existente $token")
        repo.saveExpense(payment)
        var workId: String? = null
        try {
            compose.onNodeWithTag("quick_manual_work").performScrollTo().performClick()
            fill("work_name", "FIXTURE previsto con pago $token")
            choose("Sin nuevo gasto elegido", "${payment.concept} · ${money(payment.cents)}")
            click("Guardar trabajo o mejora"); click("Guardar"); saved()
            val work = repo.dao.allVisits().single { it.title == "FIXTURE previsto con pago $token" }
            workId = work.id
            assertEquals("planned", work.status)
            assertEquals(work.id, repo.dao.allExpenses().single { it.id == payment.id }.visitId)
            assertEquals(1, repo.dao.allExpenses().count { it.visitId == work.id && it.active })
            click(work.title)
            fill("work_notes", "FIXTURE borrador conservado")
            click("Consultar gasto vinculado")
            compose.onNodeWithTag("expense_amount").assertTextContains("12,50")
            fill("expense_amount", "13,75")
            click("Guardar gasto"); saved()
            compose.onNodeWithTag("work_notes").assertTextContains("FIXTURE borrador conservado")
            click("Consultar gasto vinculado")
            compose.onNodeWithTag("expense_amount").assertTextContains("13,75")
            click("Consultar trabajo vinculado")
            compose.onNodeWithTag("work_name").assertTextContains(work.title)
            compose.onNodeWithTag("work_notes").assertTextContains("FIXTURE borrador conservado")
            choose("Previsto", "Realizado")
            click("Guardar trabajo o mejora"); click("Guardar"); saved()
            assertEquals("done", repo.dao.allVisits().single { it.id == work.id }.status)
            assertEquals("2025-01-10", repo.dao.allExpenses().single { it.id == payment.id }.date)
            assertEquals(1, repo.dao.allExpenses().count { it.visitId == work.id && it.active })
        } finally { workId?.let { repo.voidVisit(it) }; repo.voidExpense(payment.id) }
    }

    @Test fun historicalManualWorkLinksMaintenanceUsingKnownKmWithoutInventingDate(): Unit = runBlocking {
        ready()
        val repo = Repository(compose.activity)
        val beforeReadings = repo.dao.allReadings()
        val token = UUID.randomUUID().toString()
        val work = Visit(id = "ui-v6-history-$token", date = "", dateExact = false, km = 180000, title = "FIXTURE trabajo sin fecha $token", notes = "FIXTURE nota inicial amigable", manual = true)
        repo.saveManualWork(work)
        try {
            section("Trabajos y mejoras")
            choose("Previstos", "Realizados")
            waitFor(work.title); click(work.title)
            assertBlank("work_date")
            click("Vincular operación de mantenimiento")
            assertBlank("visit_date")
            val oil = repo.tasks().single { it.rule.id == "engine-oil-filter" }
            choose("Sin operación de mantenimiento", friendlyTitle(oil))
            fill("Presupuesto EUR (no se suma a gastos)", "50,00")
            click("Guardar trabajo"); click("Sí, se ha realizado"); saved()
            val linked = repo.allEvents().single { it.visitId == work.id && it.active }
            assertEquals("engine-oil-filter", linked.ruleId)
            assertNull(linked.date)
            assertFalse(linked.dateExact)
            assertEquals(180000, linked.km)
            assertEquals("", repo.dao.allVisits().single { it.id == work.id }.date)
            assertEquals(1, repo.dao.allVisits().count { it.id == work.id })
            assertEquals(beforeReadings, repo.dao.allReadings())
            assertEquals(200000, repo.tasks().single { it.rule.id == oil.rule.id }.dueKm)
            click(work.title)
            choose(friendlyTitle(oil), "Sin operación de mantenimiento")
            click("Guardar trabajo"); click("Sí, se ha realizado"); saved()
            val structuredBefore = JsonParser.parseString(repo.dao.allVisits().single { it.id == work.id }.notes).asJsonObject
            assertEquals("50,00", structuredBefore.get("budget").asString)
            click(work.title)
            compose.onNodeWithTag("work_notes").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("FIXTURE nota inicial amigable")))
            fill("work_notes", "FIXTURE nota manual actualizada")
            click("Guardar trabajo o mejora"); click("Guardar"); saved()
            val structuredAfter = JsonParser.parseString(repo.dao.allVisits().single { it.id == work.id }.notes).asJsonObject
            assertEquals("FIXTURE nota manual actualizada", structuredAfter.get("notes").asString)
            assertEquals(structuredBefore.get("budget"), structuredAfter.get("budget"))
            assertEquals(structuredBefore.get("lines"), structuredAfter.get("lines"))
            assertFalse(repo.allEvents().any { it.visitId == work.id && it.active })
            assertFalse(repo.dao.allExpenses().any { it.visitId == work.id && it.active })
            assertEquals(beforeReadings, repo.dao.allReadings())
        } finally { repo.voidVisit(work.id) }
    }

    @Test fun fuelQuickFormUsesDecimalLitresOneExpenseAndNoSilentOdometerUpdate(): Unit = runBlocking {
        ready()
        val repo = Repository(compose.activity)
        val token = UUID.randomUUID().toString()
        val oldReading = repo.currentKm()
        val base = FuelEntry(id = "ui-v6-base-$token", date = "2024-01-01", km = 175000, litresMilli = 60000, totalCents = 9000, full = true)
        val partial = FuelEntry(id = "ui-v6-partial-$token", date = "2024-01-02", km = 175200, litresMilli = 20000, totalCents = 3000, full = false)
        repo.saveFuelEntry(base); repo.saveFuelEntry(partial)
        var finalId: String? = null
        try {
            compose.onNodeWithTag("quick_fuel").performScrollTo().performClick()
            fill("fuel_date", "2024-01-03")
            fill("fuel_km", "175500")
            fill("fuel_litres", "50,000")
            fill("fuel_amount", "75.50")
            compose.onNodeWithTag("fuel_full").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithTag("fuel_update_reading").assertDoesNotExist()
            click("Guardar repostaje"); saved()
            val final = repo.dao.allFuelEntries().single { it.date == "2024-01-03" && it.km == 175500 && it.active }
            finalId = final.id
            assertEquals(50000L, final.litresMilli)
            assertEquals(7550L, final.totalCents)
            assertEquals(oldReading, repo.currentKm())
            assertEquals("14.00", Consumption.calculate(listOf(base, partial, final)).average!!.toPlainString())
            assertEquals(1, repo.dao.allExpenses().count { it.active && it.fuelId == final.id })
            click("${dateLabel(final.date)} · ${kmLabel(final.km)}")
            click("Consultar gasto de gasolina")
            compose.onNodeWithTag("expense_amount").assertTextContains("75,50")
            fill("expense_amount", "80,25")
            click("Guardar gasto"); saved()
            assertEquals(8025L, repo.dao.allFuelEntries().single { it.id == final.id }.totalCents)
            compose.onNodeWithTag("fuel_amount").assertTextContains("80,25")
            click("Guardar repostaje"); saved()
            assertEquals(8025L, repo.dao.allExpenses().single { it.fuelId == final.id && it.active }.cents)
            assertEquals(1, repo.dao.allExpenses().count { it.fuelId == final.id && it.active })
            click("${dateLabel(final.date)} · ${kmLabel(final.km)}")
            click("Anular repostaje"); click("Anular"); saved()
            assertFalse(repo.dao.allFuelEntries().single { it.id == final.id }.active)
            assertFalse(repo.dao.allExpenses().single { it.id == final.expenseId }.active)
        } finally {
            finalId?.let { repo.voidFuelEntry(it) }; repo.voidFuelEntry(base.id); repo.voidFuelEntry(partial.id)
        }
    }

    @Test fun deadlineFreeNameRenewsManuallyPreservesHistoryAndDeactivates(): Unit = runBlocking {
        ready()
        val repo = Repository(compose.activity)
        val name = "FIXTURE documentación libre ${UUID.randomUUID()}"
        val due = LocalDate.parse(model.today()).plusDays(10).toString()
        var id: String? = null
        val expenses = repo.dao.allExpenses()
        try {
            section("Documentación y vencimientos")
            click("Añadir documentación")
            fill("deadline_name", name)
            fill("deadline_due", due)
            fill("deadline_repeat", "12")
            click("Guardar documentación"); saved(); waitFor(name)
            val original = repo.dao.allDeadlines().single { it.name == name }
            id = original.id
            assertTrue(repo.dao.allDeadlineRenewals().none { it.deadlineId == original.id })
            click(name)
            click("Registrar renovación")
            val renewed = LocalDate.parse(due).plusMonths(12).plusDays(2).toString()
            fill("deadline_renew_date", renewed)
            compose.onAllNodesWithText("Confirmar renovación").onLast().performClick()
            saved(); waitFor(name)
            val history = repo.dao.allDeadlineRenewals().single { it.deadlineId == original.id }
            assertEquals(due, history.previousDueDate)
            assertEquals(renewed, history.newDueDate)
            assertEquals(renewed, repo.dao.allDeadlines().single { it.id == original.id }.dueDate)
            click(name); click("Desactivar entrada"); click("Desactivar"); saved()
            assertFalse(repo.dao.allDeadlines().single { it.id == original.id }.active)
            assertEquals(expenses, repo.dao.allExpenses())
        } finally { id?.let { entryId -> repo.dao.allDeadlines().firstOrNull { it.id == entryId }?.let { repo.saveDeadline(it.copy(active = false)) } } }
    }

    @Test fun tyreMountReviewAndBothChangePreserveUsageAndSinglePayment(): Unit = runBlocking {
        ready()
        val repo = Repository(compose.activity)
        val existingIds = repo.dao.allTyreEvents().map { it.id }.toSet()
        val originalKm = repo.currentKm()
        fun newEvents() = runBlocking { repo.dao.allTyreEvents().filter { it.id !in existingIds } }
        try {
            section("Ruedas")
            click("Ruedas delanteras")
            if (Tyres.evaluate(repo.dao.allTyreEvents(), null).first { it.axis == "front" }.mount != null) {
                throw AssertionError("Este smoke test requiere debug sin montaje activo de delanteras")
            }
            click("Registrar montaje")
            assertBlank("tyre_km")
            fill("tyre_km", "175000"); fill("tyre_interval", "10000")
            click("Guardar ruedas"); click("Guardar"); saved()
            val mount = newEvents().single { it.axis == "front" && it.kind == "mount" }
            assertNull(mount.date)
            assertNull(mount.visitId)
            click("Revisadas")
            fill("tyre_km", "185200"); fill("tyre_interval", "10000")
            click("Guardar ruedas"); click("Guardar"); saved()
            val reviewed = Tyres.evaluate(repo.dao.allTyreEvents(), 185200).first { it.axis == "front" }
            assertEquals(10200, reviewed.usedKm)
            assertEquals(195200, reviewed.dueKm)
            assertEquals(mount.id, reviewed.mount!!.id)
            section("Ruedas"); click("Cambiar ambas")
            fill("tyre_km", "195300"); fill("tyre_date", model.today())
            fill("tyre_interval", "11000"); fill("tyre_interval_rear", "12000")
            fill("tyre_paid", "250,40")
            click("Guardar ruedas"); click("Guardar"); saved()
            val changes = newEvents().filter { it.kind == "change" }
            assertEquals(2, changes.size)
            assertEquals(1, changes.map { it.visitId }.distinct().size)
            assertEquals(1, repo.dao.allExpenses().count { it.active && it.visitId == changes.first().visitId })
            assertEquals(25040L, repo.dao.allExpenses().single { it.active && it.visitId == changes.first().visitId }.cents)
            assertEquals(listOf(0, 0), Tyres.evaluate(repo.dao.allTyreEvents(), 195300).map { it.usedKm })
            assertEquals(originalKm, repo.currentKm())
        } finally {
            newEvents().mapNotNull { it.visitId }.distinct().forEach { repo.voidVisit(it) }
            newEvents().filter { it.kind == "mount" && it.active }.forEach { repo.voidTyreEvent(it.id) }
        }
    }
}
