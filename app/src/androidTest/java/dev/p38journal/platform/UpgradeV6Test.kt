package dev.p38journal.platform

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.p38journal.data.*
import dev.p38journal.domain.Consumption
import dev.p38journal.domain.Tyres
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UpgradeV6Test {
    @Test fun actualV5UpdateAndV6CompleteRepeatedRestore(): Unit = runBlocking {
        val stage=InstrumentationRegistry.getArguments().getString("v6stage")
        assumeTrue("Explicit APK lifecycle fixture on disposable emulator",stage!=null)
        val context:Context=ApplicationProvider.getApplicationContext()
        check(context.packageName=="dev.p38journal.debug" && android.os.Build.MODEL.contains("sdk_gphone"))
        val repo=Repository(context)
        val files=Files(context,repo)
        val today=LocalDate.now().toString()
        if (stage == "runtime-seed" || stage == "runtime-verify") {
            repo.initialize()
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            if (stage == "runtime-seed") {
                Preferences(context).update(Preferences(context).current().copy(enabled=true, weekly=false, odometerReminder=false))
                Notifications.channels(context)
                assertTrue(AndroidNotificationTransport(context).allowed(Notifications.DOCUMENTATION))
                repo.saveDeadline(Deadline("V6_RUNTIME_DEADLINE", "FIXTURE documento aviso", today, leadDays=0, repeatMonths=12))
                repo.saveTyreEvents(listOf(TyreEvent("V6_RUNTIME_MOUNT", "front", "mount", km=175000, intervalKm=10000)))
                repo.saveTyreEvents(listOf(TyreEvent("V6_RUNTIME_REVIEW", "front", "review", today,185200,10000)))
                repo.saveReading(Reading("V6_RUNTIME_BEFORE", today,195199,java.time.Instant.now().toString()))
                val evaluator=NotificationEvaluator(context,repo)
                evaluator.evaluate("reading")
                assertFalse(manager.activeNotifications.any { it.notification.channelId==Notifications.TYRES })
                repo.saveReading(Reading("V6_RUNTIME_LIMIT",today,195200,java.time.Instant.now().toString()))
                evaluator.evaluate("reading"); evaluator.evaluate("reading")
                val limit=System.nanoTime()+3_000_000_000L
                while (manager.activeNotifications.none { it.notification.channelId==Notifications.TYRES } && System.nanoTime()<limit) Thread.sleep(25)
                assertEquals(1,manager.activeNotifications.count { it.notification.channelId==Notifications.TYRES })
                assertEquals(1,manager.activeNotifications.count { it.notification.channelId==Notifications.DOCUMENTATION })
                assertEquals(195200,Tyres.evaluate(repo.dao.allTyreEvents(),repo.currentKm()).first().dueKm)
            } else {
                assertEquals(195200,repo.currentKm())
                val front=Tyres.evaluate(repo.dao.allTyreEvents(),repo.currentKm()).first()
                assertEquals(175000,front.mount!!.km)
                assertEquals(20200,front.usedKm)
                assertEquals(205200,front.dueKm)
                assertEquals(1,repo.dao.allDeadlineRenewals().count { it.deadlineId=="V6_RUNTIME_DEADLINE" })
                NotificationEvaluator(context,repo).evaluate("work")
                Thread.sleep(500)
                assertFalse(manager.activeNotifications.any { it.notification.channelId in setOf(Notifications.TYRES,Notifications.DOCUMENTATION) })
            }
            return@runBlocking
        }
        if(stage=="restore") {
            repo.initialize()
            val backup=Uri.fromFile(File(context.cacheDir,"v6-restore.zip"))
            files.restoreBackup(backup); files.restoreBackup(backup)
        } else {
            assertEquals("verify-update",stage)
            repo.initialize()
            assertEquals(4,repo.db.openHelper.readableDatabase.version)
            assertEquals(182000,repo.currentKm())
            assertEquals("2026-10-01",repo.planningReference().baselineDate)
            assertEquals(175000,repo.planningReference().baselineKm)
            val old=repo.dao.allVisits().single { it.id=="V5_FIXTURE_VISIT" }
            assertEquals(178000,old.km)
            assertEquals("done",old.status); assertFalse(old.manual)
            assertEquals(188000,repo.tasks().single { it.rule.id=="engine-oil-filter" }.dueKm)
            val legacyPayment=repo.dao.allExpenses().single { it.visitId==old.id }
            assertEquals(30000L,legacyPayment.cents)
            assertEquals("extras",legacyPayment.category)
            assertEquals("2026-09-30",legacyPayment.date)
            assertNull(legacyPayment.fuelId)
            assertTrue(repo.dao.allFuelEntries().isEmpty())
            assertTrue(repo.dao.allDeadlines().isEmpty())
            assertTrue(repo.dao.allTyreEvents().isEmpty())
            assertTrue(File(context.filesDir,"migration-backups").listFiles().orEmpty().any { directory ->
                File(directory,"manifest.json").takeIf { it.isFile }?.readText()?.contains("\"targetSchema\":4")==true
            })
            val settings=Preferences(context).current()
            Preferences(context).update(settings.copy(enabled=true,odometerReminder=false,leadDays=0,leadKm=0))
            assertTrue(AndroidNotificationTransport(context).allowed(Notifications.MAINTENANCE))
            NotificationEvaluator(context,repo).evaluate("change")
            assertFalse(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id==1001 })
            assertEquals(1,repo.dao.allInbox().count { it.ruleId=="V5_FIXTURE_DUE" && it.state=="read" })
            Preferences(context).update(settings)
            val expectedEvents=repo.allEvents()
            val work=Visit(id="V6_FIXTURE_WORK",date="",dateExact=false,title="FIXTURE mejora libre",manual=true,status="planned",estimatedCents=9900)
            repo.saveManualWork(work)
            assertEquals(30000L,repo.totalExpenses())
            repo.saveManualWork(work.copy(status="done"))
            assertEquals(1,repo.dao.allVisits().count { it.id==work.id })
            assertEquals(expectedEvents,repo.allEvents())
            listOf(FuelEntry("V6_FUEL_A",today,175000,70000,7000,true),
                FuelEntry("V6_FUEL_B",today,175200,20000,2000,false),
                FuelEntry("V6_FUEL_C",today,175500,50000,5000,true)).forEach { repo.saveFuelEntry(it) }
            repo.saveDeadline(Deadline("V6_FIXTURE_DEADLINE","FIXTURE documento libre",today,repeatMonths=12))
            repo.renewDeadline("V6_FIXTURE_DEADLINE",LocalDate.parse(today).plusYears(1).toString())
            repo.saveTyreEvents(listOf(TyreEvent("V6_FRONT_MOUNT","front","mount",km=175000,intervalKm=10000),
                TyreEvent("V6_REAR_MOUNT","rear","mount",km=175000,intervalKm=10000)))
            repo.saveTyreEvents(listOf(TyreEvent("V6_FRONT_REVIEW","front","review",today,185200,10000)))
            val reviewed=Tyres.evaluate(repo.dao.allTyreEvents(),185200).first()
            assertEquals(10200,reviewed.usedKm); assertEquals(195200,reviewed.dueKm)
            repo.saveTyreEvents(listOf(TyreEvent("V6_FRONT_CHANGE","front","change",today,190000,10000),
                TyreEvent("V6_REAR_CHANGE","rear","change",today,190000,12000)),10000)
            assertEquals(1,repo.dao.allTyreEvents().filter { it.kind=="change" }.map { it.visitId }.distinct().size)
            files.exportBackup(Uri.fromFile(File(context.cacheDir,"v6-after-update.zip")))
        }
        assertEquals(182000,repo.currentKm())
        assertEquals(175000,repo.planningReference().baselineKm)
        assertEquals(54000L,repo.totalExpenses())
        assertEquals(BigDecimal("14.00"),Consumption.calculate(repo.dao.allFuelEntries()).average)
        assertEquals(3,repo.dao.allExpenses().count { it.active && it.fuelId!=null })
        assertEquals(1,repo.dao.allVisits().count { it.id=="V6_FIXTURE_WORK" && it.status=="done" && it.date.isBlank() && !it.dateExact })
        assertEquals(1,repo.dao.allDeadlineRenewals().count { it.deadlineId=="V6_FIXTURE_DEADLINE" })
        assertEquals(5,repo.dao.allTyreEvents().size)
        assertEquals(200000,Tyres.evaluate(repo.dao.allTyreEvents(),190000)[0].dueKm)
        assertEquals(202000,Tyres.evaluate(repo.dao.allTyreEvents(),190000)[1].dueKm)
        assertEquals(1,repo.allEvents().count { it.active })
        val document=repo.dao.allDocuments().single { it.tags=="V5_FIXTURE_VISIT" }
        assertEquals(document.sha256,Files.checksum(File(context.filesDir,document.path)))
        assertEquals("0",context.getSharedPreferences("document-favourites",0).getString(document.id,""))
        assertEquals(777,Preferences(context).current().leadKm)
        val before=repo.allRecords()
        repo.initialize()
        assertEquals(before,repo.allRecords())
        repo.validateSnapshot(repo.exportSnapshot())
    }
}
