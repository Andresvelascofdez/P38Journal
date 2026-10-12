package dev.p38journal.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.p38journal.MainActivity
import dev.p38journal.R
import dev.p38journal.data.Inbox
import dev.p38journal.data.Ledger
import dev.p38journal.data.Repository
import java.time.Clock
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NotificationTransport {
    fun allowed(channel: String): Boolean
    fun emit(id: Int, channel: String, title: String, body: String, ruleId: String)
    fun cancel(id: Int)
    fun refreshVisible(id: Int, channel: String, title: String, body: String, ruleId: String): Boolean = false
}
class AndroidNotificationTransport(private val context: Context) : NotificationTransport {
    override fun allowed(channel: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    override fun emit(id: Int, channel: String, title: String, body: String, ruleId: String) {
        if (!allowed(channel)) return
        val intent = Intent(context, MainActivity::class.java).putExtra("ruleId", ruleId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_journal).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
    override fun cancel(id: Int) { context.getSystemService(NotificationManager::class.java).cancel(id) }
    override fun refreshVisible(id: Int, channel: String, title: String, body: String, ruleId: String): Boolean {
        if (!allowed(channel) || context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == id }) return false
        emit(id, channel, title, body, ruleId)
        return true
    }
}
object Notifications {
    const val MAINTENANCE = "maintenance-v1"
    const val CHECKS = "checks-v1"
    const val TEST = "test-v1"
    const val DOCUMENTATION = "documentation-v1"
    const val TYRES = "tyres-v1"
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(MAINTENANCE, "Mantenimiento", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHECKS, "Controles y kilómetros", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(TEST, "Avisos de prueba", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(DOCUMENTATION, "Documentación y vencimientos", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(TYRES, "Revisión de ruedas", NotificationManager.IMPORTANCE_DEFAULT)
        ))
    }
    fun schedule(context: Context) {
        channels(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintenance-evaluation-v1", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.HOURS).build())
    }
    fun refresh(context: Context) {
        channels(context)
        WorkManager.getInstance(context).enqueueUniqueWork("maintenance-refresh-v1", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<MaintenanceWorker>().build())
    }
    suspend fun evaluateNow(context: Context, trigger: String = "change") {
        refresh(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val repo = Repository(context)
            repo.initialize()
            NotificationEvaluator(context, repo).evaluate(trigger)
        }
    }
    fun test(context: Context) {
        channels(context)
        AndroidNotificationTransport(context).emit(1002, TEST, "P38 Journal · prueba", "Aviso local de prueba. No registra ningún mantenimiento.", "")
    }
    fun permissionState(context: Context): String {
        channels(context)
        val transport = AndroidNotificationTransport(context)
        val states = listOf(MAINTENANCE to "mantenimiento", CHECKS to "controles", DOCUMENTATION to "documentación", TYRES to "ruedas", TEST to "prueba")
        return states.joinToString(" · ") { (channel, title) -> "$title: ${if (transport.allowed(channel)) "permitido" else "bloqueado"}" }
    }
    fun openSettings(context: Context) {
        context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repo = Repository(applicationContext)
        repo.initialize()
        NotificationEvaluator(applicationContext, repo).evaluate()
        Files(applicationContext, repo).rotateInternalBackup()
        Result.success()
    } catch (e: java.io.IOException) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
class NotificationEvaluator(
    private val context: Context,
    private val repo: Repository,
    private val clock: Clock = Clock.systemUTC(),
    private val transport: NotificationTransport = AndroidNotificationTransport(context)
) {
    companion object { private val mutex = Mutex() }
    suspend fun evaluate(trigger: String = "worker") = mutex.withLock {
        Notifications.channels(context)
        val settings = Preferences(context).current()
        val now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of(settings.zone))
        val timestamp = clock.instant().toString()
        val window = if (settings.weekly) (now.toLocalDate().toEpochDay() / 7).toString() else "cycle"
        fun taskKey(task: dev.p38journal.domain.Task) = "v3:${task.rule.id}:${task.generation}:${task.status}:$window"
        fun active(task: dev.p38journal.domain.Task) = task.rule.enabled && task.status in setOf("DUE", "SOON") && task.rule.scheduleVisibility in setOf("maintenance", "service")
        fun generated(entry: Ledger) = entry.origin == "automatic" && (entry.id.startsWith("v3:") || entry.id.startsWith("${entry.ruleId}:") && entry.kind in setOf("INITIAL", "DUE", "SOON"))
        transport.cancel(1003)
        repo.db.withTransaction {
            val tasks = repo.tasks(now.toLocalDate(), settings.leadDays, settings.leadKm)
            val current = tasks.filter(::active)
            val validKeys = current.map(::taskKey).toSet()
            val ledger = repo.dao.allLedger()
            val reconcile = ledger.firstOrNull { it.id == "v3-reconcile" && it.state == "pending" }
            val immediate = trigger in setOf("reading", "work", "permission", "change") || reconcile?.kind == "reading"
            val automaticIds = ledger.filter(::generated).map { it.id }.toSet()
            for (old in ledger.filter { generated(it) && it.id !in validKeys && it.state != "cancelled" }) {
                repo.dao.putLedger(old.copy(state = "cancelled", updatedAt = timestamp))
            }
            val inbox = repo.dao.allInbox().associateBy { it.id }
            for (old in inbox.values) {
                if (old.origin == "automatic" && (old.id.startsWith("v3:") || old.id in automaticIds) && old.id !in validKeys && old.state != "cancelled") {
                    repo.saveInbox(old.copy(state = "cancelled"))
                }
            }
            for (task in current) {
                val key = taskKey(task)
                val legacyKey = task.legacyGeneration?.let { "v3:${task.rule.id}:$it:${task.status}:$window" }
                val previous = ledger.firstOrNull { it.id == key && it.origin == "automatic" }
                    ?: ledger.firstOrNull { it.id == legacyKey && it.origin == "automatic" && it.state != "cancelled" }
                val state = previous?.state?.takeUnless { it == "cancelled" } ?: "pending"
                val old = inbox[key] ?: legacyKey?.let(inbox::get)
                val row = Inbox(key, task.rule.id, task.rule.title, task.reasons.joinToString("; "), task.generation,
                    if (old?.state == "read" && state != "pending") "read" else state, old?.createdAt ?: timestamp)
                repo.saveInbox(row)
                if (previous == null || previous.state == "cancelled" || previous.id != key) {
                    repo.dao.putLedger(Ledger(key, task.rule.id, task.generation, task.status, window, state, previous?.updatedAt ?: timestamp))
                }
            }
            val fingerprint = current.sortedBy { it.rule.id }.joinToString("|") { "${it.rule.id}:${it.generation}:${it.status}:${it.rule.title}" }
            val summaryKey = "v3-summary-1001"
            val summary = ledger.firstOrNull { it.id == summaryKey }
            val summaryChanged = summary?.generation != fingerprint
            if (summaryChanged) {
                repo.dao.putLedger(Ledger(summaryKey, "summary", fingerprint, "snapshot", "snapshot", "pending", timestamp))
            }
            val pending = repo.dao.allLedger().filter { generated(it) && it.state == "pending" && it.id in validKeys }
            val inWindow = now.hour >= settings.hour && now.hour < minOf(settings.hour + 4, 24)
            if (!settings.enabled || current.isEmpty()) transport.cancel(1001)
            if (settings.enabled && current.isNotEmpty() && transport.allowed(Notifications.MAINTENANCE)) {
                val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-ES"))
                val body = current.joinToString("\n") { task ->
                    val date = task.dueDate?.let { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
                    val limit = listOfNotNull(task.dueKm?.let { "${formatter.format(it)} km" }, date).joinToString(" o ")
                    "${if (task.status == "DUE") "Pendiente" else "Pr\u00f3ximamente"}: ${task.rule.title} \u00b7 $limit"
                }
                val groups = current.groupBy { "${it.rule.serviceGroup.ifBlank { it.rule.id }}:${it.dueDate}:${it.dueKm}" }
                val target = if (groups.size == 1) current.first().rule.id else "maintenance-summary"
                val title = if (current.any { it.status == "DUE" }) "P38 \u00b7 mantenimiento pendiente" else "P38 \u00b7 pr\u00f3ximo mantenimiento"
                if (pending.isNotEmpty() && (inWindow || immediate)) {
                    transport.cancel(1001)
                    transport.emit(1001, Notifications.MAINTENANCE, title, body, target)
                for (entry in pending) {
                    repo.dao.putLedger(entry.copy(state = "emitted", updatedAt = timestamp))
                    repo.dao.allInbox().firstOrNull { it.id == entry.id }?.let { old ->
                        repo.saveInbox(old.copy(state = if (old.state == "read") "read" else "emitted"))
                    }
                }
                } else if (summaryChanged) transport.refreshVisible(1001, Notifications.MAINTENANCE, title, body, target)
            }
            if (reconcile != null) repo.dao.putLedger(reconcile.copy(state = "reconciled", updatedAt = timestamp))
            evaluateOdometerReminder(settings, now, timestamp, inWindow)
        }
        ManualNotificationEvaluator(context, repo, clock, transport).evaluate(trigger)
    }

    private suspend fun evaluateOdometerReminder(settings: Settings, now: ZonedDateTime, timestamp: String, inWindow: Boolean) {
        val latest = repo.dao.allReadings().firstOrNull()
        val plan = repo.planningReference()
        val generation = latest?.id ?: "plan:${plan.baselineDate}"
        val referenceDate = java.time.LocalDate.parse(latest?.date ?: plan.baselineDate)
        val due = !now.toLocalDate().isBefore(referenceDate.plusDays(7))
        val window = if (settings.weekly) (now.toLocalDate().toEpochDay() / 7).toString() else "cycle"
        val key = "v4-odometer:$generation:$window"
        val relevant = settings.enabled && settings.odometerReminder && due
        val ledger = repo.dao.allLedger().filter { it.ruleId == "odometer" && it.origin == "automatic" }
        for (old in ledger.filter { it.id != key || !relevant }) {
            if (old.state != "cancelled") repo.dao.putLedger(old.copy(state = "cancelled", updatedAt = timestamp))
        }
        for (old in repo.dao.allInbox().filter { it.ruleId == "odometer" && it.origin == "automatic" && (it.id != key || !relevant) }) {
            if (old.state != "cancelled") repo.saveInbox(old.copy(state = "cancelled"))
        }
        if (!relevant) { transport.cancel(1004); return }
        val previous = ledger.firstOrNull { it.id == key && it.state != "cancelled" }
        val old = repo.dao.allInbox().firstOrNull { it.id == key }
        val state = previous?.state ?: "pending"
        val title = "P38 · actualizar kilómetros"
        val body = "Registra una lectura real para evaluar los límites por kilómetros."
        repo.saveInbox(Inbox(key, "odometer", title, body, generation,
            if (old?.state == "read" && state == "emitted") "read" else state, old?.createdAt ?: timestamp))
        if (previous == null) repo.dao.putLedger(Ledger(key, "odometer", generation, "odometer", window, "pending", timestamp))
        if (state == "pending" && inWindow && transport.allowed(Notifications.CHECKS)) {
            transport.emit(1004, Notifications.CHECKS, title, body, "odometer")
            repo.dao.putLedger(Ledger(key, "odometer", generation, "odometer", window, "emitted", timestamp))
            repo.dao.allInbox().first { it.id == key }.let { repo.saveInbox(it.copy(state = "emitted")) }
        }
    }
}
