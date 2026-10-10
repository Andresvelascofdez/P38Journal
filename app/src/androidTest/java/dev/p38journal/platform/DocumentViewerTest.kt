package dev.p38journal.platform

import android.content.Context
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.p38journal.data.JournalDatabase
import dev.p38journal.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentViewerTest {
    @get:Rule val compose = createComposeRule()
    private fun checkViewer(asset: String, searchable: Boolean): Unit = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        val source = File(context.cacheDir, "pdf-fixture-${UUID.randomUUID()}.pdf")
        source.writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { it.readBytes() })
        val doc = Files(context, Repository(context, db)).importDocument(Uri.fromFile(source))
        try {
            compose.setContent { MaterialTheme { DocumentViewer(doc) {} } }
            if (searchable) {
                compose.waitUntil(30_000) { compose.onAllNodesWithText("Buscar texto en el PDF").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Buscar texto en el PDF").performTextInput("ISOLATED")
                compose.waitUntil(5_000) { compose.onAllNodesWithText("1 páginas con coincidencias").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("pdf_match_count").assertTextEquals("1 páginas con coincidencias")
                compose.onNodeWithText("PDF 1", substring = false).performClick()
                compose.onNodeWithText("Marcar página").performClick()
                compose.onNodeWithText("Favorita 1").assertExists()
                assertEquals("0", context.getSharedPreferences("document-favourites", 0).getString(doc.id, ""))
            } else {
                compose.waitUntil(30_000) { compose.onAllNodesWithTag("pdf_search_unavailable").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("pdf_search_unavailable").assertExists()
            }
            compose.onNodeWithText("Página PDF 1 de 1.", substring = true).assertExists()
            compose.onNodeWithText("Ampliar").performClick()
            compose.onNodeWithText("Reducir").performClick()
        } finally {
            context.getSharedPreferences("document-favourites", 0).edit().remove(doc.id).commit()
            source.delete(); File(context.filesDir, doc.path).delete(); db.close()
        }
    }
    @Test fun textPdfSearchAndBookmarksAreReal() = checkViewer("fixture-text.pdf", true)
    @Test fun scanWithoutTextDeclaresSearchUnavailable() = checkViewer("fixture-scan.pdf", false)
}
