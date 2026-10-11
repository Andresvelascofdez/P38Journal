package dev.p38journal.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.google.gson.JsonParser
import com.google.gson.Gson
import dev.p38journal.domain.Rule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: JournalDatabase
    private lateinit var repo: Repository
    private lateinit var context: Context
    @Before fun setUp() { context = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build(); repo = Repository(context, db) }
    @After fun tearDown() { db.close() }
    private suspend fun rule() { repo.saveRule(Rule("oil", "Aceite", "engine", "replace", months = 12, km = 20000, source = "isolated test fixture", sourceUrl = "https://example.invalid")) }

    @Test fun seedIsIdempotentAndHasNoInventedCompletionsOrCurrentReading() = runBlocking {
        repo.initialize(); val count = repo.allRules().size; val notes = db.dao().allNotes().size
        assertTrue(count > 11); repo.initialize(); assertEquals(count, repo.allRules().size); assertEquals(notes, db.dao().allNotes().size)
        assertTrue(repo.allEvents().isEmpty()); assertNull(repo.currentKm()); assertEquals(0L, repo.totalExpenses())
        assertEquals(175000, repo.planningReference().baselineKm)
        assertTrue(repo.tasks().none { it.status == "INITIAL" })
        assertEquals(195000, repo.tasks().first { it.rule.id == "engine-oil-filter" }.dueKm)
    }
    @Test fun planningReferenceRemainsSeparateFromActualReadingAndSurvivesSnapshotRestore() = runBlocking {
        repo.initialize(); repo.updatePlanningReference("2026-10-15")
        repo.saveReading(Reading("real", "2026-10-16", 182000))
        assertEquals(175000, repo.planningReference().baselineKm); assertEquals(182000, repo.currentKm())
        assertEquals("2027-10-15", repo.tasks().first { it.rule.id == "engine-oil-filter" }.dueDate)
        val snapshot = repo.exportSnapshot()
        val restoredDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val restored = Repository(context, restoredDb); restored.initialize(); restored.restoreSnapshot(snapshot)
            assertEquals(repo.planningReference(), restored.planningReference()); assertEquals(182000, restored.currentKm())
            assertTrue(restored.allEvents().isEmpty()); assertTrue(restoredDb.dao().allVisits().isEmpty())
            assertEquals(3, JsonParser.parseString(snapshot).asJsonObject.get("schemaVersion").asInt)
        } finally { restoredDb.close() }
    }
    @Test fun oldOwnerOverrideUsesCurrentCatalogueVisibilityWithoutLosingItsInterval() = runBlocking {
        repo.initialize(); val manual = repo.allRules().first { it.id == "oil-level" }
        val oldJson = JsonParser.parseString(Gson().toJson(manual.copy(days = 3, revision = 2))).asJsonObject
        listOf("scheduleVisibility", "serviceGroup", "firstKm", "firstMonths").forEach { oldJson.remove(it) }
        db.dao().putRule(RuleRecord(manual.id, oldJson.toString(), 2, true)); repo.initialize()
        val owner = repo.allRules().first { it.id == manual.id }
        assertEquals(3, owner.days); assertEquals("manual", owner.scheduleVisibility)
        val task = repo.tasks().first { it.rule.id == manual.id }
        assertNull(task.dueDate); assertNull(task.dueKm); assertEquals("PLANNED", task.status)
    }
    @Test fun versionTwoSnapshotImportsWithoutInventingAPlanningDateOrCompletedWork() = runBlocking {
        repo.saveExpense(Expense("old", "2026-10-10", 1250, "Factura", "other"))
        val old = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        old.addProperty("schemaVersion", 2); old.remove("planning")
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb); target.initialize(); val plan = target.planningReference()
            target.restoreSnapshot(old.toString()); target.restoreSnapshot(old.toString())
            assertEquals(plan, target.planningReference()); assertEquals(1250L, target.totalExpenses())
            assertTrue(target.allEvents().isEmpty()); assertEquals(1, targetDb.dao().allExpenses().size)
        } finally { targetDb.close() }
    }
    @Test fun versionTwoSnapshotInfersOnlyFormalAutomaticIdsAndPreservesCustomOrExplicitManualRecords() = runBlocking {
        rule()
        val entries = listOf(
            Inbox("oil:old:INITIAL:1", "oil", "Mismo título", "Fixture", "old"),
            Inbox("oil:old:CUSTOM:1", "oil", "Mismo título", "Fixture", "old"),
            Inbox("oil:old:INITIAL:2", "oil", "Mismo título", "Fixture", "old", origin = "manual"),
            Inbox("oil:old:INITIAL:invalid", "oil", "Mismo título", "Fixture", "old"),
            Inbox("oil:old:DUE:3", "oil", "Mismo título", "Fixture", "old"))
        entries.forEach { db.dao().putInbox(it) }
        val old = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        old.addProperty("schemaVersion", 2); old.remove("planning")
        old.getAsJsonArray("inbox").forEach { row -> row.asJsonObject.apply {
            if (get("id").asString != "oil:old:INITIAL:2") remove("origin")
            remove("policyVersion")
        } }
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb); target.restoreSnapshot(old.toString())
            val imported = targetDb.dao().allInbox().associateBy { it.id }
            assertEquals("automatic", imported.getValue("oil:old:INITIAL:1").origin)
            assertEquals("cancelled", imported.getValue("oil:old:INITIAL:1").state)
            assertEquals("legacy_unknown", imported.getValue("oil:old:CUSTOM:1").origin)
            assertEquals("legacy_unknown", imported.getValue("oil:old:INITIAL:invalid").origin)
            assertEquals("manual", imported.getValue("oil:old:INITIAL:2").origin)
            assertEquals("automatic", imported.getValue("oil:old:DUE:3").origin)
            target.updatePlanningReference("2026-10-17", 176000)
            val after = targetDb.dao().allInbox().associateBy { it.id }
            assertEquals("cancelled", after.getValue("oil:old:DUE:3").state)
            listOf("oil:old:CUSTOM:1", "oil:old:INITIAL:2", "oil:old:INITIAL:invalid").forEach {
                assertEquals("pending", after.getValue(it).state)
            }
            assertTrue(targetDb.dao().allLedger().isEmpty())
            assertTrue(target.allEvents().isEmpty())
        } finally { targetDb.close() }
    }
    @Test fun versionTwoSnapshotRecoversOnlyExplicitPlanCreationAndKeepsEditedLocalReference() = runBlocking {
        db.dao().putAudit(Audit("actual-plan", "2026-09-15T09:00:00Z", "plan_created", "main", "Fixture recoverable creation"))
        val old = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        old.addProperty("schemaVersion", 2); old.remove("planning")
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb); target.initialize(); target.restoreSnapshot(old.toString())
            assertEquals("2026-09-15", target.planningReference().baselineDate)
            target.updatePlanningReference("2026-10-15"); target.restoreSnapshot(old.toString())
            assertEquals("2026-10-15", target.planningReference().baselineDate)
            assertTrue(target.allEvents().isEmpty())
        } finally { targetDb.close() }
    }
    @Test fun atomicVisitAndReadingUpdatesCalendarAndPersistsPendingNotificationReconciliation() = runBlocking {
        rule(); repo.updatePlanningReference("2026-10-15")
        repo.saveVisit(Visit("performed", "2026-10-16", 195200, title = "Aceite"),
            listOf(WorkLine("line", "performed", "Aceite", "oil")), listOf("oil"),
            updateCurrentReading = Reading("current", "2026-10-16", 195200))
        assertEquals(195200, repo.currentKm()); assertEquals(215200, repo.tasks().single().dueKm)
        val marker = db.dao().allLedger().single { it.id == "v3-reconcile" }
        assertEquals("pending", marker.state); assertEquals("system", marker.ruleId)
        val visits = db.dao().allVisits().size
        try {
            repo.saveVisit(Visit("rollback", "2026-10-16", 195201, title = "Aceite"),
                listOf(WorkLine("rollback-line", "rollback", "Aceite", "oil")), listOf("oil"),
                updateCurrentReading = Reading("invalid", "2026-10-17", 180000))
            fail("Accepted decreasing current reading")
        } catch (_: IllegalArgumentException) { }
        assertEquals(visits, db.dao().allVisits().size); assertEquals(195200, repo.currentKm())
    }
    @Test fun editingVisitMetadataOrAddingOlderWorkDoesNotInvalidateExistingNotificationCycle() = runBlocking {
        rule(); repo.updatePlanningReference("2026-10-15")
        val latest = Visit("latest", "2026-11-01", 190000, title = "Aceite")
        val line = WorkLine("line", latest.id, "Aceite", "oil")
        repo.saveVisit(latest, listOf(line), listOf("oil"))
        val generation = repo.tasks().single().generation
        db.dao().putLedger(Ledger("alert", "oil", generation, "DUE", "window", "emitted", "2026-11-01T09:00:00Z"))
        repo.saveVisit(latest.copy(notes = "Nota corregida"), listOf(line), listOf("oil"))
        assertEquals(generation, repo.tasks().single().generation)
        assertEquals("emitted", db.dao().allLedger().first { it.id == "alert" }.state)
        repo.saveVisit(Visit("old", "2026-10-16", 180000, title = "Aceite antiguo"),
            listOf(WorkLine("old-line", "old", "Aceite", "oil")), listOf("oil"))
        assertEquals(generation, repo.tasks().single().generation)
        assertEquals("emitted", db.dao().allLedger().first { it.id == "alert" }.state)
        repo.voidVisit("old")
        assertEquals("emitted", db.dao().allLedger().first { it.id == "alert" }.state)
        repo.voidVisit("latest")
        assertEquals("cancelled", db.dao().allLedger().first { it.id == "alert" }.state)
    }
    @Test fun workAndPlanningChangesCancelOnlyAutomaticRecordsWithTheSameRuleId() = runBlocking {
        rule(); repo.updatePlanningReference("2026-10-15")
        val origins = listOf("automatic", "manual", "legacy_unknown")
        suspend fun seedNotifications() {
            val generation = repo.tasks().single().generation
            origins.forEach { origin ->
                db.dao().putInbox(Inbox("inbox-$origin", "oil", "Aviso", "Mismo texto", generation,
                    state = "pending", createdAt = "2026-10-16T09:00:00Z", origin = origin))
                db.dao().putLedger(Ledger("ledger-$origin", "oil", generation,
                    if (origin == "automatic") "DUE" else "CUSTOM", "window", "emitted",
                    "2026-10-16T09:00:00Z", origin = origin))
            }
        }
        suspend fun assertOnlyAutomaticCancelled() {
            origins.forEach { origin ->
                assertEquals("Inbox origin=$origin", if (origin == "automatic") "cancelled" else "pending",
                    db.dao().allInbox().single { it.id == "inbox-$origin" }.state)
                assertEquals("Ledger origin=$origin", if (origin == "automatic") "cancelled" else "emitted",
                    db.dao().allLedger().single { it.id == "ledger-$origin" }.state)
            }
        }
        seedNotifications()
        val beforeWork = repo.tasks().single().generation
        repo.saveVisit(Visit("performed", "2026-10-16", 195200, title = "Aceite"),
            listOf(WorkLine("line", "performed", "Aceite", "oil")), listOf("oil"))
        assertNotEquals(beforeWork, repo.tasks().single().generation)
        assertOnlyAutomaticCancelled()
        seedNotifications()
        val beforePlanningEdit = repo.tasks().single().generation
        repo.updatePlanningReference("2026-10-17", 176000)
        assertNotEquals(beforePlanningEdit, repo.tasks().single().generation)
        assertOnlyAutomaticCancelled()
    }
    @Test fun catalogueUpdateKeepsOverridesAndResetKeepsRevisionHistory() = runBlocking {
        repo.initialize(); val original = repo.allRules().first { it.id == "engine-oil-filter" }
        repo.saveRule(original.copy(months = 6, source = "Personalizado; base: ${original.source}")); repo.initialize()
        assertEquals(6, repo.allRules().first { it.id == original.id }.months)
        val customRevision = repo.allRules().first { it.id == original.id }.revision
        repo.resetRule(original.id); assertEquals(original.months, repo.allRules().first { it.id == original.id }.months)
        repo.saveRule(original.copy(months = 9, source = "Personalizado; base: ${original.source}"))
        assertTrue(repo.allRules().first { it.id == original.id }.revision > customRevision)
        assertEquals(3, db.dao().allRevisions().count { it.ruleId == original.id })
    }
    @Test fun seedUpdatesOldUnmodifiedRuleButPreservesOwnerOverrideAndEventRevision() = runBlocking {
        repo.initialize()
        val gson = Gson()
        val corrected = repo.allRules().first { it.id == "coolant-renewal" }
        val old = corrected.copy(recurrence = "milestone", revision = 1)
        db.dao().putRule(RuleRecord(old.id, gson.toJson(old), 1, false))
        db.dao().putRevision(RuleRevisionRecord("${old.id}:1", old.id, 1, gson.toJson(old)))
        repo.saveVisit(Visit("old-service", "2026-10-10", 176000, title = "Refrigerante anterior"),
            listOf(WorkLine("old-line", "old-service", "Refrigerante", old.id)), listOf(old.id))
        val before = repo.allEvents().single()
        assertEquals(1, before.revision)
        val ownerBase = repo.allRules().first { it.id == "evap-service" }
        val ownerOverride = ownerBase.copy(months = 12, km = 12345, recurrence = "component", revision = 1,
            source = "Personalizado por propietario; fixture")
        db.dao().putRule(RuleRecord(ownerOverride.id, gson.toJson(ownerOverride), 1, true))
        db.dao().putRevision(RuleRevisionRecord("${ownerOverride.id}:1:owner", ownerOverride.id, 1, gson.toJson(ownerOverride)))
        repo.initialize()
        val upgraded = repo.allRules().first { it.id == old.id }
        assertEquals(3, upgraded.revision); assertEquals("component", upgraded.recurrence)
        assertEquals(before, repo.allEvents().single()); assertEquals(1, repo.allEvents().single().revision)
        assertEquals(ownerOverride, repo.allRules().first { it.id == ownerOverride.id })
        assertTrue(db.dao().allRules().first { it.id == ownerOverride.id }.ownerOverride)
        assertTrue(db.dao().allRevisions().any { it.ruleId == old.id && it.revision == 1 })
        assertTrue(db.dao().allRevisions().any { it.ruleId == old.id && it.revision == 3 })
    }
    @Test fun deletedSeedHistoryIsNotRecreatedOnNextLaunch() = runBlocking {
        repo.initialize(); val history = db.dao().allNotes().first { it.id.startsWith("seed-history:") }
        repo.deleteNote(history.id); repo.initialize(); assertTrue(db.dao().allNotes().none { it.id == history.id })
    }
    @Test fun paymentAndDoubleSaveCountOnceAndFuelDoesNotAlterMaintenance() = runBlocking {
        rule(); val visit = Visit("fixture-visit", "2026-10-15", 176000, title = "Servicio", paidCents = 30000)
        val lines = listOf(WorkLine("a", visit.id, "Aceite", "oil", 10000), WorkLine("b", visit.id, "Trabajo", costCents = 20000))
        repo.saveVisit(visit, lines, listOf("oil")); repo.saveVisit(visit, lines, listOf("oil"))
        assertEquals(1, db.dao().allVisits().size); assertEquals(1, repo.allEvents().size); assertEquals(30000L, repo.totalExpenses())
        val before = repo.allEvents(); repo.saveExpense(Expense("fuel", "2026-10-15", 6000, "Gasolina", "fuel"))
        assertEquals(before, repo.allEvents()); assertNull(repo.currentKm()); assertEquals(36000L, repo.totalExpenses())
        repo.voidVisit(visit.id); assertEquals(6000L, repo.totalExpenses()); assertTrue(repo.allEvents().none { it.active })
    }
    @Test fun workshopExtrasFuelEditAndVoidTotals() = runBlocking {
        val expenses = listOf(Expense("w", "2026-10-15", 10000, "Taller", "workshop"), Expense("e", "2026-10-15", 2500, "Extras", "extras"), Expense("f", "2026-10-15", 6000, "Gasolina", "fuel"))
        expenses.forEach { repo.saveExpense(it) }; assertEquals(18500L, repo.totalExpenses())
        repo.saveExpense(expenses[2].copy(cents = 5500)); assertEquals(18000L, repo.totalExpenses())
        repo.voidExpense("e"); assertEquals(15500L, repo.totalExpenses()); assertEquals(5, db.dao().allAudit().size)
    }
    @Test fun invalidVisitRollsBackAndEditingVoidingRecalculates() = runBlocking {
        rule(); val visit = Visit("v", "2026-10-15", 176000, title = "Aceite", paidCents = 10000)
        try { repo.saveVisit(visit, listOf(WorkLine("l", "v", "Aceite", "unknown")), listOf("unknown")); fail("Accepted unknown rule") } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().allVisits().isEmpty()); assertEquals(0L, repo.totalExpenses())
        try { repo.saveVisit(visit, listOf(WorkLine("l", "v", "Aceite", "oil")), listOf("oil"), "missing-expense"); fail("Accepted missing expense") } catch (_: IllegalStateException) { }
        assertTrue(db.dao().allVisits().isEmpty()); assertTrue(repo.allEvents().isEmpty()); assertTrue(db.dao().allLines().isEmpty()); assertEquals(0L, repo.totalExpenses())
        repo.saveVisit(visit, listOf(WorkLine("l", "v", "Aceite", "oil")), listOf("oil"))
        repo.saveVisit(visit.copy(date = "2026-11-15"), listOf(WorkLine("l", "v", "Aceite", "oil")), listOf("oil"))
        assertEquals("2027-11-15", repo.tasks().single().dueDate); assertEquals(1, repo.allEvents().count { it.active })
        assertEquals(2, repo.allEvents().size); repo.voidVisit("v"); assertEquals(195000, repo.tasks().single().dueKm)
    }
    @Test fun existingPaymentLinkDoesNotDuplicateAndDifferentPaymentsAreKept() = runBlocking {
        rule(); repo.saveExpense(Expense("paid", "2026-10-15", 30000, "Factura", "workshop"))
        val visit = Visit("v", "2026-10-15", title = "Servicio", paidCents = 30000)
        repo.saveVisit(visit, emptyList(), emptyList(), "paid"); assertEquals(30000L, repo.totalExpenses()); assertEquals(1, db.dao().allExpenses().size)
        repo.saveExpense(Expense("other", "2026-10-15", 30000, "Otro pago", "workshop")); assertEquals(60000L, repo.totalExpenses())
    }
    @Test fun movingPaymentClearsPreviousVisitAndLaterEditDoesNotDuplicateExpense() = runBlocking {
        val first = Visit("first", "2026-10-15", title = "Primer taller", paidCents = 30000)
        val second = Visit("second", "2026-10-16", title = "Segundo taller")
        repo.saveVisit(first, emptyList(), emptyList()); repo.saveVisit(second, emptyList(), emptyList())
        val payment = db.dao().allExpenses().single()
        repo.saveExpense(payment.copy(visitId = second.id))
        val previousVisit = db.dao().allVisits().first { it.id == first.id }
        assertNull(previousVisit.paidCents)
        assertEquals(30000L, db.dao().allVisits().first { it.id == second.id }.paidCents)
        repo.saveVisit(previousVisit.copy(title = "Primer taller corregido"), emptyList(), emptyList())
        assertEquals(30000L, repo.totalExpenses()); assertEquals(1, db.dao().allExpenses().size)
        repo.saveExpense(db.dao().allExpenses().single().copy(visitId = null))
        assertNull(db.dao().allVisits().first { it.id == second.id }.paidCents)
        assertEquals(30000L, repo.totalExpenses())
    }
    @Test fun operationalAdoptionIsAuditedAndCreatesNoNewCompletedWork() = runBlocking {
        repo.saveRule(Rule("phase", "Filtro", "engine", "replace", months = 24, km = 40000, source = "fixture", sourceUrl = "https://example.invalid", recurrence = "phase"))
        repo.saveVisit(Visit("v", "2026-10-15", 176000, title = "Filtro"), listOf(WorkLine("l", "v", "Filtro", "phase")), listOf("phase"))
        val before = repo.tasks().single(); assertEquals("PLANNED", before.status); assertEquals(216000, before.dueKm)
        val eventId = repo.allEvents().single().id; repo.adoptOperationalAnchor(eventId); repo.adoptOperationalAnchor(eventId)
        val after = repo.tasks().single(); assertEquals(216000, after.dueKm); assertEquals("2028-10-15", after.dueDate)
        assertEquals(before.generation, after.generation); assertEquals(1, repo.allEvents().size)
        assertEquals(1, db.dao().allAudit().count { it.action == "operational_anchor_adopted" })
    }
    @Test fun explicitOdometerCorrectionAndRetroactiveReadingPreserveHistory() = runBlocking {
        repo.saveReading(Reading("one", "2026-10-10", 180000)); repo.saveReading(Reading("old", "2026-10-01", 179000))
        assertEquals(180000, repo.currentKm())
        try { repo.saveReading(Reading("two", "2026-10-11", 178000)); fail("Accepted rollback") } catch (_: IllegalArgumentException) { }
        repo.saveReading(Reading("two", "2026-10-11", 178000), true); assertEquals(178000, repo.currentKm()); assertEquals(3, db.dao().allReadings().size)
        assertTrue(db.dao().allAudit().any { it.action == "odometer_corrected" })
    }
    @Test fun snapshotRoundTripIsCompleteIdempotentAndInvalidSnapshotIsAtomic() = runBlocking {
        rule(); repo.saveReading(Reading("r", "2026-10-10", 176000)); repo.saveExpense(Expense("e", "2026-10-10", 1250, "Test", "other"))
        repo.saveVisit(Visit("v", "2026-10-10", 176000, title = "Aceite"), listOf(WorkLine("l", "v", "Aceite", "oil")), listOf("oil"))
        val snapshot = repo.exportSnapshot(); repo.validateSnapshot(snapshot)
        val restoredDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val restored = Repository(context, restoredDb); restored.restoreSnapshot(snapshot); restored.restoreSnapshot(snapshot)
            assertEquals(1250L, restored.totalExpenses()); assertEquals(repo.allEvents(), restored.allEvents()); assertEquals(1, restoredDb.dao().allVisits().size)
            val broken = JsonParser.parseString(snapshot).asJsonObject; broken.getAsJsonArray("events")[0].asJsonObject.addProperty("visitId", "absent")
            try { restored.restoreSnapshot(broken.toString()); fail("Accepted broken reference") } catch (_: IllegalArgumentException) { }
            assertEquals(1, restoredDb.dao().allVisits().size); assertEquals(1250L, restored.totalExpenses())
        } finally { restoredDb.close() }
    }
    @Test fun restoreKeepsLaterOwnerEditsAndVoids() = runBlocking {
        val expense = Expense("e", "2026-10-10", 1250, "Test", "other"); repo.saveExpense(expense)
        val snapshot = repo.exportSnapshot(); repo.voidExpense("e"); repo.restoreSnapshot(snapshot)
        assertEquals(0L, repo.totalExpenses()); assertFalse(db.dao().allExpenses().single().active)
    }
    @Test fun restoreIntoFreshSeedKeepsCopiedOverridesNewerStandardAndExistingLocalOverrides() = runBlocking {
        repo.initialize()
        val oil = repo.allRules().first { it.id == "engine-oil-filter" }
        val evap = repo.allRules().first { it.id == "evap-service" }
        repo.saveRule(oil.copy(months = 6, notes = "Personalización copiada de aceite"))
        repo.saveRule(evap.copy(months = 8, notes = "Personalización copiada de evaporación"))
        val root = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        val copiedCoolant = root.getAsJsonArray("rules").first { it.asJsonObject.get("id").asString == "coolant-renewal" }.asJsonObject
        val oldCoolant = Gson().fromJson(copiedCoolant.get("json").asString, Rule::class.java).copy(revision = 1, recurrence = "milestone")
        copiedCoolant.addProperty("revision", 1); copiedCoolant.addProperty("json", Gson().toJson(oldCoolant))
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb); target.initialize()
            val localEvap = target.allRules().first { it.id == evap.id }.copy(months = 10, notes = "Personalización local de evaporación")
            target.saveRule(localEvap)
            val beforeLocalEvap = target.allRules().first { it.id == evap.id }
            target.restoreSnapshot(root.toString()); target.initialize()
            assertEquals(6, target.allRules().first { it.id == oil.id }.months)
            assertTrue(targetDb.dao().allRules().first { it.id == oil.id }.ownerOverride)
            val coolant = target.allRules().first { it.id == oldCoolant.id }
            assertEquals(3, coolant.revision); assertEquals("component", coolant.recurrence)
            assertEquals(beforeLocalEvap, target.allRules().first { it.id == evap.id })
            assertTrue(targetDb.dao().allRevisions().any { it.ruleId == oil.id && it.json.contains("Personalización copiada de aceite") })
            assertTrue(target.allEvents().isEmpty())
        } finally { targetDb.close() }
    }
    @Test fun maliciousDocumentPathIsRejected() = runBlocking {
        try { repo.saveDocument(Document("d", "evil.pdf", "application/pdf", "../evil.pdf", "a".repeat(64), 10)); fail("Accepted traversal") } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().allDocuments().isEmpty())
    }
}
