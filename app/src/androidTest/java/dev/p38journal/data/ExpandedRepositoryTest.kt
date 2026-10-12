package dev.p38journal.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import dev.p38journal.domain.Tyres
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ExpandedRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: JournalDatabase
    private lateinit var repo: Repository
    private val clock = Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Asia/Nicosia"))
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        repo = Repository(context, db, clock)
    }
    @After fun tearDown() { db.close() }

    @Test fun plannedWorkBecomesDoneWithSameIdentityAndNoEstimatedExpenseOrMaintenanceEvent() = runBlocking {
        repo.initialize(); val rules = repo.allRules(); val reference = repo.planningReference()
        val planned = Visit("manual", "", title = "Mejora libre", status = "planned", dateExact = false,
            targetDate = "2027-01-20", estimatedCents = 25000)
        repo.saveManualWork(planned); repo.saveManualWork(planned)
        assertEquals(1, repo.observeWorks.first().size); assertTrue(repo.observeVisits.first().isEmpty())
        assertTrue(db.dao().allExpenses().isEmpty())
        repo.saveManualWork(planned.copy(status = "done", targetDate = null, notes = "Histórico sin fecha conocida"))
        val done = repo.observeVisits.first().single()
        assertEquals("manual", done.id); assertEquals("", done.date); assertFalse(done.dateExact)
        assertEquals(1, db.dao().allVisits().size); assertEquals(1, repo.visitLines(done.id).size)
        assertTrue(repo.allEvents().isEmpty()); assertTrue(db.dao().allExpenses().isEmpty())
        assertEquals(rules, repo.allRules()); assertEquals(reference, repo.planningReference()); assertNull(repo.currentKm())
    }

    @Test fun plannedActualPartsPaymentSurvivesConversionAndUnknownWorkDoesNotInventAnExpenseDate() = runBlocking {
        val payment = Expense("parts", "2026-10-10", 30000, "Piezas compradas", "extras")
        repo.saveExpense(payment)
        val planned = Visit("parts-work", "", title = "Instalar piezas", status = "planned", dateExact = false, estimatedCents = 50000)
        repo.saveManualWork(planned, payment.id)
        assertEquals(30000L, db.dao().allVisits().single().paidCents); assertEquals(30000L, repo.totalExpenses())
        repo.saveManualWork(planned.copy(status = "done"))
        assertEquals(1, db.dao().allExpenses().size); assertEquals(payment.date, db.dao().allExpenses().single().date)
        assertEquals(payment.id, db.dao().allExpenses().single().id); assertEquals(planned.id, db.dao().allExpenses().single().visitId)
        assertEquals("", db.dao().allVisits().single().date)
        try {
            repo.saveManualWork(Visit("unknown", "", title = "Otra mejora", dateExact = false, paidCents = 10000))
            fail("Created an expense with a fabricated date")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, db.dao().allVisits().size); assertEquals(30000L, repo.totalExpenses())
    }

    @Test fun freeManualWorkCannotEraseExistingMaintenanceOperations() = runBlocking {
        repo.initialize(); val rule = repo.allRules().first { it.id == "engine-oil-filter" }
        val visit = Visit("maintenance", "2026-10-12", 195200, title = "Aceite")
        repo.saveVisit(visit, listOf(WorkLine("line", visit.id, rule.title, rule.id)), listOf(rule.id))
        val before = repo.allEvents()
        try { repo.saveManualWork(visit.copy(notes = "No borrar operaciones")); fail("Erased linked maintenance") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.allEvents()); assertEquals(1, repo.visitLines(visit.id).size)
    }

    @Test fun fuelCreatesOneExpenseAndBidirectionalEditsAndCancellationRemainConsistent() = runBlocking {
        repo.saveExpense(Expense("old-gas", "2026-10-01", 1000, "Gasolina antigua", "fuel"))
        assertTrue(db.dao().allFuelEntries().isEmpty())
        val entry = FuelEntry("fill", "2026-10-12", 175000, 40000, 6000, true)
        repo.saveFuelEntry(entry); repo.saveFuelEntry(entry)
        assertEquals(2, db.dao().allExpenses().size); assertEquals(7000L, repo.totalExpenses())
        val saved = db.dao().allFuelEntries().single(); val expenseId = saved.expenseId!!
        repo.saveFuelEntry(saved.copy(date = "2026-10-11", litresMilli = 42000, totalCents = 6500))
        assertEquals(expenseId, db.dao().allFuelEntries().single().expenseId)
        val payment = db.dao().allExpenses().single { it.id == expenseId }
        assertEquals(6500L, payment.cents); assertEquals("fuel", payment.category)
        repo.saveExpense(payment.copy(date = "2026-10-10", cents = 6300, concept = "Factura gasolina corregida"))
        assertEquals("2026-10-10", db.dao().allFuelEntries().single().date)
        assertEquals(6300L, db.dao().allFuelEntries().single().totalCents)
        assertEquals(42000L, db.dao().allFuelEntries().single().litresMilli); assertNull(repo.currentKm())
        repo.voidExpense(expenseId)
        assertFalse(db.dao().allFuelEntries().single().active)
        assertEquals(1000L, repo.totalExpenses()); assertNull(db.dao().allExpenses().single { it.id == expenseId }.fuelId)
        assertTrue(repo.allEvents().isEmpty()); repo.validateSnapshot(repo.exportSnapshot())
    }

    @Test fun fuelVoidAndInvalidRelationRollbackDoNotTouchIndependentExpensesOrReadings() = runBlocking {
        val entry = FuelEntry("fill", "2026-10-12", null, null, 4500, false)
        repo.saveFuelEntry(entry)
        val saved = db.dao().allFuelEntries().single()
        try { repo.saveFuelEntry(saved.copy(litresMilli = -1)); fail("Accepted negative litres") } catch (_: IllegalArgumentException) { }
        repo.saveManualWork(Visit("manual", "", title = "Trabajo", dateExact = false))
        val expense = db.dao().allExpenses().single()
        try { repo.saveExpense(expense.copy(visitId = "manual")); fail("Shared expense between work and fuel") } catch (_: IllegalArgumentException) { }
        assertEquals(4500L, repo.totalExpenses()); assertEquals(saved, db.dao().allFuelEntries().single())
        repo.voidFuelEntry(saved.id); repo.voidFuelEntry(saved.id)
        assertEquals(0L, repo.totalExpenses()); assertFalse(db.dao().allExpenses().single().active)
        assertNull(repo.currentKm()); assertTrue(repo.allEvents().isEmpty())
    }

    @Test fun freeDeadlineRenewsOnlyExplicitlyAndMetadataKeepsEmittedLedgerAndIdMapping() = runBlocking {
        val deadline = Deadline("doc", "Certificado libre", "2026-10-12", repeatMonths = 12)
        repo.saveDeadline(deadline)
        assertTrue(db.dao().allDeadlineRenewals().isEmpty()); assertEquals(deadline.dueDate, db.dao().allDeadlines().single().dueDate)
        db.dao().putLedger(Ledger("v6-manual:doc:old", "deadline:doc", "old", "DUE", "cycle", "emitted", clock.instant().toString()))
        db.dao().putLedger(Ledger("v6-map:deadline:doc", "deadline:doc", "10000", "id-map", "map", "active", clock.instant().toString()))
        repo.saveDeadline(deadline.copy(name = "Nombre nuevo", notes = "Nota nueva", leadDays = 45))
        assertEquals("emitted", db.dao().allLedger().single { it.id.startsWith("v6-manual:") }.state)
        repo.renewDeadline(deadline.id, "2027-10-20"); repo.renewDeadline(deadline.id, "2027-10-20")
        val history = db.dao().allDeadlineRenewals().single()
        assertEquals("2026-10-12", history.previousDueDate); assertEquals("2027-10-20", history.newDueDate)
        assertEquals("cancelled", db.dao().allLedger().single { it.id.startsWith("v6-manual:") }.state)
        assertEquals("active", db.dao().allLedger().single { it.id.startsWith("v6-map:") }.state)
        assertTrue(db.dao().allExpenses().isEmpty()); assertTrue(repo.allEvents().isEmpty())
        repo.saveDeadline(db.dao().allDeadlines().single().copy(active = false))
        assertTrue(repo.observeDeadlines.first().isEmpty()); assertEquals(1, db.dao().allDeadlineRenewals().size)
    }

    @Test fun tyreReviewChangesNextLimitButNotMountUsageOrTheOtherAxis() = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("front-mount", "front", "mount", km = 175000, intervalKm = 10000)), null)
        repo.saveTyreEvents(listOf(TyreEvent("rear-mount", "rear", "mount", km = 176000, intervalKm = 15000)), null)
        assertTrue(db.dao().allVisits().isEmpty()); assertTrue(db.dao().allExpenses().isEmpty())
        repo.saveTyreEvents(listOf(TyreEvent("front-review", "front", "review", "2026-10-12", 185200, 10000)), null)
        val states = Tyres.evaluate(db.dao().allTyreEvents(), 185200).associateBy { it.axis }
        assertEquals(195200, states.getValue("front").dueKm); assertEquals(10200, states.getValue("front").usedKm)
        assertEquals(175000, states.getValue("front").mount?.km)
        assertEquals(191000, states.getValue("rear").dueKm)
        assertEquals(1, db.dao().allVisits().size); assertTrue(repo.allEvents().isEmpty()); assertNull(repo.currentKm())
        val review = db.dao().allTyreEvents().single { it.id == "front-review" }
        repo.saveTyreEvents(listOf(review.copy(km = 185300)), null)
        assertEquals(review.sequence, db.dao().allTyreEvents().single { it.id == review.id }.sequence)
        assertEquals(195300, Tyres.evaluate(db.dao().allTyreEvents(), 185300).first().dueKm)
        assertEquals(185300, db.dao().allVisits().single().km)
    }

    @Test fun changingBothTyreAxesCreatesOneVisitAndPaymentAndCorrectionKeepsSequenceAndMetadata() = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("front-mount", "front", "mount", km = 175000, intervalKm = 10000),
            TyreEvent("rear-mount", "rear", "mount", km = 175000, intervalKm = 12000)))
        val both = listOf(TyreEvent("front-change", "front", "change", "2026-10-12", 200000, 10000),
            TyreEvent("rear-change", "rear", "change", "2026-10-12", 200000, 12000))
        repo.saveTyreEvents(both, 50000); repo.saveTyreEvents(both, 50000)
        assertEquals(1, db.dao().allVisits().size); assertEquals(2, db.dao().allLines().size)
        assertEquals(1, db.dao().allExpenses().size); assertEquals(50000L, repo.totalExpenses())
        val saved = db.dao().allTyreEvents().filter { it.kind == "change" }
        val visitId = saved.first().visitId!!
        val payment = db.dao().allExpenses().single().copy(concept = "Factura ruedas", category = "extras")
        repo.saveExpense(payment)
        repo.saveTyreEvents(saved.map { it.copy(date = "2026-10-11", km = 199500, notes = "Corregido") })
        assertEquals(saved.map { it.id to it.sequence }.toMap(), db.dao().allTyreEvents().filter { it.kind == "change" }.associate { it.id to it.sequence })
        assertEquals(visitId, db.dao().allVisits().single().id); assertEquals(199500, db.dao().allVisits().single().km)
        assertEquals(payment, db.dao().allExpenses().single()); assertEquals(50000L, repo.totalExpenses())
        val oldMount = db.dao().allTyreEvents().single { it.id == "front-mount" }
        repo.saveTyreEvents(listOf(oldMount.copy(km = 174900)))
        assertEquals(199500, Tyres.evaluate(db.dao().allTyreEvents(), 200000).first().mount?.km)
        try { repo.voidTyreEvent("front-change"); fail("Silently voided both axes") } catch (_: IllegalArgumentException) { }
        assertEquals(2, db.dao().allTyreEvents().count { it.kind == "change" && it.active })
        repo.voidVisit(visitId)
        assertTrue(db.dao().allTyreEvents().filter { it.kind == "change" }.none { it.active }); assertEquals(0L, repo.totalExpenses())
    }

    @Test fun snapshotFourPreservesAllNewRelationsAndRejectsDanglingFuelAndTyreLinksAtomically() = runBlocking {
        repo.initialize()
        repo.saveManualWork(Visit("planned", "", title = "Accesorio", status = "planned", dateExact = false, estimatedCents = 10000))
        repo.saveFuelEntry(FuelEntry("fuel", "2026-10-12", 175000, 40000, 5500, true))
        repo.saveDeadline(Deadline("doc", "Documento libre", "2026-10-12")); repo.renewDeadline("doc", "2027-10-15")
        repo.saveTyreEvents(listOf(TyreEvent("mount", "front", "mount", km = 175000, intervalKm = 10000)))
        repo.saveTyreEvents(listOf(TyreEvent("change", "front", "change", "2026-10-12", 190000, 10000)), 30000)
        repo.saveInbox(Inbox("doc-inbox", "deadline:doc", "Documento", "Fixture", "cycle"))
        val snapshot = repo.exportSnapshot(); repo.validateSnapshot(snapshot)
        assertEquals(4, JsonParser.parseString(snapshot).asJsonObject.get("schemaVersion").asInt)
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb, clock); target.initialize(); target.restoreSnapshot(snapshot); target.restoreSnapshot(snapshot)
            assertEquals(35500L, target.totalExpenses()); assertEquals(2, targetDb.dao().allExpenses().size)
            assertEquals(db.dao().allFuelEntries(), targetDb.dao().allFuelEntries())
            assertEquals(db.dao().allDeadlines(), targetDb.dao().allDeadlines())
            assertEquals(db.dao().allDeadlineRenewals(), targetDb.dao().allDeadlineRenewals())
            assertEquals(db.dao().allTyreEvents(), targetDb.dao().allTyreEvents())
            assertEquals(2, targetDb.dao().allVisits().size); assertTrue(target.allEvents().isEmpty()); assertNull(target.currentKm())
            target.validateSnapshot(target.exportSnapshot())
            val broken = JsonParser.parseString(snapshot).asJsonObject
            broken.getAsJsonArray("fuelEntries").single().asJsonObject.addProperty("expenseId", "missing")
            try { target.restoreSnapshot(broken.toString()); fail("Accepted a dangling fuel expense") } catch (_: IllegalArgumentException) { }
            assertEquals(35500L, target.totalExpenses()); assertEquals(1, targetDb.dao().allFuelEntries().size)
            val badTyre = JsonParser.parseString(snapshot).asJsonObject
            badTyre.getAsJsonArray("tyreEvents").first { it.asJsonObject.get("kind").asString == "change" }.asJsonObject.addProperty("visitId", "missing")
            try { target.restoreSnapshot(badTyre.toString()); fail("Accepted a dangling tyre visit") } catch (_: IllegalArgumentException) { }
            assertEquals(2, targetDb.dao().allVisits().size)
        } finally { targetDb.close() }
    }

    @Test fun oldSnapshotsTwoAndThreeDefaultVisitsToDoneWithoutInventingFuelOrDeadlineOrTyreRows() = runBlocking {
        repo.saveVisit(Visit("legacy", "2026-10-10", title = "Trabajo anterior", paidCents = 30000), emptyList(), emptyList())
        val original = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        for (version in listOf(2, 3)) {
            val old = JsonParser.parseString(original.toString()).asJsonObject
            old.addProperty("schemaVersion", version); if (version == 2) old.remove("planning")
            listOf("fuelEntries", "deadlines", "deadlineRenewals", "tyreEvents").forEach { old.remove(it) }
            old.getAsJsonArray("visits").forEach { row -> listOf("status", "manual", "targetDate", "estimatedCents").forEach { row.asJsonObject.remove(it) } }
            old.getAsJsonArray("expenses").forEach { it.asJsonObject.remove("fuelId") }
            val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
            try {
                val target = Repository(context, targetDb, clock); target.restoreSnapshot(old.toString()); target.restoreSnapshot(old.toString())
                val visit = target.observeVisits.first().single(); assertEquals("done", visit.status); assertFalse(visit.manual)
                assertEquals(30000L, target.totalExpenses()); assertTrue(targetDb.dao().allFuelEntries().isEmpty())
                assertTrue(targetDb.dao().allDeadlines().isEmpty()); assertTrue(targetDb.dao().allTyreEvents().isEmpty())
                assertTrue(target.allEvents().isEmpty()); assertNull(target.currentKm())
            } finally { targetDb.close() }
        }
    }
    @Test fun restoredFuelCollisionPreservesLocalExpenseAndDisablesIncoherentConsumptionEntry() = runBlocking {
        repo.saveFuelEntry(FuelEntry("fuel", "2026-10-12", 175000, 40000, 5500, true))
        val source = db.dao().allExpenses().single()
        val snapshot = repo.exportSnapshot()
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb, clock)
            val local = source.copy(date = "2026-10-11", cents = 6000, category = "other", fuelId = null)
            target.saveExpense(local); target.restoreSnapshot(snapshot); target.restoreSnapshot(snapshot)
            assertEquals(local, targetDb.dao().allExpenses().single()); assertEquals(6000L, target.totalExpenses())
            assertFalse(targetDb.dao().allFuelEntries().single().active)
            assertTrue(targetDb.dao().allAudit().any { it.action == "fuel_link_normalized" })
            target.validateSnapshot(target.exportSnapshot())
        } finally { targetDb.close() }
    }
    @Test fun restoredTyreHistoryPreservesLocalTrackingWithoutInventingAnAnnullation() = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("source-mount", "front", "mount", km = 175000, intervalKm = 10000)))
        repo.saveTyreEvents(listOf(TyreEvent("source-change", "front", "change", "2026-10-12", 190000, 10000)), 30000)
        repo.saveTyreEvents(listOf(TyreEvent("source-rear", "rear", "mount", km = 180000, intervalKm = 12000)))
        val snapshot = repo.exportSnapshot()
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb, clock)
            target.saveTyreEvents(listOf(TyreEvent("local-front", "front", "mount", km = 200000, intervalKm = 10000)))
            target.restoreSnapshot(snapshot); target.restoreSnapshot(snapshot)
            val history = targetDb.dao().allTyreEvents()
            assertTrue(history.filter { it.id in setOf("source-mount", "source-change") }.all { it.active && !it.tracking })
            assertTrue(history.single { it.id == "source-rear" }.tracking)
            assertEquals(200000, Tyres.evaluate(history, 205000).first().mount?.km)
            assertEquals(180000, Tyres.evaluate(history, 205000).last().mount?.km)
            assertEquals(30000L, target.totalExpenses()); assertEquals(1, targetDb.dao().allVisits().size)
            val old = history.single { it.id == "source-change" }
            target.saveTyreEvents(listOf(old.copy(km = 191000, tracking = true)))
            assertFalse(targetDb.dao().allTyreEvents().single { it.id == old.id }.tracking)
            assertEquals(200000, Tyres.evaluate(targetDb.dao().allTyreEvents(), 205000).first().mount?.km)
            assertEquals(old.sequence, targetDb.dao().allTyreEvents().single { it.id == old.id }.sequence)
            target.saveTyreEvents(listOf(TyreEvent("new-change", "front", "change", "2026-10-12", 210000, 10000)))
            assertEquals(210000, Tyres.evaluate(targetDb.dao().allTyreEvents(), 210000).first().mount?.km)
            assertTrue(targetDb.dao().allAudit().any { it.action == "backup_tyre_tracking_conflict" })
            target.validateSnapshot(target.exportSnapshot())
        } finally { targetDb.close() }
    }
    @Test fun importedUntrackedMountCannotMakeAReviewBeforeTheRealMountValid() = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("actual", "front", "mount", km = 195000, intervalKm = 10000)))
        db.dao().putTyreEvent(TyreEvent("foreign", "front", "mount", km = 150000, intervalKm = 10000, sequence = 99, tracking = false))
        val before = db.dao().allTyreEvents()
        try {
            repo.saveTyreEvents(listOf(TyreEvent("invalid-review", "front", "review", "2026-10-12", 185000, 10000)))
            fail("Used imported history as the current tyre mount")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, db.dao().allTyreEvents()); assertTrue(db.dao().allVisits().isEmpty())
        assertEquals(195000, Tyres.evaluate(db.dao().allTyreEvents(), 195200).first().mount?.km)
        repo.saveTyreEvents(listOf(TyreEvent("valid-review", "front", "review", "2026-10-12", 195200, 10000)))
        assertEquals(205200, Tyres.evaluate(db.dao().allTyreEvents(), 195200).first().dueKm)
        assertEquals(200, Tyres.evaluate(db.dao().allTyreEvents(), 195200).first().usedKm)
    }
    @Test fun correctingImportedTyreHistoryNeverOverwritesTheLocallyEditedVisitOrPayment() = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("source-change", "front", "change", "2026-10-10", 190000, 10000)), 30000)
        val sourceEvent = db.dao().allTyreEvents().single()
        val sourceVisit = db.dao().allVisits().single()
        val snapshot = repo.exportSnapshot()
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb, clock)
            val local = sourceVisit.copy(date = "2026-10-12", km = 200000, notes = "Corrección local protegida", paidCents = 31000)
            target.saveVisit(local, emptyList(), emptyList())
            val localPayment = targetDb.dao().allExpenses().single()
            target.restoreSnapshot(snapshot)
            val imported = targetDb.dao().allTyreEvents().single()
            assertTrue(imported.active); assertFalse(imported.tracking)
            target.saveTyreEvents(listOf(imported.copy(km = 190100, date = null, notes = "Histórico corregido")))
            val corrected = targetDb.dao().allTyreEvents().single()
            assertEquals(sourceEvent.id, corrected.id); assertEquals(sourceEvent.sequence, corrected.sequence)
            assertTrue(corrected.active); assertFalse(corrected.tracking); assertNull(corrected.date)
            assertEquals(local, targetDb.dao().allVisits().single()); assertEquals(localPayment, targetDb.dao().allExpenses().single())
            assertEquals(31000L, target.totalExpenses())
            assertTrue(targetDb.dao().allAudit().any { it.action == "historical_tyre_corrected" })
            target.validateSnapshot(target.exportSnapshot())
            try { target.saveTyreEvents(listOf(corrected.copy(km = 190200)), 40000); fail("Overwrote local payment from imported history") }
            catch (_: IllegalArgumentException) { }
            assertEquals(local, targetDb.dao().allVisits().single()); assertEquals(localPayment, targetDb.dao().allExpenses().single())
            target.voidTyreEvent(corrected.id)
            assertFalse(targetDb.dao().allTyreEvents().single().active)
            assertEquals(local, targetDb.dao().allVisits().single()); assertEquals(localPayment, targetDb.dao().allExpenses().single())
            assertEquals(31000L, target.totalExpenses()); target.validateSnapshot(target.exportSnapshot())
        } finally { targetDb.close() }
    }
    @Test fun explicitUndatedMaintenanceBindingUsesItsRealMileageAndVoidingRecoversDatedReference() = runBlocking {
        repo.initialize(); repo.updatePlanningReference("2026-10-01")
        val original = repo.allRules().single { it.id == "engine-oil-filter" }
        repo.saveRuleOverride(original.copy(km = 10000))
        repo.saveVisit(Visit("dated", "2026-10-10", 178000, title = "Aceite anterior"),
            listOf(WorkLine("dated-line", "dated", original.title, original.id)), listOf(original.id))
        val before = repo.tasks().single { it.rule.id == original.id }
        assertEquals(188000, before.dueKm)
        repo.saveVisit(Visit("undated", "", 180000, title = "Aceite sin fecha conocida", dateExact = false, manual = true),
            listOf(WorkLine("undated-line", "undated", original.title, original.id)), listOf(original.id))
        val task = repo.tasks().single { it.rule.id == original.id }
        assertEquals(190000, task.dueKm); assertEquals(before.dueDate, task.dueDate)
        assertEquals(180000, task.lastEvent?.km); assertNull(task.lastEvent?.date)
        assertNull(repo.allEvents().single { it.visitId == "undated" }.date)
        assertNull(repo.currentKm()); assertTrue(db.dao().allExpenses().isEmpty())
        repo.voidVisit("undated")
        val reverted = repo.tasks().single { it.rule.id == original.id }
        assertEquals(before.generation, reverted.generation); assertEquals(188000, reverted.dueKm)
        assertEquals(before.dueDate, reverted.dueDate)
    }
}
