package dev.p38journal.platform

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.gson.JsonParser
import dev.p38journal.data.*
import dev.p38journal.domain.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class UpgradeV4Test {
    @Test fun stagedV3UpdateAndV4Restore(): Unit = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("v4stage")
        assumeTrue("Explicitly orchestrated on the disposable debug emulator", stage != null)
        val context: Context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "dev.p38journal.debug")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk_gphone"))
        val repo = Repository(context)
        WorkManager.getInstance(context).cancelAllWork().result.get(10, TimeUnit.SECONDS)
        val files = Files(context, repo)
        if (stage == "seed-v3") {
            withContext(Dispatchers.IO) { repo.db.clearAllTables() }
            repo.initialize(); repo.updatePlanningReference("2026-10-01", 175000)
            val oil = repo.dao.allRules().single { it.id == "engine-oil-filter" }
            val json = JsonParser.parseString(oil.json).asJsonObject.apply {
                addProperty("km", 10000); addProperty("revision", 4)
                addProperty("title", "FIXTURE aceite personalizado v3")
            }
            repo.dao.putRule(RuleRecord(oil.id, json.toString(), 4, true))
            val visit = Visit("V4_FIXTURE_VISIT", "2026-10-02", 178000, title = "FIXTURE revisión", paidCents = 30000)
            repo.saveVisit(visit, listOf(WorkLine("V4_FIXTURE_LINE", visit.id, "FIXTURE aceite", oil.id)), listOf(oil.id))
            val expense = repo.dao.allExpenses().single { it.visitId == visit.id }
            repo.saveExpense(expense.copy(date = "2026-09-30", concept = "FIXTURE concepto personalizado", category = "extras", notes = "FIXTURE nota de pago"))
            val second = Visit("V4_FIXTURE_FREE_VISIT", "2026-10-03", title = "FIXTURE reparación libre", paidCents = 1250)
            repo.saveVisit(second, listOf(WorkLine("V4_FIXTURE_FREE_LINE", second.id, "FIXTURE reparación")), emptyList())
            repo.voidExpense(repo.dao.allExpenses().single { it.visitId == second.id }.id)
            repo.saveReading(Reading("V4_FIXTURE_READING", "2026-10-10", 182000))
            val dueJson = JsonParser.parseString(oil.json).asJsonObject.apply {
                addProperty("id", "V4_FIXTURE_DUE"); addProperty("title", "FIXTURE aviso v3 ya emitido")
                addProperty("km", 1000); addProperty("source", "FIXTURE testing only")
                addProperty("sourceUrl", "https://example.invalid"); addProperty("serviceGroup", "")
            }
            repo.dao.putRule(RuleRecord("V4_FIXTURE_DUE", dueJson.toString(), oil.revision, true))
            val due = repo.tasks().single { it.rule.id == "V4_FIXTURE_DUE" }
            assertEquals("DUE", due.status)
            val key = "v3:${due.rule.id}:${due.generation}:DUE:cycle"
            repo.dao.putLedger(Ledger(key, due.rule.id, due.generation, "DUE", "cycle", "emitted", Instant.now().toString()))
            repo.saveInbox(Inbox(key, due.rule.id, due.rule.title, "FIXTURE emitted in v3", due.generation, "read"))
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("fixture-text.pdf").use { it.readBytes() }
            val document = files.importDocument(Uri.fromFile(File(context.cacheDir, "v4-fixture.pdf").apply { writeBytes(bytes) }))
            context.getSharedPreferences("document-favourites", 0).edit().putString(document.id, "0").commit()
            repo.saveDocument(document.copy(tags = visit.id))
            Preferences(context).update(Settings(leadDays = 14, leadKm = 777, hour = 10, weekly = false, enabled = false, odometerReminder = true))
            files.exportBackup(Uri.fromFile(File(context.cacheDir, "v4-before-update.zip")))
            return@runBlocking
        }
        if (stage == "restore") {
            repo.initialize()
            val uri = Uri.fromFile(File(context.cacheDir, "v4-restore.zip"))
            files.restoreBackup(uri); files.restoreBackup(uri)
        } else {
            assertEquals("verify-update", stage)
            repo.initialize()
            val prefs = Preferences(context).current()
            Preferences(context).update(prefs.copy(enabled = true, odometerReminder = false, leadDays = 0, leadKm = 0))
            assertTrue("Notification permission/channel must allow a real duplicate to be observable", AndroidNotificationTransport(context).allowed(Notifications.MAINTENANCE))
            NotificationEvaluator(context, repo).evaluate("change")
            assertFalse(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id == 1001 })
            assertEquals(1, repo.dao.allInbox().count { it.ruleId == "V4_FIXTURE_DUE" && it.state == "read" })
            assertTrue(repo.dao.allLedger().any { it.ruleId == "V4_FIXTURE_DUE" && it.state == "emitted" })
            Preferences(context).update(prefs)
            assertTrue(repo.dao.allExpenses().filter { !it.active }.all { it.visitId == null })
            assertTrue(repo.dao.allAudit().any { it.action == "expense_link_normalized" })
            val visit = repo.dao.allVisits().single { it.id == "V4_FIXTURE_VISIT" }
            repo.saveVisit(visit.copy(km = 178500, notes = "FIXTURE edit after update"), repo.workLines(visit.id), listOf("engine-oil-filter"))
            repo.saveExpense(Expense("V4_FIXTURE_REPLACEMENT", "2026-10-04", 1000, "FIXTURE nuevo pago", visitId = "V4_FIXTURE_FREE_VISIT"))
            repo.saveCustomRule(Rule("V4_FIXTURE_ONCE", "FIXTURE cita única", "owner", "check", recurrence = "once", origin = "custom", dueKm = 182500))
            val done = Visit("V4_FIXTURE_ONCE_VISIT", "2026-10-10", 182500, title = "FIXTURE cita realizada")
            repo.saveVisit(done, listOf(WorkLine("V4_FIXTURE_ONCE_LINE", done.id, "FIXTURE cita", "V4_FIXTURE_ONCE")), listOf("V4_FIXTURE_ONCE"))
            repo.saveCustomRule(Rule("V4_FIXTURE_CUSTOM", "FIXTURE operación propia", "owner", "check", months = 6, km = 10000, origin = "custom", enabled = false, notes = "FIXTURE sin fuente"))
            files.exportBackup(Uri.fromFile(File(context.cacheDir, "v4-after-update.zip")))
        }
        assertEquals(182000, repo.currentKm())
        assertEquals("2026-10-01", repo.planningReference().baselineDate)
        assertEquals(175000, repo.planningReference().baselineKm)
        assertEquals(31000L, repo.totalExpenses())
        val payment = repo.dao.allExpenses().single { it.visitId == "V4_FIXTURE_VISIT" }
        assertEquals("2026-09-30", payment.date); assertEquals("extras", payment.category)
        assertEquals("FIXTURE concepto personalizado", payment.concept)
        assertEquals(30000L, payment.cents)
        assertEquals(188500, repo.tasks().single { it.rule.id == "engine-oil-filter" }.dueKm)
        assertEquals("personalized", repo.allRules().single { it.id == "engine-oil-filter" }.origin)
        val own = repo.allRules().single { it.id == "V4_FIXTURE_CUSTOM" }
        assertEquals("custom", own.origin); assertFalse(own.enabled); assertEquals("", own.sourceUrl)
        assertNull(repo.tasks().single { it.rule.id == "V4_FIXTURE_ONCE" }.dueKm)
        assertEquals(2, repo.allEvents().count { it.active })
        val document = repo.dao.allDocuments().single { it.tags == "V4_FIXTURE_VISIT" }
        assertEquals(document.sha256, Files.checksum(File(context.filesDir, document.path)))
        assertEquals("0", context.getSharedPreferences("document-favourites", 0).getString(document.id, ""))
        assertEquals(777, Preferences(context).current().leadKm)
        assertTrue(Preferences(context).current().odometerReminder)
        val records = repo.allRecords()
        repo.initialize()
        assertEquals(records.visits, repo.dao.allVisits())
        assertEquals(records.events, repo.dao.allEvents())
        assertEquals(records.expenses, repo.dao.allExpenses())
    }
}
