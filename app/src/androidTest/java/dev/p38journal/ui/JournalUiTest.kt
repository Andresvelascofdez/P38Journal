package dev.p38journal.ui

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import dev.p38journal.MainActivity
import dev.p38journal.data.Repository
import dev.p38journal.domain.ConditionalProfiles
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
        compose.onNodeWithText("Más").performClick()
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

    @Test fun conditionalProfileRequiresEvidenceAndCanBeDisabledWithoutCompletingWork(): Unit = runBlocking {
        waitForApp()
        val repository = Repository(compose.activity)
        val original = repository.tasks().first { it.rule.id == "fuel-filter-applicability" }
        val expected = requireNotNull(ConditionalProfiles.proposed(original.rule))
        val beforeEvents = repository.allEvents()
        val beforeKm = repository.currentKm()
        compose.onNodeWithText("Plan").performClick()
        compose.onNodeWithText(if (original.status == "INITIAL") original.rule.initialAction else original.rule.title).performScrollTo().performClick()
        compose.onNodeWithText("Confirmar aplicabilidad y activar intervalo").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar y activar perfil").performScrollTo().performClick()
        compose.onNodeWithText("Escribe la evidencia que confirma que la condición se aplica.").assertExists()
        val evidence = "FIXTURE UI: condición simulada exclusivamente para esta prueba debug"
        compose.onNodeWithTag("profile_evidence").performScrollTo().performTextInput(evidence)
        compose.onNodeWithText("Confirmar y activar perfil").performScrollTo().performClick()
        compose.onNodeWithText("Activar perfil").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allRules().any { it.id == expected.id && it.recurrence == "component" } } }
        val active = repository.allRules().single { it.id == expected.id }
        assertEquals(48, active.months)
        assertEquals(80000, active.km)
        assertTrue(active.condition.contains(evidence))
        assertEquals(beforeEvents, repository.allEvents())
        assertEquals(beforeKm, repository.currentKm())
        waitForSaveFeedback()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Gestionar perfil condicionado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Gestionar perfil condicionado").performScrollTo().performClick()
        waitForText("Desactivar perfil condicionado")
        compose.onNodeWithText("Desactivar perfil condicionado").performScrollTo().performClick()
        compose.onNodeWithText("Desactivar").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.allRules().single { it.id == expected.id }.recurrence == "inactive" } }
        assertEquals(beforeEvents, repository.allEvents())
        val disabled = repository.tasks().single { it.rule.id == expected.id }
        assertNull(disabled.dueDate)
        assertNull(disabled.dueKm)
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
        compose.onNodeWithText("Más").performClick()
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
        compose.onNodeWithText("Más").performClick()
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
