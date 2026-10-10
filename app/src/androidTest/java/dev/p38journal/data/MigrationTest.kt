package dev.p38journal.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        helper.runMigrationsAndValidate(name, 2, true, JournalDatabase.MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, JournalDatabase::class.java, name).addMigrations(JournalDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(1250L, db.dao().totalExpenses())
            assertEquals(176000, db.dao().allReadings().single().km)
            assertEquals("Fixture", db.dao().allNotes().single().title)
            assertTrue(db.dao().allAudit().isEmpty())
            db.dao().putAudit(Audit("audit", "2026-10-10T09:00:00Z", "test", "expense", "fixture"))
            assertEquals(1, db.dao().allAudit().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
