package dev.p38journal.ui

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import dev.p38journal.MainActivity
import dev.p38journal.data.Repository
import dev.p38journal.platform.Files
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class JournalUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForApp() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Actualizar kilómetros").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitForText(text: String, timeout: Long = 15_000) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            throw AssertionError("Pantalla esperada: $text\n${compose.onRoot(useUnmergedTree = true).printToString()}", error)
        }
    }
    private fun waitForSaveFeedback() {
        val model = ViewModelProvider(compose.activity)[JournalViewModel::class.java]
        compose.waitUntil(10_000) { !model.busy.value && model.message.value == null }
    }
    private fun choose(current: String, option: String) {
        compose.onNodeWithText(current).performScrollTo().performClick()
        compose.onNodeWithText(option).performClick()
    }

    @Test fun expenseFormSurvivesRecreationAndSavesExactCentsWithoutChangingMaintenance(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val beforeEvents = repository.allEvents()
        val beforeKm = repository.currentKm()
        val concept = "FIXTURE UI ${UUID.randomUUID()}"
        compose.onNodeWithText("Gastos").performClick()
        compose.onNodeWithText("Añadir gasto").performClick()
        compose.onNodeWithTag("expense_amount").performTextInput("12,50")
        compose.onNodeWithTag("expense_concept").performTextInput(concept)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("expense_amount").assertTextContains("12,50")
        compose.onNodeWithTag("expense_concept").assertTextContains(concept)
        compose.onNodeWithText("Guardar gasto").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { repository.dao.allExpenses().any { it.concept == concept && it.active } } }
        val saved = repository.dao.allExpenses().single { it.concept == concept && it.active }
        assertEquals(1250L, saved.cents)
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeKm, repository.currentKm())
        compose.onNodeWithText(concept).performScrollTo().assertExists()
        repository.voidExpense(saved.id)
    }

    @Test fun visitRequiresExplicitCompletionAndKeepsUnselectedOperationsOpen(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val title = "FIXTURE trabajo UI ${UUID.randomUUID()}"
        val beforeEvents = repository.allEvents()
        compose.onNodeWithText("Registrar trabajo").performClick()
        compose.onNodeWithTag("visit_title").performTextInput(title)
        compose.onNodeWithTag("line_title_0").performScrollTo().performTextInput("Revisión visual sin operaciones periódicas satisfechas")
        compose.onNodeWithTag("line_completed_0").performScrollTo().performClick()
        compose.onNodeWithText("Guardar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar trabajo realizado").assertExists()
        assertFalse(repository.dao.allVisits().any { it.title == title })
        compose.onNodeWithText("Sí, se ha realizado").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.dao.allVisits().any { it.title == title && it.active } } }
        val visit = repository.dao.allVisits().single { it.title == title }
        assertEquals(1, repository.visitLines(visit.id).size)
        assertEquals(beforeEvents, repository.allEvents())
        repository.voidVisit(visit.id)
    }

    @Test fun groupedReviewRecordsOnlySelectedOperationWithPrefilledDateAndKilometres(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val group = maintenanceGroups(repository.tasks()).first { it.tasks.size > 1 }
        val selected = group.tasks.firstOrNull { it.rule.id == "engine-oil-filter" } ?: group.tasks.first()
        val selectedIndex = group.tasks.indexOf(selected)
        val baseline = repository.planningReference()
        val beforeKm = repository.currentKm()
        val expectedKm = beforeKm ?: baseline.baselineKm
        val title = "FIXTURE revisión parcial ${UUID.randomUUID()}"
        compose.onNodeWithContentDescription("Mantenimiento").performClick()
        compose.onAllNodesWithText(group.title).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Registrar realizado").assertExists()
        compose.onNodeWithText("Ver fuente del fabricante").assertExists()
        compose.onNodeWithText("Qué incluye").performScrollTo().performClick()
        compose.onNodeWithText(friendlyTitle(selected)).performScrollTo().assertExists()
        compose.onNodeWithText("Registrar realizado").performScrollTo().performClick()
        compose.onNodeWithTag("visit_km").assertTextContains(expectedKm.toString())
        compose.onNodeWithTag("visit_date").assertTextContains(ViewModelProvider(compose.activity)[JournalViewModel::class.java].today())
        compose.onNodeWithTag("visit_title").performTextReplacement(title)
        compose.onNodeWithText("Guardar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Selecciona al menos un trabajo realizado y escribe su descripción.").assertExists()
        assertFalse(repository.dao.allVisits().any { it.title == title })
        compose.onNodeWithTag("line_completed_$selectedIndex").performScrollTo().performClick()
        compose.onNodeWithText("Guardar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar trabajo realizado").assertExists()
        assertFalse(repository.dao.allVisits().any { it.title == title })
        compose.onNodeWithText("Sí, se ha realizado").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.dao.allVisits().any { it.title == title && it.active } } }
        val saved = repository.dao.allVisits().single { it.title == title }
        assertEquals(listOf(selected.rule.id), repository.allEvents().filter { it.visitId == saved.id && it.active }.map { it.ruleId })
        assertEquals(1, repository.visitLines(saved.id).size)
        assertEquals(beforeKm, repository.currentKm())
        assertEquals(baseline, repository.planningReference())
        waitForSaveFeedback()
        compose.onNodeWithTag("history_search").performTextInput("aceite")
        compose.onNodeWithText(title).performScrollTo().assertExists()
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithTag("line_title_0").performScrollTo().assertTextContains(friendlyTitle(selected))
        compose.onNodeWithText("Guardar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar trabajo realizado").assertExists()
        compose.onNodeWithText("Sí, se ha realizado").performClick()
        waitForSaveFeedback()
        assertEquals(1, repository.allEvents().count { it.visitId == saved.id && it.active })
        repository.voidVisit(saved.id)
    }

    @Test fun editingVisitMetadataPreservesHistoricalAllocationAfterLinkedExpenseCorrection(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val token = UUID.randomUUID().toString()
        val title = "FIXTURE reparto histórico $token"
        val visit = dev.p38journal.data.Visit(id = "ui-allocation-$token", date = ViewModelProvider(compose.activity)[JournalViewModel::class.java].today(), title = title, paidCents = 300)
        val lines = listOf(
            dev.p38journal.data.WorkLine(id = "ui-line-a-$token", visitId = visit.id, title = "Trabajo A", costCents = 150),
            dev.p38journal.data.WorkLine(id = "ui-line-b-$token", visitId = visit.id, title = "Trabajo B", costCents = 150))
        val beforeEvents = repository.allEvents()
        val beforeKm = repository.currentKm()
        try {
            repository.saveVisit(visit, lines, emptyList())
            val payment = repository.dao.allExpenses().single { it.active && it.visitId == visit.id }
            repository.saveExpense(payment.copy(cents = 310))
            compose.onNodeWithText("Historial").performClick()
            waitForText(title)
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithTag("visit_paid").performScrollTo().assertTextContains("3,10")
            compose.onNodeWithTag("Notas").performScrollTo().performTextReplacement("FIXTURE corrección de notas sin cambiar el reparto")
            compose.onNodeWithText("Guardar trabajo").performScrollTo().performClick()
            compose.onNodeWithText("Confirmar trabajo realizado").assertExists()
            compose.onNodeWithText("Sí, se ha realizado").performClick()
            waitForSaveFeedback()
            val edited = repository.dao.allVisits().single { it.id == visit.id }
            assertTrue(edited.notes.contains("corrección de notas"))
            assertEquals(310L, edited.paidCents)
            assertEquals(lines.map { it.id to it.costCents }.toMap(), repository.visitLines(visit.id).associate { it.id to it.costCents })
            assertEquals(310L, repository.dao.allExpenses().single { it.active && it.visitId == visit.id }.cents)
            assertEquals(beforeEvents, repository.allEvents())
            assertEquals(beforeKm, repository.currentKm())
        } finally {
            repository.voidVisit(visit.id)
        }
    }

    @Test fun planningReferenceIsEditableWithoutInventingReadingOrWork(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val original = repository.planningReference()
        val beforeEvents = repository.allEvents()
        val beforeReadings = repository.dao.allReadings()
        val changedDate = java.time.LocalDate.parse(original.baselineDate).minusDays(1).toString()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("planning_date").performTextReplacement(changedDate)
        compose.onNodeWithTag("planning_km").assertTextContains(original.baselineKm.toString())
        compose.onNodeWithText("Guardar referencia del plan").performScrollTo().performClick()
        compose.onNodeWithText("Cambiar referencia del plan").assertExists()
        assertEquals(original, repository.planningReference())
        compose.onNodeWithText("Guardar referencia").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.planningReference().baselineDate == changedDate } }
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeReadings, repository.dao.allReadings())
        repository.updatePlanningReference(original.baselineDate, original.baselineKm)
    }

    @Test fun secondaryReferencesActivateAndDisableConditionalProfileWithoutInventedWork(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val original = repository.tasks().single { it.rule.id == "fuel-filter-applicability" }
        val beforeEvents = repository.allEvents()
        val beforeReadings = repository.dao.allReadings()
        val beforePlanIds = maintenanceGroups(repository.tasks()).flatMap { it.tasks }.map { it.rule.id }.toSet()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Referencias del mantenimiento").performClick()
        compose.onNodeWithText(friendlyTitle(original)).performScrollTo().performClick()
        compose.onNodeWithText("Según condición").assertExists()
        compose.onAllNodesWithText("Plan calculado desde", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Registrar realizado").assertDoesNotExist()
        compose.onNodeWithText("Confirmar aplicabilidad y activar intervalo").assertDoesNotExist()
        compose.onNodeWithText("Ver fuente del fabricante").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar aplicabilidad y activar intervalo").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar y activar perfil").performScrollTo().performClick()
        compose.onNodeWithText("Escribe la evidencia que confirma que la condición se aplica.").assertExists()
        compose.onNodeWithTag("profile_evidence").performScrollTo().performTextInput("FIXTURE UI: condición simulada para prueba debug, sin VIN real")
        compose.onNodeWithText("Confirmar y activar perfil").performScrollTo().performClick()
        compose.onNodeWithText("Activar perfil").performClick()
        val activeId = "${original.rule.id}-active"
        compose.waitUntil(15_000) { runBlocking { repository.allRules().any { it.id == activeId && it.recurrence == "component" } } }
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeReadings, repository.dao.allReadings())
        assertEquals(beforePlanIds + activeId, maintenanceGroups(repository.tasks()).flatMap { it.tasks }.map { it.rule.id }.toSet())
        waitForSaveFeedback()
        compose.onNodeWithText("Ver fuente del fabricante").performScrollTo().performClick()
        compose.onNodeWithText("Gestionar perfil condicionado").performScrollTo().performClick()
        waitForText("Desactivar perfil condicionado")
        compose.onNodeWithText("Desactivar perfil condicionado").performScrollTo().performClick()
        compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allRules().single { it.id == activeId }.recurrence == "inactive" } }
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeReadings, repository.dao.allReadings())
        assertEquals(beforePlanIds, maintenanceGroups(repository.tasks()).flatMap { it.tasks }.map { it.rule.id }.toSet())
        assertFalse(repository.tasks().any { it.status == "INITIAL" })
    }

    @Test fun expenseFiltersCombinePersistAndReactToDateCategoryAndCancellation(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val token = UUID.randomUUID().toString()
        val beforeTotal = repository.totalExpenses()
        val beforeEvents = repository.allEvents()
        val beforeKm = repository.currentKm()
        val fixtures = listOf(
            dev.p38journal.data.Expense("ui-april24-$token", "2024-04-10", 300, "FIXTURE abril 2024 $token", "fuel"),
            dev.p38journal.data.Expense("ui-april25-$token", "2025-04-10", 1250, "FIXTURE abril 2025 $token", "fuel"),
            dev.p38journal.data.Expense("ui-extra25-$token", "2025-04-11", 400, "FIXTURE extra $token", "extras"),
            dev.p38journal.data.Expense("ui-may25-$token", "2025-05-10", 600, "FIXTURE mayo $token", "fuel"))
        try {
            fixtures.forEach { repository.saveExpense(it) }
            compose.onNodeWithText("Gastos").performClick()
            choose("Todos los meses", "Abril")
            choose("Todos los tipos", "Gasolina")
            compose.onNodeWithText("Abril en todos los años").assertExists()
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(1550))
            compose.onNodeWithTag("expenses_total").assertTextEquals(money(beforeTotal + 2550))
            choose("Todos los años", "2025")
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(1250))
            compose.onNodeWithText("Inicio").performClick()
            compose.onNodeWithText("Gastos").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Abril").assertExists()
            compose.onNodeWithText("2025").assertExists()
            compose.onNodeWithText("Gasolina").assertExists()
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(1250))
            choose("2025", "2024")
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(300))
            choose("Abril", "Mayo")
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(0))
            compose.onNodeWithText("No hay gastos para esta selección.").assertExists()
            choose("Mayo", "Abril")
            choose("2024", "2025")
            repository.saveExpense(fixtures[1].copy(date = "2025-05-15", category = "extras", cents = 1300))
            compose.waitUntil(15_000) { compose.onAllNodesWithText("No hay gastos para esta selección.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(0))
            compose.onNodeWithText("Limpiar filtros").performScrollTo().performClick()
            compose.onNodeWithTag("expenses_subtotal").assertTextEquals(money(beforeTotal + 2600))
            repository.voidExpense(fixtures[2].id)
            compose.waitUntil(15_000) { compose.onAllNodesWithText(money(beforeTotal + 2200)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("expenses_total").assertTextEquals(money(beforeTotal + 2200))
            assertEquals(beforeEvents, repository.allEvents())
            assertEquals(beforeKm, repository.currentKm())
        } finally { fixtures.forEach { repository.voidExpense(it.id) } }
    }

    @Test fun groupedOperationCanCustomizePhaseAndRestoreManufacturerWithoutWork(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val phase = repository.tasks().single { it.rule.id == "transfer-level" }
        val group = maintenanceGroups(repository.tasks()).single { it.tasks.any { member -> member.rule.id == phase.rule.id } }
        val beforeEvents = repository.allEvents()
        val beforeReadings = repository.dao.allReadings()
        val name = "FIXTURE fase personalizada ${UUID.randomUUID()}"
        compose.onNodeWithContentDescription("Mantenimiento").performClick()
        compose.onNodeWithText(group.title).performScrollTo().performClick()
        compose.onNodeWithText("Qué incluye").performScrollTo().performClick()
        compose.onNodeWithText(friendlyTitle(phase)).performScrollTo().performClick()
        compose.onNodeWithText("Editar mantenimiento").performScrollTo().performClick()
        compose.onNodeWithTag("rule_title").performTextReplacement(name)
        compose.onNodeWithTag("rule_km").performTextReplacement("10000")
        compose.onNodeWithTag("rule_months").performTextReplacement("6")
        compose.onNodeWithText("Guardar mantenimiento").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allRules().single { it.id == phase.rule.id }.km == 10000 } }
        val personalized = repository.allRules().single { it.id == phase.rule.id }
        assertEquals("component", personalized.recurrence)
        assertEquals("personalized", personalized.origin)
        assertNull(personalized.firstKm)
        assertNull(personalized.firstMonths)
        assertEquals(185000, repository.tasks().single { it.rule.id == phase.rule.id }.dueKm)
        waitForSaveFeedback()
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("Personalizado").assertExists()
        compose.onNodeWithText("Editar mantenimiento").performScrollTo().performClick()
        waitForText("Restaurar fabricante")
        compose.onNodeWithText("Restaurar fabricante").performScrollTo().performClick()
        compose.onNodeWithText("Restaurar").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allRules().single { it.id == phase.rule.id }.recurrence == "phase_b" } }
        val restored = repository.allRules().single { it.id == phase.rule.id }
        assertEquals(20000, restored.firstKm)
        assertEquals(40000, restored.km)
        assertEquals("manufacturer", restored.origin)
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeReadings, repository.dao.allReadings())
    }

    @Test fun forecastSeparatesConditionalFutureRevisionsFromRealRegistration(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val beforeEvents = repository.allEvents()
        val beforeVisits = repository.dao.allVisits()
        compose.onNodeWithContentDescription("Mantenimiento").performClick()
        choose("Próximos vencimientos reales", "Previsión de revisiones futuras")
        compose.onNodeWithText("Primeros cuatro hitos previstos").assertExists()
        compose.onNodeWithText("Registrar realizado").assertDoesNotExist()
        compose.onNodeWithText("Revisión prevista a los 215.000 km").performScrollTo().assertExists()
        compose.onAllNodesWithText("Consultar operaciones previstas")[1].performScrollTo().performClick()
        val oil = repository.allRules().single { it.id == "engine-oil-filter" }
        val phaseC = repository.allRules().single { it.id == "automatic-fluid" }
        compose.onAllNodesWithText("33 operaciones previstas").onFirst().performScrollTo().assertExists()
        compose.onNodeWithText("• ${oil.title}").performScrollTo().assertExists()
        compose.onNodeWithText("• ${phaseC.title}").performScrollTo().assertExists()
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeVisits, repository.dao.allVisits())
    }

    @Test fun ownUniqueMaintenanceCreatesEditsDisablesAndCompletesOnceWithoutUrl(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val title = "FIXTURE cita propia ${UUID.randomUUID()}"
        val today = ViewModelProvider(compose.activity)[JournalViewModel::class.java].today()
        val beforeKm = repository.currentKm()
        compose.onNodeWithContentDescription("Mantenimiento").performClick()
        compose.onNodeWithText("Crear mantenimiento").performScrollTo().performClick()
        compose.onNodeWithTag("rule_title").performTextInput(title)
        choose("Recurrente", "Tarea única")
        compose.onNodeWithTag("rule_due_date").performTextInput(today)
        compose.onNodeWithText("Guardar mantenimiento").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allRules().any { it.title == title } } }
        val own = repository.allRules().single { it.title == title }
        assertEquals("custom", own.origin)
        assertTrue(own.source.isBlank())
        assertTrue(own.sourceUrl.isBlank())
        waitForSaveFeedback()
        compose.onNodeWithText("Editar mantenimiento").performScrollTo().performClick()
        compose.onNodeWithTag("rule_notes").performTextInput("FIXTURE nota modificada")
        compose.waitForIdle()
        compose.onNodeWithTag("Recordatorios de esta operación").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNode(hasAnyAncestor(hasTestTag("Recordatorios de esta operación")) and isToggleable(), useUnmergedTree = true).assertIsOff()
        compose.onNodeWithText("Guardar mantenimiento").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performClick()
        try {
            compose.waitUntil(15_000) { runBlocking { !repository.allRules().single { it.id == own.id }.enabled } }
        } catch (error: Throwable) {
            val model = ViewModelProvider(compose.activity)[JournalViewModel::class.java]
            throw AssertionError("Recordatorios de operación propia no desactivados. busy=${model.busy.value}; message=${model.message.value}\n${compose.onRoot(useUnmergedTree = true).printToString()}", error)
        }
        assertEquals(1, repository.allRules().count { it.id == own.id })
        waitForSaveFeedback()
        compose.onNodeWithText("Recordatorios desactivados").assertExists()
        compose.onNodeWithText("Registrar realizado").performScrollTo().performClick()
        compose.onNodeWithTag("line_completed_0").performScrollTo().performClick()
        compose.onNodeWithText("Guardar trabajo").performScrollTo().performClick()
        compose.onNodeWithText("Sí, se ha realizado").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allEvents().any { it.ruleId == own.id && it.active } } }
        val complete = repository.tasks().single { it.rule.id == own.id }
        assertNull(complete.dueDate)
        assertNull(complete.dueKm)
        assertEquals(beforeKm, repository.currentKm())
        assertEquals(1, repository.allEvents().count { it.ruleId == own.id && it.active })
    }

    @Test fun readingFaultPartAndLinkedPdfWorkThroughNativeScreens(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val token = UUID.randomUUID().toString()
        val beforeEvents = repository.allEvents()
        val beforeExpenses = repository.dao.allExpenses()
        val reading = (repository.currentKm() ?: 176000) + 1
        compose.onNodeWithText("Actualizar kilómetros").performClick()
        compose.onNodeWithTag("reading_km").performTextInput(reading.toString())
        compose.onNodeWithText("Guardar lectura").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { repository.currentKm() == reading } }
        waitForSaveFeedback()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Averías").performClick()
        compose.onNodeWithText("Registrar avería").performClick()
        val faultTitle = "FIXTURE avería UI $token"
        compose.onNodeWithTag("note_title").performTextInput(faultTitle)
        compose.onNodeWithTag("Síntomas observados").performScrollTo().performTextInput("FIXTURE: síntoma simulado")
        compose.onNodeWithTag("Diagnóstico confirmado (si existe)").performScrollTo().performTextInput("FIXTURE: diagnóstico separado")
        compose.onNodeWithText("Abierta").performScrollTo().performClick()
        compose.onNodeWithText("Resuelta").performClick()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        try {
            compose.waitUntil(15_000) { runBlocking { repository.dao.allNotes().any { it.title == faultTitle } } }
        } catch (error: Throwable) {
            val model = ViewModelProvider(compose.activity)[JournalViewModel::class.java]
            throw AssertionError("Avería no guardada. busy=${model.busy.value}; message=${model.message.value}\n${compose.onRoot(useUnmergedTree = true).printToString()}", error)
        }
        val fault = repository.dao.allNotes().single { it.title == faultTitle }
        assertEquals("resolved", fault.status)
        val details = JsonParser.parseString(fault.body).asJsonObject
        assertEquals("FIXTURE: síntoma simulado", details.get("symptoms").asString)
        assertEquals("FIXTURE: diagnóstico separado", details.get("diagnosis").asString)
        val source = File(compose.activity.cacheDir, "receipt-fixture-$token.pdf")
        source.writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-text.pdf").use { it.readBytes() })
        val document = Files(compose.activity, repository).importDocument(Uri.fromFile(source))
        repository.saveDocument(document.copy(tags = fault.id))
        waitForSaveFeedback()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Registrar avería").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(faultTitle).performScrollTo().performClick()
        compose.onNodeWithTag("note_title").assertTextContains(faultTitle)
        compose.onNodeWithText("Fotos y documentos").performScrollTo().performClick()
        waitForText(document.name)
        compose.onNodeWithText("Abrir documento").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Buscar texto en el PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Página PDF 1 de 1.", substring = true).assertExists()
        compose.onNodeWithText("Volver a documentos").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("Síntomas observados").assertTextContains("FIXTURE: síntoma simulado")
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Piezas").performClick()
        compose.onNodeWithText("Añadir pieza").performClick()
        val partTitle = "FIXTURE pieza UI $token"
        compose.onNodeWithTag("note_title").performTextInput(partTitle)
        compose.onNodeWithTag("Referencia").performScrollTo().performTextInput("FIXTURE-REFERENCE")
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { repository.dao.allNotes().any { it.title == partTitle } } }
        val part = repository.dao.allNotes().single { it.title == partTitle }
        assertEquals("pending", JsonParser.parseString(part.body).asJsonObject.get("compatibility").asString)
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeExpenses, repository.dao.allExpenses())
        repository.deleteNote(fault.id); repository.deleteNote(part.id); repository.deleteDocument(document.id)
        File(compose.activity.filesDir, document.path).delete(); source.delete()
    }
}
