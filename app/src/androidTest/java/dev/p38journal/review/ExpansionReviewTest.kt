package dev.p38journal.review

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.p38journal.data.*
import dev.p38journal.domain.Tyres
import dev.p38journal.platform.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ExpansionReviewTest {
    private lateinit var context: Context
    private lateinit var db: JournalDatabase
    private lateinit var repo: Repository
    private lateinit var settings: Settings
    private val clock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneOffset.UTC)

    @Before fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        repo = Repository(context, db, clock)
        repo.initialize()
        repo.updatePlanningReference("2026-10-01", 175000)
        settings = Preferences(context).current()
    }

    @After fun teardown(): Unit = runBlocking {
        Preferences(context).update(settings)
        db.close()
    }

    @Test fun plannedConversionUsesSameHistoryIdentityWithUnknownDateAndRealExpense(): Unit = runBlocking {
        val beforeTasks = repo.tasks()
        val planned = Visit("review-work", "", title = "FIXTURE improvement", dateExact = false,
            status = "planned", manual = true, targetDate = "2026-11-01", estimatedCents = 99999)
        repo.saveManualWork(planned)
        assertTrue(repo.dao.allExpenses().isEmpty())
        assertEquals(1, repo.dao.allVisits().size)
        repo.saveExpense(Expense("review-payment", "2026-10-05", 4321, "FIXTURE payment", "extras"))
        repo.saveManualWork(planned.copy(status = "done"), "review-payment")
        val completed = repo.dao.allVisits().single()
        assertEquals(planned.id, completed.id)
        assertEquals("", completed.date)
        assertFalse(completed.dateExact)
        assertNull(completed.km)
        assertEquals(4321L, completed.paidCents)
        assertEquals(planned.id, repo.dao.allExpenses().single().visitId)
        assertEquals(4321L, repo.totalExpenses())
        assertEquals(1, repo.workLines(planned.id).size)
        assertTrue(repo.allEvents().isEmpty())
        assertEquals(beforeTasks, repo.tasks())
        assertTrue(repo.dao.allReadings().isEmpty())
    }

    @Test fun plannedWorkCanLinkARealPurchaseAndConversionDoesNotCountItTwice(): Unit = runBlocking {
        val work = Visit("review-planned", "", title = "FIXTURE future work", dateExact = false,
            status = "planned", manual = true, estimatedCents = 55555)
        repo.saveManualWork(work)
        repo.saveExpense(Expense("review-real-purchase", "2026-10-05", 1000, "FIXTURE parts bought", "extras"))
        repo.saveManualWork(work, "review-real-purchase")
        assertEquals("planned", repo.dao.allVisits().single().status)
        assertEquals(1000L, repo.totalExpenses())
        assertEquals(work.id, repo.dao.allExpenses().single().visitId)
        repo.saveManualWork(repo.dao.allVisits().single().copy(status = "done"))
        assertEquals("done", repo.dao.allVisits().single().status)
        assertEquals(1, repo.dao.allVisits().size)
        assertEquals(1, repo.dao.allExpenses().size)
        assertEquals("review-real-purchase", repo.dao.allExpenses().single().id)
        assertEquals(1000L, repo.totalExpenses())
        assertTrue(repo.allEvents().isEmpty())
    }

    @Test fun fuelExpenseEditsAndRestorePreserveOnePaymentAndLocalCancellation(): Unit = runBlocking {
        repo.saveExpense(Expense("old-fuel", "2026-09-01", 1111, "FIXTURE old fuel", "fuel"))
        repo.saveFuelEntry(FuelEntry("review-fuel", "2026-10-02", 170000, 20125, 2222, false))
        val snapshot = repo.exportSnapshot()
        val payment = repo.dao.allExpenses().single { it.fuelId == "review-fuel" }
        repo.saveExpense(payment.copy(date = "2026-10-03", cents = 3333, notes = "FIXTURE receipt"))
        assertEquals(3333L, repo.dao.allFuelEntries().single().totalCents)
        assertEquals("2026-10-03", repo.dao.allFuelEntries().single().date)
        repo.restoreSnapshot(snapshot)
        repo.restoreSnapshot(snapshot)
        assertEquals(4444L, repo.totalExpenses())
        assertEquals(3333L, repo.dao.allFuelEntries().single().totalCents)
        assertEquals(2, repo.dao.allExpenses().size)
        assertTrue(repo.dao.allReadings().isEmpty())
        assertNull(repo.currentKm())
        assertEquals(175000, repo.planningReference().baselineKm)
        repo.voidExpense(payment.id)
        repo.restoreSnapshot(snapshot)
        assertFalse(repo.dao.allFuelEntries().single().active)
        assertEquals(1111L, repo.totalExpenses())
        assertNull(repo.dao.allExpenses().single { it.id == payment.id }.fuelId)
    }

    @Test fun restoringFuelOverAConflictingLocalReceiptPreservesReceiptAndValidBackup(): Unit = runBlocking {
        repo.saveFuelEntry(FuelEntry("review-conflict", "2026-10-02", 170000, 20000, 2222, false))
        val copied = repo.exportSnapshot()
        val paymentId = repo.dao.allFuelEntries().single().expenseId!!
        val destinationDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val destination = Repository(context, destinationDb, clock)
            destination.initialize()
            destination.saveExpense(Expense(paymentId, "2026-10-11", 9999, "FIXTURE local receipt", "extras"))
            destination.restoreSnapshot(copied)
            destination.restoreSnapshot(copied)
            assertEquals(9999L, destination.totalExpenses())
            assertEquals("extras", destination.dao.allExpenses().single().category)
            assertFalse(destination.dao.allFuelEntries().single().active)
            assertNull(destination.dao.allExpenses().single().fuelId)
            destination.validateSnapshot(destination.exportSnapshot())
            assertTrue(destination.dao.allReadings().isEmpty())
        } finally { destinationDb.close() }
    }

    @Test fun correctionOfBothAxesKeepsSinglePaymentAndLaterFrontReplacement(): Unit = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("mount-front", "front", "mount", null, 175000, 10000),
            TyreEvent("mount-rear", "rear", "mount", null, 175000, 10000)))
        assertTrue(repo.dao.allVisits().isEmpty())
        assertTrue(repo.dao.allExpenses().isEmpty())
        repo.saveTyreEvents(listOf(TyreEvent("both-front", "front", "change", "2026-10-10", 185200, 10000),
            TyreEvent("both-rear", "rear", "change", "2026-10-10", 185200, 12000)), 20000)
        val original = repo.dao.allTyreEvents().filter { it.id.startsWith("both-") }
        repo.saveTyreEvents(listOf(TyreEvent("later-front", "front", "change", null, 195000, 15000)))
        repo.saveTyreEvents(original.map { it.copy(km = 185250, notes = "FIXTURE correction") }, 20000)
        assertEquals(2, repo.dao.allVisits().size)
        assertEquals(1, repo.dao.allExpenses().size)
        assertEquals(20000L, repo.totalExpenses())
        val corrected = repo.dao.allTyreEvents().filter { it.id.startsWith("both-") }
        assertEquals(original.associate { it.id to it.sequence }, corrected.associate { it.id to it.sequence })
        assertEquals(1, corrected.map { it.visitId }.distinct().size)
        val states = Tyres.evaluate(repo.dao.allTyreEvents(), 195000)
        assertEquals("later-front", states.first().mount!!.id)
        assertEquals(0, states.first().usedKm)
        assertEquals(9750, states.last().usedKm)
        assertEquals(197250, states.last().dueKm)
        val laterVisit = repo.dao.allVisits().single { it.km == 195000 }
        assertEquals("", laterVisit.date)
        assertFalse(laterVisit.dateExact)
        assertTrue(repo.allEvents().isEmpty())
    }

    @Test fun foreignSequenceIsHistoryOnlyOverLocalTrackingButRestoresTrackingWhenClean(): Unit = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("local-mount", "front", "mount", null, 175000, 10000)))
        repo.saveTyreEvents(listOf(TyreEvent("local-current", "front", "change", null, 185000, 10000)))
        val before = Tyres.evaluate(repo.dao.allTyreEvents(), 190000).first()
        val sourceDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        val cleanDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val source = Repository(context, sourceDb, clock)
            source.initialize()
            source.dao.putTyreEvent(TyreEvent("foreign-mount", "front", "mount", null, 160000, 12000, sequence = 99))
            val copied = source.exportSnapshot()
            repo.restoreSnapshot(copied)
            repo.restoreSnapshot(copied)
            assertEquals(before, Tyres.evaluate(repo.dao.allTyreEvents(), 190000).first())
            val historical = repo.dao.allTyreEvents().single { it.id == "foreign-mount" }
            assertTrue(historical.active)
            assertFalse(historical.tracking)
            repo.validateSnapshot(repo.exportSnapshot())
            val clean = Repository(context, cleanDb, clock)
            clean.initialize()
            clean.restoreSnapshot(copied)
            assertEquals("foreign-mount", Tyres.evaluate(clean.dao.allTyreEvents(), 190000).first().mount!!.id)
            assertTrue(clean.dao.allTyreEvents().single().tracking)
        } finally { sourceDb.close(); cleanDb.close() }
    }

    @Test fun globalNotificationsOffOnKeepsAlreadyEmittedCycleAndReadInbox(): Unit = runBlocking {
        Preferences(context).update(Settings(zone = "UTC", hour = 9, enabled = true, weekly = false))
        repo.saveDeadline(Deadline("review-deadline", "FIXTURE document", "2026-10-12"))
        val transport = object : NotificationTransport {
            var emissions = 0
            override fun allowed(channel: String) = true
            override fun emit(id: Int, channel: String, title: String, body: String, ruleId: String) { emissions++ }
            override fun cancel(id: Int) = Unit
        }
        val evaluator = ManualNotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate("change")
        val inbox = repo.dao.allInbox().single { it.ruleId == "deadline:review-deadline" }
        repo.saveInbox(inbox.copy(state = "read"))
        Preferences(context).update(Preferences(context).current().copy(enabled = false))
        evaluator.evaluate("change")
        assertEquals("read", repo.dao.allInbox().single { it.id == inbox.id }.state)
        Preferences(context).update(Preferences(context).current().copy(enabled = true))
        evaluator.evaluate("change")
        evaluator.evaluate()
        assertEquals(1, transport.emissions)
        assertEquals("read", repo.dao.allInbox().single { it.id == inbox.id }.state)
        assertEquals("emitted", repo.dao.allLedger().single { it.id == inbox.id }.state)
    }

    @Test fun tyreNotificationRequiresActualReadingRatherThanPlanningBaseline(): Unit = runBlocking {
        Preferences(context).update(Settings(zone = "UTC", hour = 9, enabled = true, weekly = false))
        repo.saveTyreEvents(listOf(TyreEvent("below-baseline", "front", "mount", null, 170000, 1000)))
        val transport = object : NotificationTransport {
            var emissions = 0
            override fun allowed(channel: String) = true
            override fun emit(id: Int, channel: String, title: String, body: String, ruleId: String) {
                assertEquals("tyres:front", ruleId)
                emissions++
            }
            override fun cancel(id: Int) = Unit
        }
        val evaluator = ManualNotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate("change")
        assertEquals(0, transport.emissions)
        repo.saveReading(Reading("actual-tyre-reading", "2026-10-12", 171000))
        evaluator.evaluate("reading")
        evaluator.evaluate()
        assertEquals(1, transport.emissions)
    }
}
