package dev.p38journal.platform

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.p38journal.data.*
import dev.p38journal.domain.Rule
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformTest {
    private lateinit var context: Context
    private lateinit var db: JournalDatabase
    private lateinit var repo: Repository
    private lateinit var previous: Settings
    private val cleanup = mutableListOf<File>()
    @Before fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        repo = Repository(context, db)
        previous = Preferences(context).current()
        Preferences(context).update(Settings(zone = "UTC", hour = 9, enabled = true))
        repo.updatePlanningReference("2025-10-15", 175000)
    }
    @After fun teardown(): Unit = runBlocking { Preferences(context).update(previous); db.close(); cleanup.forEach { it.delete() } }
    private fun temp(suffix: String) = File(context.cacheDir, "fixture-${UUID.randomUUID()}$suffix").also { cleanup.add(it) }
    private class Transport(var permission: Boolean = true) : NotificationTransport {
        val ids = mutableListOf<Int>(); val cancelled = mutableListOf<Int>()
        val visible = mutableMapOf<Int, Pair<String, String>>()
        override fun allowed(channel: String) = permission
        override fun emit(id: Int, channel: String, title: String, body: String, ruleId: String) { ids.add(id); visible[id] = body to ruleId }
        override fun refreshVisible(id: Int, channel: String, title: String, body: String, ruleId: String): Boolean {
            if (id !in visible || !permission) return false
            visible[id] = body to ruleId
            return true
        }
        override fun cancel(id: Int) { cancelled.add(id); visible.remove(id) }
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:15:00Z"), ZoneOffset.UTC)
    private suspend fun oil() { repo.saveRule(Rule("fixture-oil", "Aceite fixture", "engine", "replace", months = 12, km = 20000, source = "isolated fixture", sourceUrl = "https://example.invalid")) }
    @Test fun concurrentNotificationsDeduplicateAndCompletionCancels(): Unit = runBlocking {
        oil(); val transport = Transport(); val evaluator = NotificationEvaluator(context, repo, clock, transport)
        coroutineScope { listOf(async { evaluator.evaluate() }, async { evaluator.evaluate() }).forEach { it.await() } }
        assertEquals(listOf(1001), transport.ids.distinct()); assertEquals(1, transport.ids.count { it == 1001 })
        assertTrue(transport.cancelled.containsAll(listOf(1003, 1004)))
        assertEquals(1, repo.dao.allInbox().size)
        val visit = Visit("notify-visit", "2026-10-15", 195200, title = "Aceite fixture")
        repo.saveVisit(visit, listOf(WorkLine("notify-line", visit.id, "Aceite", "fixture-oil")), listOf("fixture-oil"))
        evaluator.evaluate()
        assertTrue(transport.cancelled.contains(1001)); assertTrue(repo.dao.allInbox().all { it.state == "cancelled" })
        assertEquals(215200, repo.tasks(java.time.LocalDate.parse("2026-10-15")).single().dueKm)
    }
    @Test fun partialCompletionInvalidatesGroupDestinationAndDisablingChannelsCancels(): Unit = runBlocking {
        oil()
        repo.saveRule(Rule("fixture-coolant", "Refrigerante", "cooling", "replace", months = 36, km = 60000, source = "isolated fixture", sourceUrl = "https://example.invalid"))
        repo.saveReading(Reading("partial-reading", "2026-10-15", 235000))
        val transport = Transport(); val evaluator = NotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate()
        transport.cancelled.clear()
        val visit = Visit("partial-v", "2026-10-15", 235200, title = "Solo aceite")
        repo.saveVisit(visit, listOf(WorkLine("partial-l", visit.id, "Aceite", "fixture-oil")), listOf("fixture-oil"))
        evaluator.evaluate()
        assertFalse(transport.cancelled.contains(1001))
        assertEquals(1, repo.dao.allInbox().count { it.state != "cancelled" })
        assertEquals("fixture-coolant", repo.dao.allInbox().single { it.state != "cancelled" }.ruleId)
        assertEquals(1, transport.ids.count { it == 1001 })
        assertEquals("fixture-coolant", transport.visible[1001]?.second)
        assertTrue(transport.visible[1001]?.first?.contains("Refrigerante") == true)
        assertFalse(transport.visible[1001]?.first?.contains("Aceite fixture") == true)
        Preferences(context).update(Settings(zone = "UTC", hour = 9, enabled = true, checks = false, odometerReminder = false))
        evaluator.evaluate()
        assertTrue(transport.cancelled.contains(1003)); assertTrue(transport.cancelled.contains(1004))
    }
    @Test fun deniedPermissionKeepsInboxAndGrantEmitsRelevantSummary(): Unit = runBlocking {
        oil(); val transport = Transport(false); val evaluator = NotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate(); assertTrue(transport.ids.isEmpty()); assertEquals("pending", repo.dao.allInbox().single().state)
        transport.permission = true; evaluator.evaluate(); evaluator.evaluate()
        assertEquals(1, transport.ids.count { it == 1001 }); assertEquals("emitted", repo.dao.allInbox().single().state)
    }
    @Test fun dismissedSummaryIsNotResurrectedByPartialCompletion(): Unit = runBlocking {
        oil()
        repo.saveRule(Rule("fixture-coolant", "Refrigerante", "cooling", "replace", months = 36, km = 60000, source = "isolated fixture", sourceUrl = "https://example.invalid"))
        repo.saveReading(Reading("dismissed-reading", "2026-10-15", 235000))
        val transport = Transport()
        val evaluator = NotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate()
        assertEquals(1, transport.ids.size)
        transport.visible.remove(1001)
        val visit = Visit("dismissed-v", "2026-10-15", 235200, title = "Solo aceite fixture")
        repo.saveVisit(visit, listOf(WorkLine("dismissed-l", visit.id, "Aceite", "fixture-oil")), listOf("fixture-oil"))
        evaluator.evaluate("work")
        assertEquals(1, transport.ids.size)
        assertFalse(transport.visible.containsKey(1001))
        assertEquals("fixture-coolant", repo.dao.allInbox().single { it.state != "cancelled" }.ruleId)
    }
    @Test fun soonToDueHasNewNotificationKindInSameWeek(): Unit = runBlocking {
        repo.updatePlanningReference("2025-10-16", 175000)
        oil(); val visit = Visit("kind-v", "2025-10-16", 176000, title = "Aceite")
        repo.saveVisit(visit, listOf(WorkLine("kind-l", visit.id, "Aceite", "fixture-oil")), listOf("fixture-oil"))
        val transport = Transport()
        NotificationEvaluator(context, repo, clock, transport).evaluate()
        NotificationEvaluator(context, repo, Clock.fixed(Instant.parse("2026-10-16T09:15:00Z"), ZoneOffset.UTC), transport).evaluate()
        assertEquals(2, transport.ids.count { it == 1001 })
        assertEquals(setOf("SOON", "DUE"), repo.dao.allLedger().filter { it.ruleId == "fixture-oil" }.map { it.kind }.toSet())
    }
    @Test fun notificationWindowAndDisabledPreferencesRemainHonest(): Unit = runBlocking {
        oil(); val transport = Transport()
        NotificationEvaluator(context, repo, Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneOffset.UTC), transport).evaluate()
        assertTrue(transport.ids.isEmpty()); assertEquals("pending", repo.dao.allInbox().single().state)
        Preferences(context).update(Settings(zone = "UTC", hour = 9, enabled = false))
        NotificationEvaluator(context, repo, clock, transport).evaluate()
        assertTrue(transport.ids.isEmpty()); assertTrue(transport.cancelled.contains(1001))
    }
    @Test fun freshBaselineComputesLimitsWithoutInitialInboxOrInventedReading(): Unit = runBlocking {
        repo.updatePlanningReference("2026-10-15", 175000)
        oil()
        val transport = Transport()
        NotificationEvaluator(context, repo, clock, transport).evaluate()
        val task = repo.tasks(java.time.LocalDate.parse("2026-10-15")).single()
        assertEquals("PLANNED", task.status)
        assertEquals(195000, task.dueKm)
        assertEquals("2027-10-15", task.dueDate)
        assertTrue(repo.dao.allInbox().isEmpty())
        assertTrue(repo.dao.allReadings().isEmpty())
        assertTrue(repo.dao.allVisits().isEmpty())
        assertTrue(repo.allEvents().isEmpty())
        assertTrue(transport.ids.isEmpty())
        assertFalse(repo.dao.allLedger().any { it.kind == "INITIAL" })
    }
    @Test fun fullBackupKeepsAttachmentsExactTotalsAndReimportIsIdempotent(): Unit = runBlocking {
        val source = temp(".pdf"); source.writeBytes(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-text.pdf").use { it.readBytes() })
        val files = Files(context, repo)
        val doc = files.importDocument(Uri.fromFile(source)); cleanup.add(File(context.filesDir, doc.path))
        source.delete()
        assertTrue(File(context.filesDir, doc.path).exists())
        repo.saveExpense(Expense("backup-fuel", "2026-10-15", 6000, "Gasolina fixture", "fuel"))
        val favourites = context.getSharedPreferences("document-favourites", 0)
        favourites.edit().putString(doc.id, "0,1").commit()
        val zip = temp(".zip"); files.exportBackup(Uri.fromFile(zip))
        File(context.filesDir, doc.path).delete()
        favourites.edit().remove(doc.id).commit()
        val db2 = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val restored = Repository(context, db2); val backup = Files(context, restored)
            assertTrue(backup.previewBackup(Uri.fromFile(zip)).contains("Copia validada"))
            backup.restoreBackup(Uri.fromFile(zip)); backup.restoreBackup(Uri.fromFile(zip))
            assertEquals(6000L, restored.totalExpenses()); assertEquals(1, restored.dao.allExpenses().size)
            assertEquals(repo.planningReference(), restored.planningReference())
            assertEquals(doc.sha256, restored.dao.allDocuments().single().sha256)
            assertEquals(doc.sha256, Files.checksum(File(context.filesDir, doc.path)))
            assertEquals("0,1", favourites.getString(doc.id, ""))
            favourites.edit().remove(doc.id).commit()
            assertFalse(Preferences(context).current().enabled)
        } finally { db2.close() }
    }
    @Test fun maliciousBackupAndWrongChecksumFailBeforeAnyWrite(): Unit = runBlocking {
        val zip = temp(".zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("../outside.txt")); it.write("bad".toByteArray()); it.closeEntry() }
        try { Files(context, repo).restoreBackup(Uri.fromFile(zip)); fail("Accepted traversal") } catch (error: Exception) { assertTrue(error is IllegalArgumentException || error is java.io.IOException) }
        assertTrue(repo.dao.allDocuments().isEmpty()); assertTrue(repo.dao.allExpenses().isEmpty())
        val source = temp(".pdf"); source.writeBytes(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-text.pdf").use { it.readBytes() })
        val document = Files(context, repo).importDocument(Uri.fromFile(source)); val file = File(context.filesDir, document.path); cleanup.add(file)
        file.appendText("tampered")
        try { Files(context, repo).exportBackup(Uri.fromFile(zip)); fail("Accepted corrupt document") } catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.dao.allDocuments().size)
    }
    @Test fun invalidDocumentImportDoesNotPersist(): Unit = runBlocking {
        val source = temp(".pdf"); source.writeText("not a PDF")
        try { Files(context, repo).importDocument(Uri.fromFile(source)); fail("Accepted invalid document") } catch (_: IllegalStateException) { }
        assertTrue(repo.dao.allDocuments().isEmpty())
    }
}
