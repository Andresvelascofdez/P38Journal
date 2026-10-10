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
import dev.p38journal.domain.Engine
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NotificationTransport {
    fun allowed(channel: String): Boolean
    fun emit(id: Int, channel: String, title: String, body: String, ruleId: String)
    fun cancel(id: Int)
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
}
object Notifications {
    const val MAINTENANCE = "maintenance-v1"
    const val CHECKS = "checks-v1"
    const val TEST = "test-v1"
    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(MAINTENANCE, "Mantenimiento", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHECKS, "Controles y kilómetros", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(TEST, "Avisos de prueba", NotificationManager.IMPORTANCE_DEFAULT)
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
    fun test(context: Context) {
        channels(context)
        AndroidNotificationTransport(context).emit(1002, TEST, "P38 Journal · prueba", "Aviso local de prueba. No registra ningún mantenimiento.", "")
    }
    fun permissionState(context: Context): String {
        channels(context)
        val transport = AndroidNotificationTransport(context)
        val states = listOf(MAINTENANCE to "mantenimiento", CHECKS to "controles", TEST to "prueba")
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
    suspend fun evaluate() = mutex.withLock {
        Notifications.channels(context)
        val settings = Preferences(context).current()
        val now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of(settings.zone))
        val today = now.toLocalDate()
        val timestamp = clock.instant().toString()
        val window = today.toEpochDay() / if (settings.weekly) 7L else Long.MAX_VALUE
        fun frequentTask(task: dev.p38journal.domain.Task) = (task.rule.days ?: Int.MAX_VALUE) <= 7 || (task.rule.months ?: Int.MAX_VALUE) <= 1
        fun taskWindow(task: dev.p38journal.domain.Task) = if (frequentTask(task)) today.toEpochDay() else window
        fun taskKey(task: dev.p38journal.domain.Task) = "${task.rule.id}:${task.generation}:${task.status}:${taskWindow(task)}"
        val tasks = Engine.evaluate(repo.allRules(), repo.allEvents(), today, repo.currentKm(), settings.leadDays, settings.leadKm)
        val active = tasks.filter { it.status != "PLANNED" }
        repo.db.withTransaction {
            val ledger = repo.dao.allLedger()
            val obsolete = ledger.filter { old -> old.ruleId !in setOf("odometer", "summary") && tasks.none { it.rule.id == old.ruleId && it.generation == old.generation && it.status != "PLANNED" } }
            for (old in obsolete) {
                repo.dao.putLedger(old.copy(state = "cancelled", updatedAt = timestamp))
            }
            val validKeys = active.map { taskKey(it) }.toSet()
            for (old in repo.dao.allInbox()) {
                if (old.id !in validKeys && old.state != "cancelled") repo.saveInbox(old.copy(state = "cancelled"))
            }
            for (task in active) {
                val key = taskKey(task)
                val prior = ledger.firstOrNull { it.id == key }
                val oldInbox = repo.dao.allInbox().firstOrNull { it.id == key }
                val state = if (oldInbox?.state == "read") "read" else prior?.state ?: "pending"
                repo.saveInbox(Inbox(id = key, ruleId = task.rule.id, title = task.rule.title,
                    body = task.reasons.joinToString("; "), generation = task.generation, state = state, createdAt = timestamp))
                if (prior == null) repo.dao.putLedger(Ledger(id = key, ruleId = task.rule.id, generation = task.generation,
                    kind = task.status, window = taskWindow(task).toString(), state = "pending", updatedAt = timestamp))
            }
            // Room serializes completion and emission; Android delivery itself cannot be atomic with SQLite.
            val current = Engine.evaluate(repo.allRules(), repo.allEvents(), today, repo.currentKm(), settings.leadDays, settings.leadKm)
            val currentActive = current.filter { it.status != "PLANNED" }
            suspend fun reconcileSummary(id: Int, group: List<dev.p38journal.domain.Task>) {
                val fingerprint = group.sortedBy { it.rule.id }.joinToString("|") { "${it.rule.id}:${it.generation}:${it.status}" }
                val key = "summary-$id"
                val old = repo.dao.allLedger().firstOrNull { it.id == key }
                if (old?.generation != fingerprint) {
                    transport.cancel(id)
                    repo.dao.putLedger(Ledger(key, "summary", fingerprint, "snapshot", "snapshot", "pending", timestamp))
                }
            }
            reconcileSummary(1001, currentActive.filter { !frequentTask(it) })
            reconcileSummary(1003, currentActive.filter { frequentTask(it) })
            if (!settings.checks) transport.cancel(1003)
            if (!settings.odometerReminder) transport.cancel(1004)
            val currentIds = currentActive.map { it.rule.id to it.generation }.toSet()
            val pending = repo.dao.allLedger().filter { it.state == "pending" && (it.ruleId to it.generation) in currentIds && currentActive.any { task -> taskKey(task) == it.id } }
            val usual = pending.filter { entry -> !frequentTask(currentActive.first { it.rule.id == entry.ruleId }) }
            val frequent = pending - usual.toSet()
            if (usual.isEmpty() && active.none { !frequentTask(it) }) transport.cancel(1001)
            if (frequent.isEmpty() && active.none { frequentTask(it) }) transport.cancel(1003)
            if (!settings.enabled) { transport.cancel(1001); transport.cancel(1003); transport.cancel(1004) }
            val inWindow = now.hour >= settings.hour && now.hour < minOf(settings.hour + 4, 24)
            suspend fun emitGroup(entries: List<Ledger>, id: Int, channel: String, title: String) {
                if (entries.isEmpty() || !settings.enabled || !inWindow || !transport.allowed(channel)) return
                val task = currentActive.first { it.rule.id == entries.first().ruleId }
                transport.emit(id, channel, title, "${entries.size} tareas para consultar en el plan.", task.rule.id)
                for (entry in entries) {
                    repo.dao.putLedger(entry.copy(state = "emitted", updatedAt = timestamp))
                    val inbox = repo.dao.allInbox().firstOrNull { it.id == entry.id }
                    if (inbox != null) repo.saveInbox(inbox.copy(state = if (inbox.state == "read") "read" else "emitted"))
                }
            }
            emitGroup(usual, 1001, Notifications.MAINTENANCE, "P38 · mantenimiento pendiente o próximo")
            if (settings.checks) emitGroup(frequent, 1003, Notifications.CHECKS, "P38 · controles de uso")
            if (settings.enabled && settings.odometerReminder && inWindow && transport.allowed(Notifications.CHECKS)) {
                val latest = repo.dao.allReadings().maxByOrNull { it.date }
                if (latest == null || LocalDate.parse(latest.date).plusDays(7) <= today) {
                    val key = "odometer:$window"
                    if (repo.dao.allLedger().none { it.id == key && it.state == "emitted" }) {
                        transport.emit(1004, Notifications.CHECKS, "P38 · actualizar kilómetros", "Registra una lectura real para evaluar los límites por kilómetros.", "odometer")
                        repo.dao.putLedger(Ledger(key, "odometer", "reminder", "odometer", window.toString(), "emitted", timestamp))
                    }
                } else transport.cancel(1004)
            }
        }
    }
}
