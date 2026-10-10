package dev.p38journal.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SystemWorkTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @Test fun realNotificationManagerEmitsTestWithoutMaintenanceEvents() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repo = dev.p38journal.data.Repository(context)
        repo.initialize()
        val before = repo.allEvents()
        val manager = context.getSystemService(NotificationManager::class.java)
        Notifications.test(context)
        delay(300)
        assertTrue(manager.activeNotifications.any { it.id == 1002 && it.notification.contentIntent != null })
        assertEquals(before, repo.allEvents())
        manager.cancel(1002)
    }
    @Test fun realWorkManagerRunsPersistentLocalEvaluationAndInternalBackup() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = Preferences(context); val prior = prefs.current()
        try {
            prefs.update(prior.copy(enabled = false, externalFolderUri = ""))
            val manager = WorkManager.getInstance(context)
            val priorIds = manager.getWorkInfosForUniqueWork("maintenance-refresh-v1").get(5, TimeUnit.SECONDS).map { it.id }.toSet()
            Notifications.schedule(context); Notifications.refresh(context)
            var succeeded = false
            repeat(100) {
                val work = manager.getWorkInfosForUniqueWork("maintenance-refresh-v1").get(5, TimeUnit.SECONDS)
                if (work.any { it.id !in priorIds && it.state == WorkInfo.State.SUCCEEDED }) { succeeded = true; return@repeat }
                delay(200)
            }
            assertTrue("Actual WorkManager evaluation did not succeed", succeeded)
            val periodic = manager.getWorkInfosForUniqueWork("maintenance-evaluation-v1").get(5, TimeUnit.SECONDS)
            assertTrue(periodic.isNotEmpty())
            val repo = dev.p38journal.data.Repository(context)
            assertTrue(repo.dao.allInbox().isNotEmpty())
            assertTrue(Files(context, repo).internalBackups().isNotEmpty())
        } finally { prefs.update(prior) }
    }
}
