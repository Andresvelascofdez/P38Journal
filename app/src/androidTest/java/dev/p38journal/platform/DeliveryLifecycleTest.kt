package dev.p38journal.platform

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.p38journal.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeliveryLifecycleTest {
    @Test fun stagedUpdateUninstallAndCompleteRestore(): Unit = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("stage")
        assumeTrue("This test is explicitly orchestrated by scripts/verify-lifecycle.py", stage != null)
        val context: Context = ApplicationProvider.getApplicationContext()
        require(context.packageName.endsWith(".debug"))
        val repo = Repository(context); repo.initialize()
        val files = Files(context, repo)
        if (stage == "seed") {
            repo.saveExpense(Expense("DELIVERY_FIXTURE_PAYMENT", "2026-10-10", 1250, "ISOLATED DELIVERY FIXTURE", "extras"))
            repo.saveReading(Reading("DELIVERY_FIXTURE_READING", "2026-10-10", 176001), true)
            repo.saveNote(Note("DELIVERY_FIXTURE_NOTE", "fault", "ISOLATED DELIVERY FIXTURE", "Test fixture only", "resolved", "2026-10-10"))
            val source = File(context.cacheDir, "fixture-source.pdf")
            source.writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-text.pdf").use { it.readBytes() })
            val document = files.importDocument(Uri.fromFile(source))
            repo.saveDocument(document.copy(tags = "DELIVERY_FIXTURE_NOTE"))
            context.getSharedPreferences("document-favourites", 0).edit().putString(document.id, "0").commit()
            files.exportBackup(Uri.fromFile(File(context.cacheDir, "lifecycle-export.zip")))
            source.delete()
        }
        if (stage == "restore") {
            assertFalse(repo.dao.allExpenses().any { it.id == "DELIVERY_FIXTURE_PAYMENT" })
            val input = Uri.fromFile(File(context.cacheDir, "lifecycle-import.zip"))
            assertTrue(files.previewBackup(input).contains("Copia validada"))
            files.restoreBackup(input); files.restoreBackup(input)
        }
        assertEquals(1250L, repo.dao.allExpenses().single { it.id == "DELIVERY_FIXTURE_PAYMENT" }.cents)
        assertEquals(176001, repo.currentKm())
        assertEquals("resolved", repo.dao.allNotes().single { it.id == "DELIVERY_FIXTURE_NOTE" }.status)
        val document = repo.dao.allDocuments().single { "DELIVERY_FIXTURE_NOTE" in it.tags }
        assertEquals(document.sha256, Files.checksum(File(context.filesDir, document.path)))
        assertEquals("0", context.getSharedPreferences("document-favourites", 0).getString(document.id, ""))
        assertTrue(repo.allEvents().isEmpty())
        assertEquals(1250L, repo.totalExpenses())
        assertEquals(60, repo.allRules().size)
    }
}
