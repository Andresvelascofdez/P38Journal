package dev.p38journal.platform

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.p38journal.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeliveryLifecycleTest {
    @Test fun stagedUpdateUninstallAndCompleteRestore(): Unit = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("stage")
        assumeTrue("This test is explicitly orchestrated by scripts/verify-lifecycle.py", stage != null)
        val context: Context = ApplicationProvider.getApplicationContext()
        require(context.packageName.endsWith(".debug"))
        val repo = Repository(context); repo.initialize()
        val files = Files(context, repo)
        require(stage in setOf("verify-update", "restore"))
        if (stage == "restore") {
            assertFalse(repo.dao.allExpenses().any { it.id == "DELIVERY_FIXTURE_PAYMENT" })
            val input = Uri.fromFile(File(context.cacheDir, "lifecycle-import.zip"))
            assertTrue(files.previewBackup(input).contains("Copia validada"))
            files.restoreBackup(input); files.restoreBackup(input)
        }
        assertEquals(1250L, repo.dao.allExpenses().single { it.id == "DELIVERY_FIXTURE_PAYMENT" }.cents)
        assertEquals(182000, repo.currentKm())
        assertEquals("resolved", repo.dao.allNotes().single { it.id == "DELIVERY_FIXTURE_NOTE" }.status)
        val document = repo.dao.allDocuments().single { "DELIVERY_FIXTURE_NOTE" in it.tags }
        assertEquals(document.sha256, Files.checksum(File(context.filesDir, document.path)))
        assertEquals("0", context.getSharedPreferences("document-favourites", 0).getString(document.id, ""))
        assertEquals(2, repo.allEvents().count { it.visitId == "DELIVERY_FIXTURE_VISIT" && it.active })
        assertEquals(30000L, repo.dao.allExpenses().single { it.visitId == "DELIVERY_FIXTURE_VISIT" }.cents)
        assertEquals(31250L, repo.totalExpenses())
        assertEquals(62, repo.allRules().size)
        assertEquals(175000, repo.planningReference().baselineKm)
        assertEquals("2026-10-10", repo.planningReference().baselineDate)
        assertEquals("pending", repo.dao.allInbox().single { it.id == "DELIVERY_OWNER_MANUAL" }.state)
        assertEquals("cancelled", repo.dao.allInbox().single { it.id == "engine-oil-filter:fixture:INITIAL:0" }.state)
        assertEquals(198000, repo.tasks().single { it.rule.id == "engine-oil-filter" }.dueKm)
        assertFalse(repo.tasks().any { it.status == "INITIAL" })
        repo.initialize()
        assertEquals(2, repo.allEvents().count { it.visitId == "DELIVERY_FIXTURE_VISIT" && it.active })
        if (stage == "verify-update") {
            val backups = File(context.filesDir, "migration-backups").listFiles().orEmpty()
            assertTrue(backups.isNotEmpty())
            val manifest = com.google.gson.JsonParser.parseString(File(backups.single(), "manifest.json").readText()).asJsonObject
            assertEquals(2, manifest.get("sourceSchema").asInt)
            assertTrue(manifest.get("verifiedIntegrity").asBoolean)
            val source = android.database.sqlite.SQLiteDatabase.openDatabase(File(backups.single(), "p38-journal.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            try {
                assertEquals(2, source.version)
                source.rawQuery("SELECT km FROM readings WHERE id='DELIVERY_FIXTURE_READING'", null).use { assertTrue(it.moveToFirst()); assertEquals(182000, it.getInt(0)) }
            } finally { source.close() }
        }
    }
}
