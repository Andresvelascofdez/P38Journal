package dev.p38journal.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import dev.p38journal.domain.Rule
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
class CustomRulesAndPaymentsTest {
    private lateinit var context: Context
    private lateinit var db: JournalDatabase
    private lateinit var repo: Repository
    private val clock = Clock.fixed(Instant.parse("2026-10-16T09:00:00Z"), ZoneId.of("Asia/Nicosia"))
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        repo = Repository(context, db, clock)
    }
    @After fun tearDown() { db.close() }

    @Test fun customCreateEditDisableAndSnapshotKeepIdentityHistoryAndReference() = runBlocking {
        repo.updatePlanningReference("2026-10-15")
        val own = Rule("own", "Operación propia", "custom", "custom", km = 10000, months = 6,
            origin = "custom", notes = "Nota")
        repo.saveCustomRule(own); repo.saveCustomRule(own)
        assertEquals(1, repo.allRules().size); assertEquals(1, db.dao().allRevisions().size)
        assertEquals(185000, repo.tasks().single().dueKm); assertNull(repo.originalRule("own"))
        repo.saveVisit(Visit("job", "2026-10-16", 185200, title = "Servicio"),
            listOf(WorkLine("line", "job", "Operación propia", "own")), listOf("own"))
        val performed = repo.allEvents()
        repo.saveCustomRule(repo.allRules().single().copy(title = "Nombre nuevo", km = 12000))
        repo.setRuleEnabled("own", false)
        assertFalse(repo.allRules().single().enabled); assertEquals(197200, repo.tasks().single().dueKm)
        assertEquals(performed, repo.allEvents()); assertEquals(175000, repo.planningReference().baselineKm)
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb, clock); target.initialize(); target.restoreSnapshot(repo.exportSnapshot())
            val restored = target.allRules().single { it.id == "own" }
            assertEquals("custom", restored.origin); assertFalse(restored.enabled); assertEquals("", restored.source)
            assertEquals(performed, target.allEvents()); assertEquals("2026-10-15", target.planningReference().baselineDate)
            assertEquals(197200, target.tasks().single { it.rule.id == "own" }.dueKm)
        } finally { targetDb.close() }
    }

    @Test fun customUniqueTaskCompletesOnceAndVoidingRestoresItsOriginalAbsoluteLimits() = runBlocking {
        repo.saveCustomRule(Rule("once", "Cita taller", "custom", "custom", recurrence = "once", origin = "custom",
            dueDate = "2026-10-20", dueKm = 177000))
        repo.saveVisit(Visit("job", "2026-10-16", title = "Cita realizada"),
            listOf(WorkLine("line", "job", "Cita taller", "once")), listOf("once"))
        assertNull(repo.tasks().single().dueDate); assertNull(repo.tasks().single().dueKm)
        repo.voidVisit("job")
        assertEquals("2026-10-20", repo.tasks().single().dueDate); assertEquals(177000, repo.tasks().single().dueKm)
        assertEquals(1, repo.allEvents().size); assertFalse(repo.allEvents().single().active)
    }

    @Test fun phasePersonalizationBecomesSimpleAndOriginalManufacturerCanBeRestored() = runBlocking {
        repo.initialize(); repo.updatePlanningReference("2026-10-15")
        val original = repo.allRules().first { it.recurrence == "phase_b" }
        repo.saveRuleOverride(original.copy(title = "Personalizado", months = 6, km = 10000, notes = "Prueba"))
        val custom = repo.allRules().single { it.id == original.id }
        assertEquals("component", custom.recurrence); assertNull(custom.firstKm); assertNull(custom.firstMonths)
        assertEquals("personalized", custom.origin); assertEquals(original.revision, custom.manufacturerRevision)
        assertEquals(original, repo.originalRule(original.id)); assertEquals(original.source, custom.source)
        assertEquals(185000, repo.tasks().single { it.rule.id == original.id }.dueKm)
        assertEquals("2027-04-15", repo.tasks().single { it.rule.id == original.id }.dueDate)
        repo.saveVisit(Visit("job", "2026-10-16", 195200, title = "Trabajo"),
            listOf(WorkLine("line", "job", original.title, original.id)), listOf(original.id))
        val event = repo.allEvents().single()
        assertEquals(205200, repo.tasks().single { it.rule.id == original.id }.dueKm)
        repo.resetRule(original.id); repo.initialize()
        assertEquals(original, repo.allRules().single { it.id == original.id })
        assertEquals(235200, repo.tasks().single { it.rule.id == original.id }.dueKm)
        assertEquals(event, repo.allEvents().single())
        assertTrue(db.dao().allRevisions().any { it.ruleId == original.id && it.revision == custom.revision })
    }

    @Test fun ruleNameNotesPreserveEmissionButIntervalAndReminderToggleCancelIt() = runBlocking {
        repo.initialize()
        val original = repo.allRules().first { it.id == "engine-oil-filter" }
        val task = repo.tasks().single { it.rule.id == original.id }
        db.dao().putLedger(Ledger("alert", original.id, task.generation, "DUE", "window", "emitted", clock.instant().toString()))
        repo.saveRuleOverride(original.copy(title = "Nombre nuevo", notes = "Notas nuevas"))
        assertEquals(task.generation, repo.tasks().single { it.rule.id == original.id }.generation)
        assertEquals("emitted", db.dao().allLedger().single().state)
        repo.resetRule(original.id)
        assertEquals("emitted", db.dao().allLedger().single().state)
        repo.saveRuleOverride(repo.allRules().single { it.id == original.id }.copy(km = 10000))
        assertEquals("cancelled", db.dao().allLedger().single().state)
        val current = repo.tasks().single { it.rule.id == original.id }
        db.dao().putLedger(Ledger("alert", original.id, current.generation, "DUE", "window", "emitted", clock.instant().toString()))
        repo.setRuleEnabled(original.id, false)
        assertEquals(current.dueKm, repo.tasks().single { it.rule.id == original.id }.dueKm)
        assertEquals("cancelled", db.dao().allLedger().single().state)
    }

    @Test fun voidLinkedExpenseAllowsNewPaymentAndVisitEditsKeepPersonalizedExpenseMetadata() = runBlocking {
        val visit = Visit("visit", "2026-10-16", 180000, title = "Servicio", paidCents = 30000)
        repo.saveVisit(visit, emptyList(), emptyList())
        val original = db.dao().allExpenses().single()
        repo.voidExpense(original.id)
        assertNull(db.dao().allExpenses().single().visitId); assertNull(db.dao().allVisits().single().paidCents)
        val payment = Expense("replacement", "2026-10-14", 31000, "Concepto propio", "extras", "Nota pago", "visit")
        repo.saveExpense(payment)
        repo.saveVisit(db.dao().allVisits().single().copy(km = 181000, title = "Título editado", notes = "Nota visita"), emptyList(), emptyList())
        assertEquals(payment, db.dao().allExpenses().single { it.active })
        assertEquals(31000L, db.dao().allVisits().single().paidCents); assertEquals(31000L, repo.totalExpenses())
        repo.voidVisit("visit")
        assertTrue(db.dao().allExpenses().none { it.active }); assertTrue(db.dao().allExpenses().all { it.visitId == null })
        repo.saveVisit(db.dao().allVisits().single().copy(notes = "Nota anulada"), emptyList(), emptyList())
        assertEquals(0L, repo.totalExpenses()); assertNull(db.dao().allVisits().single().paidCents)
    }

    @Test fun inactiveExpenseSavedDirectlyDetachesAndOldPaymentIsNeverSilentlyResurrected() = runBlocking {
        repo.saveVisit(Visit("visit", title = "Servicio", paidCents = 30000), emptyList(), emptyList())
        val old = db.dao().allExpenses().single()
        repo.saveExpense(old.copy(active = false))
        assertNull(db.dao().allExpenses().single().visitId)
        repo.saveVisit(db.dao().allVisits().single().copy(paidCents = 32000), emptyList(), emptyList())
        assertEquals(2, db.dao().allExpenses().size); assertEquals(32000L, repo.totalExpenses())
        assertFalse(db.dao().allExpenses().single { it.id == old.id }.active)
        assertNotEquals(old.id, db.dao().allExpenses().single { it.active }.id)
        assertTrue(db.dao().allAudit().any { it.action == "expense_edited" })
    }

    @Test fun legacySnapshotsTwoAndThreeDetachInactiveLinksAndPermitReplacementWithoutLosingAudit() = runBlocking {
        repo.saveVisit(Visit("visit", title = "Servicio", paidCents = 30000), emptyList(), emptyList())
        val base = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        val expenseId = base.getAsJsonArray("expenses").single().asJsonObject.get("id").asString
        base.getAsJsonArray("expenses").single().asJsonObject.addProperty("active", false)
        for (version in listOf(2, 3)) {
            val snapshot = JsonParser.parseString(base.toString()).asJsonObject
            snapshot.addProperty("schemaVersion", version); if (version == 2) snapshot.remove("planning")
            val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
            try {
                val target = Repository(context, targetDb, clock); target.restoreSnapshot(snapshot.toString())
                assertNull(targetDb.dao().allExpenses().single().visitId); assertNull(targetDb.dao().allVisits().single().paidCents)
                target.saveExpense(Expense("new", "2026-10-16", 33000, "Nuevo pago", visitId = "visit"))
                assertEquals(33000L, target.totalExpenses()); assertEquals(2, targetDb.dao().allExpenses().size)
                assertTrue(targetDb.dao().allAudit().any { it.action == "expense_link_normalized" && it.recordId == expenseId && it.detail.contains("visit") })
            } finally { targetDb.close() }
        }
    }

    @Test fun manufacturerPhaseMayBeExplicitlyPersonalizedAsAnAbsoluteUniqueTask() = runBlocking {
        repo.initialize(); repo.updatePlanningReference("2026-10-15")
        val original = repo.allRules().first { it.recurrence == "phase_b" }
        repo.saveRuleOverride(original.copy(recurrence = "once", months = null, km = null, dueDate = "2026-10-20", dueKm = 177000))
        val rule = repo.allRules().single { it.id == original.id }
        assertEquals("once", rule.recurrence); assertEquals("personalized", rule.origin)
        assertNull(rule.firstKm); assertNull(rule.firstMonths)
        assertEquals(177000, repo.tasks().single { it.rule.id == original.id }.dueKm)
        repo.saveVisit(Visit("once-job", "2026-10-16", title = "Realizado"),
            listOf(WorkLine("once-line", "once-job", rule.title, rule.id)), listOf(rule.id))
        assertNull(repo.tasks().single { it.rule.id == original.id }.dueKm)
        assertEquals(original, repo.originalRule(original.id))
    }

    @Test fun restoreActivePaymentOverLegacyInactiveLinkNormalizesExistingDataAndCountsOnce() = runBlocking {
        repo.saveVisit(Visit("visit", title = "Servicio", paidCents = 30000), emptyList(), emptyList())
        val snapshot = repo.exportSnapshot()
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            targetDb.dao().putVisit(Visit("visit", title = "Servicio local", paidCents = 10000))
            targetDb.dao().putExpense(Expense("legacy-inactive", "2026-10-10", 10000, "Pago anulado", visitId = "visit", active = false))
            val target = Repository(context, targetDb, clock)
            target.restoreSnapshot(snapshot); target.restoreSnapshot(snapshot)
            assertEquals(30000L, target.totalExpenses()); assertEquals(2, targetDb.dao().allExpenses().size)
            assertNull(targetDb.dao().allExpenses().single { it.id == "legacy-inactive" }.visitId)
            assertEquals(30000L, targetDb.dao().allVisits().single().paidCents)
            assertEquals("Servicio local", targetDb.dao().allVisits().single().title)
            assertTrue(targetDb.dao().allAudit().any { it.action == "expense_link_normalized" && it.recordId == "legacy-inactive" })
            assertTrue(target.allEvents().isEmpty())
        } finally { targetDb.close() }
    }

    @Test fun oldRuleJsonDefaultsEnabledAndRetainsOwnerIdentityAndLegacyAbsoluteOnceTarget() = runBlocking {
        repo.saveCustomRule(Rule("legacy-own", "Propia anterior", "custom", "custom", km = 10000, origin = "custom"))
        val old = JsonParser.parseString(repo.exportSnapshot()).asJsonObject
        old.addProperty("schemaVersion", 2); old.remove("planning")
        val record = old.getAsJsonArray("rules").single().asJsonObject
        val json = JsonParser.parseString(record.get("json").asString).asJsonObject
        listOf("origin", "notes", "enabled", "dueDate", "dueKm", "manufacturerRevision").forEach { json.remove(it) }
        json.addProperty("recurrence", "once"); json.addProperty("firstKm", 177000)
        record.addProperty("json", json.toString())
        val targetDb = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        try {
            val target = Repository(context, targetDb, clock); target.initialize(); target.restoreSnapshot(old.toString())
            val rule = target.allRules().single { it.id == "legacy-own" }
            assertEquals("custom", rule.origin); assertTrue(rule.enabled)
            assertEquals(177000, target.tasks().single { it.rule.id == rule.id }.dueKm)
            assertTrue(target.allEvents().isEmpty()); assertNull(target.currentKm())
        } finally { targetDb.close() }
    }
    @Test fun linkedPaymentEditAllowsVisitMetadataEditWithoutInventingANewAllocation() = runBlocking {
        val visit = Visit("allocated", "2026-10-16", 180000, title = "Servicio", paidCents = 30000)
        val lines = listOf(WorkLine("a", visit.id, "Primera operación", costCents = 15000),
            WorkLine("b", visit.id, "Segunda operación", costCents = 15000))
        repo.saveVisit(visit, lines, emptyList())
        val payment = db.dao().allExpenses().single().copy(cents = 31000, date = "2026-10-14", concept = "Factura corregida", category = "extras")
        repo.saveExpense(payment)
        val canonicalVisit = db.dao().allVisits().single()
        assertEquals(31000L, canonicalVisit.paidCents)
        repo.saveVisit(canonicalVisit.copy(km = 181000, notes = "Solo datos de la visita"), lines, emptyList())
        assertEquals(payment, db.dao().allExpenses().single()); assertEquals(31000L, repo.totalExpenses())
        assertEquals(lines.toSet(), db.dao().allLines().toSet())
        try {
            repo.saveVisit(db.dao().allVisits().single(), listOf(lines[0].copy(costCents = 16000), lines[1].copy(costCents = 14000)), emptyList())
            fail("Accepted a changed allocation inconsistent with the payment")
        } catch (_: IllegalArgumentException) { }
        assertEquals(lines.toSet(), db.dao().allLines().toSet()); assertEquals(31000L, repo.totalExpenses())
        repo.saveVisit(db.dao().allVisits().single(), listOf(lines[0].copy(costCents = 16000), lines[1]), emptyList())
        assertEquals(31000L, db.dao().allLines().sumOf { it.costCents ?: 0L })
    }
}
