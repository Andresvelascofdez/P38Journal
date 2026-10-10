package dev.p38journal.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.p38journal.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SystemWorkTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private suspend fun isolatedJournal(block: suspend (Context, Repository) -> Unit) {
        val context: Context = ApplicationProvider.getApplicationContext()
        check(context.packageName.endsWith(".debug")) { "System fixtures require the isolated debug application." }
        val work = WorkManager.getInstance(context)
        work.cancelAllWork().result.get(10, TimeUnit.SECONDS)
        val repo = Repository(context)
        val snapshot = repo.allRecords()
        val ledger = repo.dao.allLedger()
        val prefs = Preferences(context)
        val previous = prefs.current()
        val backupFiles = Files(context, repo).internalBackups().map { it to it.readBytes() }
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            manager.cancelAll()
            backupFiles.forEach { (file, _) -> check(file.delete()) { "Could not isolate existing internal backup." } }
            withContext(Dispatchers.IO) { repo.db.clearAllTables() }
            prefs.update(Settings(zone = "UTC", enabled = false, externalFolderUri = ""))
            repo.initialize()
            repo.updatePlanningReference(LocalDate.now(ZoneOffset.UTC).toString(), 175000)
            block(context, repo)
        } finally {
            work.cancelAllWork().result.get(10, TimeUnit.SECONDS)
            manager.cancelAll()
            withContext(Dispatchers.IO) { repo.db.clearAllTables() }
            repo.db.withTransaction {
                snapshot.vehicle.forEach { repo.dao.putVehicle(it) }
                snapshot.rules.forEach { repo.dao.putRule(it) }
                snapshot.revisions.forEach { repo.dao.putRevision(it) }
                snapshot.visits.forEach { repo.dao.putVisit(it) }
                snapshot.lines.forEach { repo.dao.putLine(it) }
                snapshot.events.forEach { repo.dao.putEvent(it) }
                snapshot.expenses.forEach { repo.dao.putExpense(it) }
                snapshot.readings.forEach { repo.dao.putReading(it) }
                snapshot.notes.forEach { repo.dao.putNote(it) }
                snapshot.documents.forEach { repo.dao.putDocument(it) }
                snapshot.inbox.forEach { repo.dao.putInbox(it) }
                snapshot.audit.forEach { repo.dao.putAudit(it) }
                snapshot.planning.forEach { repo.dao.putPlanningReference(it) }
                ledger.forEach { repo.dao.putLedger(it) }
            }
            Files(context, repo).internalBackups().forEach { check(it.delete()) { "Could not remove fixture internal backup." } }
            backupFiles.forEach { (file, bytes) -> file.parentFile?.mkdirs(); file.writeBytes(bytes) }
            prefs.update(previous)
        }
    }

    private suspend fun awaitNotification(manager: NotificationManager, present: Boolean) {
        repeat(100) {
            if (manager.activeNotifications.any { it.id == 1001 } == present) return
            delay(50)
        }
        assertEquals("Actual maintenance notification state", present, manager.activeNotifications.any { it.id == 1001 })
    }

    @Test fun realNotificationManagerEmitsTestWithoutMaintenanceEvents(): Unit = runBlocking {
        isolatedJournal { context, repo ->
            val before = repo.allEvents()
            val manager = context.getSystemService(NotificationManager::class.java)
            Notifications.test(context)
            delay(300)
            assertTrue(manager.activeNotifications.any { it.id == 1002 && it.notification.contentIntent != null })
            assertEquals(before, repo.allEvents())
            manager.cancel(1002)
        }
    }

    @Test fun realReadingCrossingOutsidePreferredHourEmitsOnceAndWorkStartsNewCycle(): Unit = runBlocking {
        isolatedJournal { context, repo ->
            val today = LocalDate.now(ZoneOffset.UTC).toString()
            val hour = ZonedDateTime.now(ZoneOffset.UTC).hour
            val preferredHour = (hour + 6) % 24
            assertFalse(hour in preferredHour..minOf(preferredHour + 3, 23))
            Preferences(context).update(Settings(zone = "UTC", hour = preferredHour, leadDays = 0, leadKm = 0, weekly = false, enabled = true))
            Notifications.channels(context)
            assertTrue("Actual maintenance channel or permission is blocked", AndroidNotificationTransport(context).allowed(Notifications.MAINTENANCE))
            repo.allRules().filter { it.id != "engine-oil-filter" }.forEach {
                repo.saveRule(it.copy(recurrence = "inactive", scheduleVisibility = "inactive"))
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            repo.saveReading(Reading("fixture-boundary-before", today, 194999))
            Notifications.evaluateNow(context, trigger = "reading")
            assertEquals(194999, repo.currentKm())
            assertEquals(195000, repo.tasks().single { it.rule.id == "engine-oil-filter" }.dueKm)
            assertFalse(manager.activeNotifications.any { it.id == 1001 })
            assertTrue(repo.dao.allInbox().isEmpty())

            repo.saveReading(Reading("fixture-boundary-due", today, 195000))
            Notifications.evaluateNow(context, trigger = "reading")
            awaitNotification(manager, true)
            val visible = manager.activeNotifications.single { it.id == 1001 }
            assertNotNull(visible.notification.contentIntent)
            assertTrue(visible.notification.extras.getCharSequence("android.text").toString().contains("195.000"))
            val old = repo.dao.allLedger().single { it.ruleId == "engine-oil-filter" && it.kind == "DUE" && it.state == "emitted" }
            assertEquals("emitted", repo.dao.allInbox().single { it.ruleId == "engine-oil-filter" && it.state != "cancelled" }.state)

            repo.saveReading(Reading("fixture-boundary-due", today, 195000))
            Notifications.evaluateNow(context, trigger = "reading")
            val reopened = Repository(context)
            reopened.initialize()
            Notifications.evaluateNow(context)
            NotificationEvaluator(context, reopened).evaluate("worker")
            assertEquals(1, repo.dao.allLedger().count { it.ruleId == "engine-oil-filter" && it.kind == "DUE" && it.state == "emitted" })
            assertEquals(old, repo.dao.allLedger().single { it.id == old.id })
            assertEquals(visible.postTime, manager.activeNotifications.single { it.id == 1001 }.postTime)

            val visit = Visit("fixture-boundary-work", today, 195200, title = "FIXTURE cambio de aceite real NotificationManager")
            repo.saveVisit(visit, listOf(WorkLine("fixture-boundary-line", visit.id, "Aceite y filtro", "engine-oil-filter")), listOf("engine-oil-filter"))
            Notifications.evaluateNow(context, trigger = "work")
            awaitNotification(manager, false)
            assertEquals("cancelled", repo.dao.allLedger().single { it.id == old.id }.state)
            assertEquals(215200, repo.tasks().single { it.rule.id == "engine-oil-filter" }.dueKm)
            assertEquals(195000, repo.currentKm())

            repo.saveReading(Reading("fixture-boundary-next", today, 215200))
            Notifications.evaluateNow(context, trigger = "reading")
            awaitNotification(manager, true)
            val next = repo.dao.allLedger().single { it.ruleId == "engine-oil-filter" && it.kind == "DUE" && it.state == "emitted" }
            assertNotEquals(old.generation, next.generation)
            assertEquals(2, repo.dao.allInbox().count { it.ruleId == "engine-oil-filter" })
            assertTrue(manager.activeNotifications.single { it.id == 1001 }.notification.extras.getCharSequence("android.text").toString().contains("215.200"))
        }
    }

    @Test fun realWorkManagerRunsCleanPlanningAndBackupWithoutInitialInbox(): Unit = runBlocking {
        isolatedJournal { context, repo ->
            val manager = WorkManager.getInstance(context)
            val priorIds = manager.getWorkInfosForUniqueWork("maintenance-refresh-v1").get(5, TimeUnit.SECONDS).map { it.id }.toSet()
            Notifications.schedule(context); Notifications.refresh(context)
            var succeeded = false
            for (attempt in 0 until 100) {
                val work = manager.getWorkInfosForUniqueWork("maintenance-refresh-v1").get(5, TimeUnit.SECONDS)
                if (work.any { it.id !in priorIds && it.state == WorkInfo.State.SUCCEEDED }) { succeeded = true; break }
                delay(200)
            }
            assertTrue("Actual WorkManager evaluation did not succeed", succeeded)
            val periodic = manager.getWorkInfosForUniqueWork("maintenance-evaluation-v1").get(5, TimeUnit.SECONDS)
            assertTrue(periodic.isNotEmpty())
            assertEquals(175000, repo.planningReference().baselineKm)
            assertNull(repo.currentKm())
            assertTrue(repo.allEvents().isEmpty())
            assertTrue(repo.dao.allVisits().isEmpty())
            assertTrue(repo.dao.allInbox().isEmpty())
            assertFalse(repo.tasks().any { it.status == "INITIAL" })
            assertFalse(repo.dao.allLedger().any { it.kind == "INITIAL" })
            assertEquals(195000, repo.tasks().single { it.rule.id == "engine-oil-filter" }.dueKm)
            assertTrue(Files(context, repo).internalBackups().isNotEmpty())
        }
    }

    @Test fun realDateDeadlineEmitsFromWorkManagerWhileActivityIsClosed(): Unit = runBlocking {
        isolatedJournal { context, repo ->
            val today = LocalDate.now(ZoneOffset.UTC)
            repo.updatePlanningReference(today.minusYears(1).toString(), 175000)
            repo.allRules().filter { it.id != "engine-oil-filter" }.forEach {
                repo.saveRule(it.copy(recurrence = "inactive", scheduleVisibility = "inactive"))
            }
            Preferences(context).update(Settings(zone = "UTC", hour = ZonedDateTime.now(ZoneOffset.UTC).hour,
                leadDays = 0, leadKm = 0, weekly = false, enabled = true))
            val task = repo.tasks(today).single { it.rule.id == "engine-oil-filter" }
            assertEquals("DUE", task.status)
            assertEquals(today.toString(), task.dueDate)
            assertEquals(195000, task.dueKm)
            assertNull(repo.currentKm())
            assertTrue(repo.allEvents().isEmpty())
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                assertTrue(monitor.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).isEmpty())
                assertTrue(monitor.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.STARTED).isEmpty())
            }
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
            val priorIds = workManager.getWorkInfosForUniqueWork("maintenance-refresh-v1").get(5, TimeUnit.SECONDS).map { it.id }.toSet()
            Notifications.refresh(context)
            var succeeded = false
            for (attempt in 0 until 100) {
                val work = workManager.getWorkInfosForUniqueWork("maintenance-refresh-v1").get(5, TimeUnit.SECONDS)
                if (work.any { it.id !in priorIds && it.state == WorkInfo.State.SUCCEEDED }) { succeeded = true; break }
                delay(200)
            }
            assertTrue("Actual time deadline worker did not succeed", succeeded)
            val manager = context.getSystemService(NotificationManager::class.java)
            awaitNotification(manager, true)
            val notification = manager.activeNotifications.single { it.id == 1001 }.notification
            assertNotNull(notification.contentIntent)
            assertTrue(notification.extras.getCharSequence("android.text").toString().contains(today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
            assertEquals("emitted", repo.dao.allInbox().single { it.ruleId == "engine-oil-filter" }.state)
            assertNull(repo.currentKm())
            assertTrue(repo.allEvents().isEmpty())
            assertTrue(repo.dao.allVisits().isEmpty())
            instrumentation.runOnMainSync {
                assertTrue(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).isEmpty())
            }
        }
    }
}
