package dev.p38journal.ui

import android.net.Uri
import androidx.compose.ui.test.*
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
        val selected = group.tasks.first()
        val baseline = repository.planningReference()
        val beforeKm = repository.currentKm()
        val expectedKm = beforeKm ?: baseline.baselineKm
        val title = "FIXTURE revisión parcial ${UUID.randomUUID()}"
        compose.onNodeWithText("Mantenimiento").performClick()
        compose.onAllNodesWithText(group.title).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Registrar realizado").assertExists()
        compose.onNodeWithText("Ver fuente del fabricante").assertExists()
        compose.onNodeWithText("Qué incluye").performScrollTo().performClick()
        compose.onNodeWithText("Desmarcar todas").performScrollTo().performClick()
        compose.onNodeWithText(friendlyTitle(selected)).performScrollTo().performClick()
        compose.onNodeWithText("Registrar realizado").performScrollTo().performClick()
        compose.onNodeWithTag("visit_km").assertTextContains(expectedKm.toString())
        compose.onNodeWithTag("visit_date").assertTextContains(ViewModelProvider(compose.activity)[JournalViewModel::class.java].today())
        compose.onNodeWithTag("visit_title").performTextReplacement(title)
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
        repository.voidVisit(saved.id)
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
        compose.waitUntil(15_000) { runBlocking { repository.dao.allNotes().any { it.title == faultTitle } } }
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
