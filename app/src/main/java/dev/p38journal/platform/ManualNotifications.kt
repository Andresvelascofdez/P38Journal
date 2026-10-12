package dev.p38journal.platform

import android.content.Context
import androidx.room.withTransaction
import dev.p38journal.data.Inbox
import dev.p38journal.data.Ledger
import dev.p38journal.data.Repository
import dev.p38journal.domain.Tyres
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManualNotificationEvaluator(
    private val context: Context,
    private val repo: Repository,
    private val clock: Clock = Clock.systemUTC(),
    private val transport: NotificationTransport = AndroidNotificationTransport(context)
) {
    companion object {
        private val mutex = Mutex()
        private const val ENTRY_PREFIX = "v6-manual:"
        private const val MAP_PREFIX = "v6-map:"
        private const val MIN_ID = 10000
    }
    private data class Candidate(val target: String, val generation: String, val kind: String,
        val channel: String, val title: String, val body: String)

    suspend fun evaluate(trigger: String = "worker") = mutex.withLock {
        Notifications.channels(context)
        val settings = Preferences(context).current()
        val now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of(settings.zone))
        val timestamp = clock.instant().toString()
        val window = if (settings.weekly) (now.toLocalDate().toEpochDay() / 7).toString() else "cycle"
        val inWindow = now.hour >= settings.hour && now.hour < minOf(settings.hour + 4, 24)
        repo.db.withTransaction {
            val original = repo.dao.allLedger()
            val reconciliation = original.firstOrNull { it.id == "v6-reconcile" && it.state == "pending" }
            val immediate = trigger in setOf("reading", "work", "permission", "change") || reconciliation != null
            val candidates = mutableListOf<Candidate>()
            for (deadline in repo.dao.allDeadlines().filter { it.active && it.enabled }) {
                val due = LocalDate.parse(deadline.dueDate)
                val kind = when {
                    !now.toLocalDate().isBefore(due) -> "DUE"
                    !now.toLocalDate().isBefore(due.minusDays(deadline.leadDays.toLong())) -> "SOON"
                    else -> continue
                }
                val generation = digest(deadline.dueDate)
                val date = due.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                candidates += Candidate("deadline:${deadline.id}", generation, kind, Notifications.DOCUMENTATION,
                    if (kind == "DUE") "${deadline.name} · vencido" else "${deadline.name} · próximo vencimiento",
                    "Vencimiento: $date. Renueva con la nueva fecha cuando se haya realizado.")
            }
            val currentKm = repo.currentKm()
            for (tyre in Tyres.evaluate(repo.dao.allTyreEvents(), currentKm)) {
                val dueKm = tyre.dueKm ?: continue
                if (currentKm == null || currentKm < dueKm) continue
                val axle = if (tyre.axis == "front") "delanteras" else "traseras"
                val formatter = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-ES"))
                candidates += Candidate("tyres:${tyre.axis}", tyre.generation, "DUE", Notifications.TYRES,
                    "P38 · revisar ruedas $axle",
                    "Toca revisar las ruedas $axle y valorar el cambio. Límite: ${formatter.format(dueKm)} km.")
            }
            fun key(candidate: Candidate) = "$ENTRY_PREFIX${candidate.target}:${candidate.generation}:${candidate.kind}:$window"
            val validKeys = candidates.map(::key).toSet()
            val maps = original.filter { it.id.startsWith(MAP_PREFIX) }.associateBy { it.ruleId }.toMutableMap()
            val allocated = maps.values.mapNotNull { it.generation.toIntOrNull() }.toMutableSet()
            suspend fun androidId(target: String): Int {
                maps[target]?.generation?.toIntOrNull()?.takeIf { it >= MIN_ID }?.let { return it }
                val range = Int.MAX_VALUE.toLong() - MIN_ID + 1
                var id = MIN_ID + ((target.hashCode().toLong() and 0x7fffffff) % range).toInt()
                while (id in allocated) id = if (id == Int.MAX_VALUE) MIN_ID else id + 1
                val mapping = Ledger("$MAP_PREFIX$target", target, id.toString(), "android-id", "mapping", "allocated", timestamp, policyVersion = 6)
                repo.dao.putLedger(mapping)
                maps[target] = mapping
                allocated += id
                return id
            }
            val entries = original.filter { it.origin == "automatic" && it.id.startsWith(ENTRY_PREFIX) }
            val inbox = repo.dao.allInbox().associateBy { it.id }
            for (old in entries.filter { it.id !in validKeys }) {
                if (old.state != "cancelled") repo.dao.putLedger(old.copy(state = "cancelled", updatedAt = timestamp))
            }
            for (old in inbox.values.filter { it.origin == "automatic" && it.id.startsWith(ENTRY_PREFIX) && it.id !in validKeys }) {
                if (old.state != "cancelled") repo.saveInbox(old.copy(state = "cancelled"))
            }
            for ((target, mapping) in maps) {
                if (!settings.enabled || candidates.none { it.target == target }) mapping.generation.toIntOrNull()?.let(transport::cancel)
            }
            for (candidate in candidates) {
                val key = key(candidate)
                val id = androidId(candidate.target)
                val mapping = maps.getValue(candidate.target)
                if (mapping.window != key) {
                    transport.cancel(id)
                    val updated = mapping.copy(window = key, updatedAt = timestamp)
                    repo.dao.putLedger(updated)
                    maps[candidate.target] = updated
                }
                val previous = entries.firstOrNull { it.id == key && it.state != "cancelled" }
                val oldInbox = inbox[key]
                val state = previous?.state ?: "pending"
                val ledger = Ledger(key, candidate.target, candidate.generation, candidate.kind, window, state, timestamp, policyVersion = 6)
                repo.dao.putLedger(ledger)
                repo.saveInbox(Inbox(key, candidate.target, candidate.title, candidate.body, candidate.generation,
                    if (oldInbox?.state == "read" && state == "emitted") "read" else state,
                    oldInbox?.createdAt ?: timestamp, policyVersion = 6))
                if (!settings.enabled || !transport.allowed(candidate.channel)) continue
                if (state == "pending" && (inWindow || immediate)) {
                    try {
                        transport.cancel(id)
                        transport.emit(id, candidate.channel, candidate.title, candidate.body, candidate.target)
                    } catch (_: SecurityException) { continue }
                    repo.dao.putLedger(ledger.copy(state = "emitted"))
                    repo.dao.allInbox().first { it.id == key }.let { repo.saveInbox(it.copy(state = if (oldInbox?.state == "read") "read" else "emitted")) }
                } else if (state == "emitted" && (oldInbox?.title != candidate.title || oldInbox?.body != candidate.body))
                    transport.refreshVisible(id, candidate.channel, candidate.title, candidate.body, candidate.target)
            }
            if (reconciliation != null) repo.dao.putLedger(reconciliation.copy(state = "reconciled", updatedAt = timestamp))
        }
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
