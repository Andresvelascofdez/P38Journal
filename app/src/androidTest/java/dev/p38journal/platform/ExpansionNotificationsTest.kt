package dev.p38journal.platform

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.p38journal.data.Deadline
import dev.p38journal.data.JournalDatabase
import dev.p38journal.data.Reading
import dev.p38journal.data.Repository
import dev.p38journal.data.TyreEvent
import dev.p38journal.domain.Tyres
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpansionNotificationsTest {
    private lateinit var context: Context
    private lateinit var db: JournalDatabase
    private lateinit var repo: Repository
    private lateinit var previous: Settings
    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:15:00Z"), ZoneOffset.UTC)
    private class Transport(var permission: Boolean = true) : NotificationTransport {
        data class Message(val id: Int, val target: String, val channel: String, val title: String)
        val messages = mutableListOf<Message>()
        val visible = mutableMapOf<Int, Message>()
        val cancelled = mutableListOf<Int>()
        override fun allowed(channel: String) = permission
        override fun emit(id: Int, channel: String, title: String, body: String, ruleId: String) {
            val message = Message(id, ruleId, channel, title)
            messages += message
            visible[id] = message
        }
        override fun cancel(id: Int) { cancelled += id; visible.remove(id) }
        override fun refreshVisible(id: Int, channel: String, title: String, body: String, ruleId: String): Boolean {
            if (id !in visible || !permission) return false
            visible[id] = Message(id, ruleId, channel, title)
            return true
        }
    }
    @Before fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        repo = Repository(context, db)
        previous = Preferences(context).current()
        Preferences(context).update(Settings(zone = "UTC", hour = 9, enabled = true, weekly = false, odometerReminder = false))
        repo.updatePlanningReference("2026-10-15", 175000)
    }
    @After fun teardown(): Unit = runBlocking {
        Preferences(context).update(previous)
        db.close()
    }
    @Test fun namedDeadlineDeduplicatesMetadataEditsAndRenewalCancels(): Unit = runBlocking {
        val deadline = Deadline("free-date", "Documento libre FIXTURE", "2026-10-15", leadDays = 2, repeatMonths = 12)
        repo.saveDeadline(deadline)
        val transport = Transport()
        val evaluator = ManualNotificationEvaluator(context, repo, clock, transport)
        coroutineScope { listOf(async { evaluator.evaluate() }, async { evaluator.evaluate() }).forEach { it.await() } }
        assertEquals(1, transport.messages.size)
        val message = transport.messages.single()
        assertEquals("deadline:free-date", message.target)
        assertTrue(message.id >= 10000)
        repo.saveDeadline(deadline.copy(name = "Nombre corregido FIXTURE", notes = "Nota nueva"))
        evaluator.evaluate("change")
        assertEquals(1, transport.messages.size)
        assertTrue(transport.visible.getValue(message.id).title.contains("Nombre corregido"))
        repo.renewDeadline(deadline.id, "2027-10-15")
        evaluator.evaluate("change"); evaluator.evaluate()
        assertFalse(transport.visible.containsKey(message.id))
        assertEquals("2026-10-15", repo.dao.allDeadlineRenewals().single().previousDueDate)
        assertEquals("2027-10-15", repo.dao.allDeadlines().single().dueDate)
        assertTrue(repo.dao.allExpenses().isEmpty())
        assertTrue(repo.dao.allInbox().all { it.state == "cancelled" })
    }
    @Test fun perEntryLeadPermissionAndDeactivationAreHonest(): Unit = runBlocking {
        val deadline = Deadline("lead", "Fecha FIXTURE", "2026-10-20", leadDays = 2)
        repo.saveDeadline(deadline)
        val transport = Transport(false)
        ManualNotificationEvaluator(context, repo, clock, transport).evaluate()
        assertTrue(repo.dao.allInbox().isEmpty())
        val nearClock = Clock.fixed(Instant.parse("2026-10-18T09:15:00Z"), ZoneOffset.UTC)
        val evaluator = ManualNotificationEvaluator(context, repo, nearClock, transport)
        evaluator.evaluate()
        assertEquals("pending", repo.dao.allInbox().single().state)
        assertTrue(transport.messages.isEmpty())
        transport.permission = true
        evaluator.evaluate("permission"); evaluator.evaluate()
        assertEquals(1, transport.messages.size)
        assertEquals("SOON", repo.dao.allLedger().single { it.id.startsWith("v6-manual:") }.kind)
        repo.saveDeadline(deadline.copy(active = false))
        evaluator.evaluate("change")
        assertTrue(transport.visible.isEmpty())
        assertEquals("cancelled", repo.dao.allInbox().single().state)
    }
    @Test fun soonToDueReplacesOneVisibleNotificationAndStaysVisible(): Unit = runBlocking {
        repo.saveDeadline(Deadline("transition", "Vencimiento FIXTURE", "2026-10-16", leadDays = 2))
        val transport = Transport()
        ManualNotificationEvaluator(context, repo, clock, transport).evaluate()
        val dueClock = Clock.fixed(Instant.parse("2026-10-16T09:15:00Z"), ZoneOffset.UTC)
        val evaluator = ManualNotificationEvaluator(context, repo, dueClock, transport)
        evaluator.evaluate(); evaluator.evaluate(); evaluator.evaluate()
        assertEquals(2, transport.messages.size)
        assertEquals(1, transport.messages.map { it.id }.distinct().size)
        assertEquals(1, transport.visible.size)
        assertTrue(transport.visible.values.single().title.contains("vencido"))
        assertEquals(setOf("SOON", "DUE"), repo.dao.allLedger().filter { it.id.startsWith("v6-manual:") }.map { it.kind }.toSet())
    }
    @Test fun collidingHashesReceivePersistentDistinctAndroidIds(): Unit = runBlocking {
        assertEquals("deadline:FB".hashCode(), "deadline:Ea".hashCode())
        repo.saveDeadline(Deadline("FB", "Uno FIXTURE", "2026-10-15"))
        repo.saveDeadline(Deadline("Ea", "Dos FIXTURE", "2026-10-15"))
        val transport = Transport()
        val evaluator = ManualNotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate(); evaluator.evaluate()
        assertEquals(2, transport.messages.size)
        assertEquals(2, transport.messages.map { it.id }.distinct().size)
        assertTrue(transport.messages.all { it.id >= 10000 })
        val first = transport.messages.first { it.target == "deadline:FB" }
        val other = transport.messages.first { it.target == "deadline:Ea" }
        repo.saveDeadline(repo.dao.allDeadlines().first { it.id == "FB" }.copy(enabled = false))
        evaluator.evaluate("change")
        assertFalse(transport.visible.containsKey(first.id))
        assertTrue(transport.visible.containsKey(other.id))
    }
    @Test fun tyresCrossExactManualLimitAndReviewCancelsWithoutResettingUse(): Unit = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("front-mount", "front", "mount", "2026-10-01", 175000, 10000)))
        repo.saveTyreEvents(listOf(TyreEvent("front-review", "front", "review", "2026-10-10", 185200, 10000)))
        repo.saveReading(Reading("before", "2026-10-15", 195199, "2026-10-15T08:00:00Z"))
        val transport = Transport()
        val evaluator = NotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate("reading")
        assertTrue(transport.messages.isEmpty())
        repo.saveReading(Reading("limit", "2026-10-15", 195200, "2026-10-15T08:01:00Z"))
        evaluator.evaluate("reading"); evaluator.evaluate()
        assertEquals(1, transport.messages.size)
        val notification = transport.messages.single()
        assertEquals("tyres:front", notification.target)
        assertEquals(Notifications.TYRES, notification.channel)
        repo.saveTyreEvents(listOf(TyreEvent("next-review", "front", "review", "2026-10-15", 195200, 10000)))
        evaluator.evaluate("work"); evaluator.evaluate()
        assertFalse(transport.visible.containsKey(notification.id))
        val state = Tyres.evaluate(repo.dao.allTyreEvents(), repo.currentKm()).first { it.axis == "front" }
        assertEquals(20200, state.usedKm)
        assertEquals(205200, state.dueKm)
    }
    @Test fun tyresWithoutReadingDoNotInferCurrentKmFromPlanningBaseline(): Unit = runBlocking {
        repo.saveTyreEvents(listOf(TyreEvent("unknown-reading", "rear", "mount", null, 150000, 10000)))
        val transport = Transport()
        ManualNotificationEvaluator(context, repo, clock, transport).evaluate("change")
        assertNull(repo.currentKm())
        assertTrue(transport.messages.isEmpty())
        assertTrue(repo.dao.allInbox().isEmpty())
    }
    @Test fun realAndroidTransportPostsManualDeadlineAndCancelsAfterRenewal(): Unit = runBlocking {
        Notifications.channels(context)
        val transport = AndroidNotificationTransport(context)
        assumeTrue("Requiere permiso de notificaciones y canal activo en el entorno Android", transport.allowed(Notifications.DOCUMENTATION))
        val deadline = Deadline("real-notification-fixture", "Documento FIXTURE Android", "2026-10-15", leadDays = 0)
        repo.saveDeadline(deadline)
        val evaluator = ManualNotificationEvaluator(context, repo, clock, transport)
        evaluator.evaluate("change")
        val id = repo.dao.allLedger().single { it.id == "v6-map:deadline:${deadline.id}" }.generation.toInt()
        val manager = context.getSystemService(NotificationManager::class.java)
        fun awaitPosted(expected: Boolean) {
            val limit = System.nanoTime() + 3_000_000_000L
            while (manager.activeNotifications.any { it.id == id } != expected && System.nanoTime() < limit) Thread.sleep(25)
            assertEquals(expected, manager.activeNotifications.any { it.id == id })
        }
        try {
            awaitPosted(true)
            assertTrue(manager.activeNotifications.any { it.id == id && it.notification.channelId == Notifications.DOCUMENTATION })
            repo.renewDeadline(deadline.id, "2027-10-15")
            evaluator.evaluate("change")
            awaitPosted(false)
        } finally { manager.cancel(id) }
    }
}
