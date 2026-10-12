package dev.p38journal.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), JournalDatabase::class.java)

    @Test fun versionOneFixtureRetainsExpenseReadingAndNoteWhileAddingAudit() = runBlocking {
        val name = "migration-fixture.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO expenses(id,date,cents,concept,category,notes,visitId,active) VALUES('expense','2026-10-10',1250,'Fixture','other','',NULL,1)")
            execSQL("INSERT INTO readings(id,date,km,recordedAt) VALUES('reading','2026-10-10',176000,'2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO notes(id,kind,title,body,status,date,relatedId) VALUES('note','history','Fixture','','historical','',NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, JournalDatabase.MIGRATION_1_2, JournalDatabase.MIGRATION_2_3).close()
        val db = Room.databaseBuilder(context, JournalDatabase::class.java, name).addMigrations(JournalDatabase.MIGRATION_1_2, JournalDatabase.MIGRATION_2_3, JournalDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(1250L, db.dao().totalExpenses())
            assertEquals(176000, db.dao().allReadings().single().km)
            assertEquals("Fixture", db.dao().allNotes().single().title)
            assertEquals(1, db.dao().allAudit().size)
            db.dao().putAudit(Audit("audit", "2026-10-10T09:00:00Z", "test", "expense", "fixture"))
            assertEquals(2, db.dao().allAudit().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun versionTwoMigrationPreservesOwnerRecordsAndOnlyCancelsInitialAutomaticMetadata() = runBlocking {
        val name = "migration-v3-fixture.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO expenses(id,date,cents,concept,category,notes,visitId,active) VALUES('expense','2026-10-10',1250,'Fixture','other','',NULL,1)")
            execSQL("INSERT INTO readings(id,date,km,recordedAt) VALUES('reading','2026-10-10',182000,'2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO notes(id,kind,title,body,status,date,relatedId) VALUES('note','history','Real history','','historical','',NULL)")
            execSQL("INSERT INTO audit(id,at,action,recordId,detail) VALUES('creation','2026-10-10T09:00:00Z','plan_created','main','Fixture actual creation')")
            execSQL("INSERT INTO ledger(id,ruleId,generation,kind,window,state,updatedAt) VALUES('initial','fixture','old','INITIAL','0','pending','2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO ledger(id,ruleId,generation,kind,window,state,updatedAt) VALUES('manual','fixture','real','CUSTOM','0','pending','2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO inbox(id,ruleId,title,body,generation,state,createdAt) VALUES('initial','fixture','Same title','Fixture','old','pending','2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO inbox(id,ruleId,title,body,generation,state,createdAt) VALUES('manual','fixture','Same title','Fixture','real','pending','2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO inbox(id,ruleId,title,body,generation,state,createdAt) VALUES('fixture:orphan:INITIAL:1','fixture','Same title','Fixture','orphan','pending','2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO inbox(id,ruleId,title,body,generation,state,createdAt) VALUES('fixture:orphan:CUSTOM:1','fixture','Same title','Fixture','orphan','pending','2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO inbox(id,ruleId,title,body,generation,state,createdAt) VALUES('fixture:orphan:INITIAL:invalid','fixture','Same title','Fixture','orphan','pending','2026-10-10T09:00:00Z')")
            close()
        }
        val clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-11T08:00:00Z"), java.time.ZoneId.of("Asia/Nicosia"))
        val migration = JournalDatabase.migration2To3(clock)
        helper.runMigrationsAndValidate(name, 3, true, migration).apply {
            migration.migrate(this)
            close()
        }
        val db = Room.databaseBuilder(context, JournalDatabase::class.java, name).addMigrations(JournalDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(182000, db.dao().allReadings().single().km); assertEquals(1250L, db.dao().totalExpenses())
            assertEquals("Real history", db.dao().allNotes().single().title)
            val plan = db.dao().allPlanningReferences().single()
            assertEquals(175000, plan.baselineKm); assertEquals("2026-10-10", plan.baselineDate)
            assertEquals("cancelled", db.dao().allInbox().first { it.id == "initial" }.state)
            assertEquals("pending", db.dao().allInbox().first { it.id == "manual" }.state)
            val orphan = db.dao().allInbox().first { it.id == "fixture:orphan:INITIAL:1" }
            assertEquals("automatic", orphan.origin); assertEquals("cancelled", orphan.state)
            listOf("fixture:orphan:CUSTOM:1", "fixture:orphan:INITIAL:invalid").forEach { id ->
                val preserved = db.dao().allInbox().first { it.id == id }
                assertEquals("legacy_unknown", preserved.origin); assertEquals("pending", preserved.state)
            }
            assertEquals(1, db.dao().allAudit().count { it.action == "planning_policy_migrated" })
            assertTrue(db.dao().allEvents().isEmpty()); assertTrue(db.dao().allVisits().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun rawBackupVerifiesOldDatabaseBeforeMigrationAndLeavesOriginalUntouched() = runBlocking {
        val name = "raw-backup-fixture.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val legacy = helper.createDatabase(name, 2)
        assertTrue(legacy.enableWriteAheadLogging())
        legacy.execSQL("INSERT INTO expenses(id,date,cents,concept,category,notes,visitId,active) VALUES('expense','2026-10-10',1250,'Fixture','other','',NULL,1)")
        val folder = PreMigrationBackup.ensure(context, name)
        assertNotNull(folder)
        try {
            assertTrue(java.io.File(folder, "$name-wal").exists())
            assertEquals(folder, PreMigrationBackup.ensure(context, name))
            val manifest = com.google.gson.JsonParser.parseString(java.io.File(folder, "manifest.json").readText()).asJsonObject
            assertTrue(manifest.get("verifiedIntegrity").asBoolean); assertEquals(2, manifest.get("sourceSchema").asInt)
            val copy = android.database.sqlite.SQLiteDatabase.openDatabase(java.io.File(folder, name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            try { assertEquals(2, copy.version); copy.rawQuery("SELECT SUM(cents) FROM expenses", null).use { assertTrue(it.moveToFirst()); assertEquals(1250, it.getInt(0)) } }
            finally { copy.close() }
            val source = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            try { assertEquals(2, source.version) } finally { source.close() }
        } finally { legacy.close(); folder?.deleteRecursively(); context.deleteDatabase(name) }
    }
    @Test fun versionThreeMigrationToFourKeepsHistoricalAmountsAndReferencesWithoutSeedingNewDomains() = runBlocking {
        val name = "migration-v6-fixture.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO visits(id,date,km,kmExact,title,workshop,notes,paidCents,active,dateExact,operationalAnchor) VALUES('visit','2026-10-10',182000,1,'Trabajo anterior','','',30000,1,1,0)")
            execSQL("INSERT INTO expenses(id,date,cents,concept,category,notes,visitId,active) VALUES('expense','2026-10-10',30000,'Pago anterior','workshop','','visit',1)")
            execSQL("INSERT INTO readings(id,date,km,recordedAt) VALUES('reading','2026-10-10',182000,'2026-10-10T09:00:00Z')")
            execSQL("INSERT INTO planning_reference(id,baselineKm,baselineDate,provenance,policyVersion) VALUES('main',175000,'2026-09-15','user_selected_planning_baseline',3)")
            close()
        }
        val backup = PreMigrationBackup.ensure(context, name)
        assertNotNull(backup)
        val manifest = com.google.gson.JsonParser.parseString(java.io.File(backup, "manifest.json").readText()).asJsonObject
        assertEquals(3, manifest.get("sourceSchema").asInt); assertEquals(4, manifest.get("targetSchema").asInt)
        helper.runMigrationsAndValidate(name, 4, true, JournalDatabase.MIGRATION_3_4).apply {
            JournalDatabase.MIGRATION_3_4.migrate(this)
            close()
        }
        val db = Room.databaseBuilder(context, JournalDatabase::class.java, name).build()
        try {
            val visit = db.dao().allVisits().single()
            assertEquals("done", visit.status); assertTrue(!visit.manual); assertEquals(30000L, visit.paidCents)
            assertEquals("visit", db.dao().allExpenses().single().visitId); assertTrue(db.dao().allExpenses().single().fuelId == null)
            assertEquals(30000L, db.dao().totalExpenses()); assertEquals(182000, db.dao().allReadings().single().km)
            assertEquals("2026-09-15", db.dao().allPlanningReferences().single().baselineDate)
            assertTrue(db.dao().allFuelEntries().isEmpty()); assertTrue(db.dao().allDeadlines().isEmpty())
            assertTrue(db.dao().allDeadlineRenewals().isEmpty()); assertTrue(db.dao().allTyreEvents().isEmpty())
            assertTrue(db.dao().allEvents().isEmpty())
        } finally { db.close(); backup?.deleteRecursively(); context.deleteDatabase(name) }
    }
}
