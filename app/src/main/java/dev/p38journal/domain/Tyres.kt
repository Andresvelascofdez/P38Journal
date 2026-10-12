package dev.p38journal.domain

import dev.p38journal.data.TyreEvent
import java.security.MessageDigest

data class TyreState(val axis: String, val mount: TyreEvent?, val review: TyreEvent?, val dueKm: Int?,
    val usedKm: Int?, val remainingKm: Int?, val generation: String)

object Tyres {
    fun evaluate(events: List<TyreEvent>, currentKm: Int?): List<TyreState> = listOf("front", "rear").map { axis ->
        val history = events.filter { it.active && it.tracking && it.axis == axis }
            .sortedWith(compareBy<TyreEvent> { it.sequence }.thenBy { it.id })
        val mount = history.lastOrNull { it.kind in setOf("mount", "change") }
        val review = mount?.let { initial -> history.lastOrNull {
            it.kind == "review" && it.sequence > initial.sequence && it.km >= initial.km
        } }
        val anchor = review ?: mount
        val due = anchor?.takeIf { it.km >= 0 && it.intervalKm > 0 }?.let {
            runCatching { Math.addExact(it.km, it.intervalKm) }.getOrNull()
        }
        val used = if (mount != null && currentKm != null && currentKm >= mount.km) currentKm - mount.km else null
        val remaining = if (due != null && currentKm != null) due - currentKm else null
        val fingerprint = "$axis|${mount?.sequence}|${mount?.km}|${anchor?.km}|${anchor?.intervalKm}|$due"
        val generation = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        TyreState(axis, mount, review, due, used, remaining, generation)
    }
}
