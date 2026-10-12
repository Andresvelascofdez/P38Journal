package dev.p38journal.data

import androidx.room.withTransaction
import com.google.gson.Gson
import dev.p38journal.domain.Tyres
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class ExpandedRecords(private val repo: Repository, private val clock: Clock) {
    private val dao get() = repo.dao
    private val gson = Gson()
    private suspend fun audit(action: String, id: String, before: Any?, after: Any? = null) =
        dao.putAudit(Audit(at = Instant.now(clock).toString(), action = action, recordId = id, detail = gson.toJson(before) + " -> " + gson.toJson(after)))
    private suspend fun changed(target: String? = null) {
        if (target != null) {
            dao.cancelInbox(target)
            dao.allLedger().filter { it.ruleId == target && it.origin == "automatic" && !it.id.startsWith("v6-map:") }
                .forEach { dao.putLedger(it.copy(state = "cancelled")) }
        }
        dao.putLedger(Ledger("v6-reconcile", "system", UUID.randomUUID().toString(), "manual", "change", "pending", Instant.now(clock).toString()))
    }

    suspend fun saveManualWork(visit: Visit, existingExpenseId: String?) = repo.db.withTransaction {
        require(dao.allTyreEvents().none { it.visitId == visit.id }) { "Corrige este trabajo desde la operación conjunta de ruedas" }
        require(dao.visitLines(visit.id).none { it.ruleId != null }) { "Edita las operaciones vinculadas desde el registro de mantenimiento" }
        val linked = dao.allExpenses().firstOrNull { it.active && it.visitId == visit.id }
        val manual = visit.copy(manual = true, dateExact = visit.date.isNotBlank() && visit.dateExact,
            paidCents = if (visit.status == "planned") null else visit.paidCents ?: linked?.cents)
        val lines = dao.visitLines(visit.id).ifEmpty { listOf(WorkLine(id = "manual:${visit.id}", visitId = visit.id, title = visit.title)) }
        repo.saveVisit(manual, lines, emptyList(), existingExpenseId)
    }

    suspend fun saveFuelEntry(entry: FuelEntry) = repo.db.withTransaction {
        validateFuel(entry)
        val previous = dao.allFuelEntries().firstOrNull { it.id == entry.id }
        require(previous == null || previous.active || !entry.active) { "El repostaje está anulado; registra uno nuevo" }
        if (!entry.active) { if (previous != null) voidFuelEntry(entry.id); return@withTransaction }
        val expenses = dao.allExpenses()
        val oldPayment = previous?.expenseId?.let { id -> expenses.firstOrNull { it.id == id } }
        require(entry.expenseId == null || previous?.expenseId == null || entry.expenseId == previous.expenseId) { "El vínculo del repostaje conserva su gasto original" }
        val selected = entry.expenseId?.let { id -> expenses.firstOrNull { it.id == id } ?: error("Gasto no encontrado") }
        val payment = oldPayment ?: selected
        require(payment == null || payment.active && payment.visitId == null && (payment.fuelId == null || payment.fuelId == entry.id)) { "Gasto no disponible para este repostaje" }
        val expenseId = payment?.id ?: "fuel:${entry.id}".takeIf { id -> expenses.none { it.id == id } } ?: UUID.randomUUID().toString()
        val saved = entry.copy(expenseId = expenseId)
        val expense = payment?.copy(date = entry.date, cents = entry.totalCents, category = "fuel", fuelId = entry.id)
            ?: Expense(expenseId, entry.date, entry.totalCents, "Repostaje", "fuel", fuelId = entry.id)
        if (previous == saved && payment == expense) return@withTransaction
        dao.putFuelEntry(saved); dao.putExpense(expense)
        audit(if (previous == null) "fuel_created" else "fuel_edited", entry.id, previous, saved)
        changed()
    }
    suspend fun voidFuelEntry(id: String) = repo.db.withTransaction {
        val fuel = dao.allFuelEntries().firstOrNull { it.id == id } ?: error("Repostaje no encontrado")
        if (!fuel.active) return@withTransaction
        dao.putFuelEntry(fuel.copy(active = false))
        fuel.expenseId?.let { expenseId -> dao.allExpenses().firstOrNull { it.id == expenseId }?.let {
            dao.putExpense(it.copy(active = false, fuelId = null))
        } }
        audit("fuel_voided", id, fuel); changed()
    }
    fun validateFuel(entry: FuelEntry) {
        id(entry.id); LocalDate.parse(entry.date)
        require(entry.km == null || entry.km >= 0); require(entry.litresMilli == null || entry.litresMilli > 0)
        require(entry.totalCents >= 0)
    }
    suspend fun validateFuelExpense(expense: Expense, previous: Expense?) {
        require(expense.visitId == null || expense.fuelId == null) { "Un gasto no puede pertenecer a trabajo y repostaje" }
        if (expense.active && previous?.fuelId != null) require(expense.fuelId == previous.fuelId) { "Edita el vínculo desde Repostajes" }
        expense.fuelId?.let { fuelId ->
            val fuel = dao.allFuelEntries().firstOrNull { it.id == fuelId } ?: error("Repostaje no encontrado")
            require(fuel.expenseId == expense.id && (!expense.active || fuel.active)) { "Vínculo de repostaje no válido" }
            require(expense.category == "fuel") { "El gasto de repostaje pertenece a Gasolina" }
        }
    }
    suspend fun expenseSaved(previous: Expense?, saved: Expense) {
        val fuelId = previous?.fuelId ?: saved.fuelId ?: return
        val fuel = dao.allFuelEntries().firstOrNull { it.id == fuelId } ?: return
        val active = saved.active && saved.fuelId == fuelId
        val revised = fuel.copy(date = saved.date, totalCents = saved.cents, active = active)
        if (revised != fuel) { dao.putFuelEntry(revised); audit("fuel_synced_from_expense", fuel.id, fuel, revised); changed() }
    }
    suspend fun normalizeFuelLinks() {
        val expenses = dao.allExpenses().associateBy { it.id }
        dao.allFuelEntries().filter { it.active }.forEach { fuel ->
            val expense = fuel.expenseId?.let(expenses::get)
            if (expense == null || !expense.active || expense.visitId != null || expense.category != "fuel" || expense.fuelId != null && expense.fuelId != fuel.id) {
                dao.putFuelEntry(fuel.copy(active = false)); audit("fuel_link_normalized", fuel.id, fuel)
            } else {
                if (expense.fuelId == null) dao.putExpense(expense.copy(fuelId = fuel.id))
                val synced = fuel.copy(date = expense.date, totalCents = expense.cents)
                if (synced != fuel) { dao.putFuelEntry(synced); audit("fuel_link_normalized", fuel.id, fuel, synced) }
            }
        }
    }

    fun validateDeadline(value: Deadline) {
        id(value.id); require(value.name.isNotBlank()); LocalDate.parse(value.dueDate)
        require(value.leadDays in 0..3650); require(value.repeatMonths == null || value.repeatMonths > 0)
    }
    suspend fun saveDeadline(value: Deadline) = repo.db.withTransaction {
        validateDeadline(value)
        val old = dao.allDeadlines().firstOrNull { it.id == value.id }
        if (old == value) return@withTransaction
        dao.putDeadline(value); audit("deadline_saved", value.id, old, value)
        val cycleChanged = old == null || old.dueDate != value.dueDate || old.enabled != value.enabled || old.active != value.active
        changed(if (cycleChanged) "deadline:${value.id}" else null)
    }
    suspend fun renewDeadline(id: String, newDate: String) = repo.db.withTransaction {
        LocalDate.parse(newDate)
        val old = dao.allDeadlines().firstOrNull { it.id == id && it.active } ?: error("Documento no encontrado")
        if (old.dueDate == newDate) return@withTransaction
        dao.putDeadlineRenewal(DeadlineRenewal(deadlineId = id, previousDueDate = old.dueDate, newDueDate = newDate, renewedAt = Instant.now(clock).toString()))
        dao.putDeadline(old.copy(dueDate = newDate)); audit("deadline_renewed", id, old, old.copy(dueDate = newDate)); changed("deadline:$id")
    }

    fun validateTyre(value: TyreEvent) {
        id(value.id); require(value.axis in setOf("front", "rear")); require(value.kind in setOf("mount", "review", "change"))
        value.date?.let { LocalDate.parse(it) }; require(value.km >= 0 && value.intervalKm > 0 && value.sequence >= 0)
        Math.addExact(value.km, value.intervalKm)
    }
    suspend fun saveTyreEvents(events: List<TyreEvent>, paidCents: Long?) = repo.db.withTransaction {
        require(events.size in 1..2 && events.map { it.axis }.distinct().size == events.size && events.map { it.id }.distinct().size == events.size)
        events.forEach(::validateTyre); require(events.all { it.active }) { "Usa la anulación explícita" }
        require(paidCents == null || paidCents >= 0)
        require(events.map { it.kind }.distinct().size == 1 && events.map { it.km }.distinct().size == 1 && events.map { it.date }.distinct().size == 1) { "La operación conjunta necesita tipo, fecha y kilómetros comunes" }
        val all = dao.allTyreEvents()
        val before = Tyres.evaluate(all, repo.currentKm()).associate { it.axis to it.generation }
        val old = events.mapNotNull { item -> all.firstOrNull { it.id == item.id } }
        require(old.isEmpty() || old.size == events.size) { "Corrige una operación existente o registra una nueva" }
        old.forEach { previous -> val item = events.single { it.id == previous.id }
            require(previous.active && previous.axis == item.axis && previous.kind == item.kind) { "Conserva el eje y tipo de la operación corregida" }
        }
        val linked = old.mapNotNull { it.visitId }.distinct()
        require(linked.size <= 1)
        val existingVisitId = linked.firstOrNull()
        require(old.map { it.tracking }.distinct().size <= 1) { "Corrige por separado el seguimiento actual y el historial incorporado" }
        val oldVisit = existingVisitId?.let { id -> dao.allVisits().firstOrNull { it.id == id } }
        val historicalConflict = old.isNotEmpty() && old.all { !it.tracking } && existingVisitId != null &&
            (oldVisit == null || !oldVisit.active || oldVisit.status != "done" || old.any { it.date.orEmpty() != oldVisit.date || it.km != oldVisit.km }
                || all.any { it.visitId == existingVisitId && it.active && it.tracking }
                || dao.visitLines(existingVisitId).any { it.ruleId != null })
        if (existingVisitId != null) require(all.filter { it.visitId == existingVisitId && it.active && it.tracking == old.first().tracking }.map { it.id }.toSet() == events.map { it.id }.toSet()) { "Corrige la operación conjunta de ambos ejes" }
        val kind = events.first().kind
        require(kind != "mount" || paidCents == null) { "El montaje inicial es una referencia; registra un cambio para añadir gasto" }
        val visitId = if (kind == "mount") null else existingVisitId ?: UUID.randomUUID().toString()
        val saved = events.map { item ->
            val previous = old.firstOrNull { it.id == item.id }
            item.copy(visitId = visitId, tracking = previous?.tracking ?: true,
                sequence = previous?.sequence ?: Math.addExact(all.filter { it.axis == item.axis }.maxOfOrNull { it.sequence } ?: 0L, 1L))
        }
        saved.filter { it.kind == "review" }.forEach { review ->
            if (review.tracking) {
                val mount = all.filter { it.axis == review.axis && it.active && it.tracking && it.kind in setOf("mount", "change") && it.sequence < review.sequence }.maxByOrNull { it.sequence }
                require(mount != null) { "Registra primero los kilómetros de montaje del eje" }
                require(review.km >= mount.km) { "La revisión no puede preceder al montaje de ese juego" }
            }
        }
        if (old.toSet() == saved.toSet() && (paidCents == null || paidCents == oldVisit?.paidCents)) return@withTransaction
        if (historicalConflict) {
            require(paidCents == null) { "El trabajo local se conserva; corrige aquí únicamente el historial incorporado de ruedas" }
            saved.forEach { dao.putTyreEvent(it); audit("historical_tyre_corrected", it.id, old.first { previous -> previous.id == it.id }, it) }
            return@withTransaction
        }
        if (existingVisitId != null) saved.forEach { dao.putTyreEvent(it) }
        if (visitId != null) {
            val previousVisit = dao.allVisits().firstOrNull { it.id == visitId }
            val date = saved.first().date.orEmpty()
            val payment = paidCents ?: previousVisit?.paidCents
            require(payment == null || date.isNotBlank() || dao.allExpenses().any { it.active && it.visitId == visitId }) { "Indica fecha real para el gasto nuevo" }
            val axes = if (saved.size == 2) "ambos ejes" else if (saved.first().axis == "front") "ruedas delanteras" else "ruedas traseras"
            val title = "${if (kind == "review") "Revisión de" else "Cambio de"} $axes"
            val visit = (previousVisit ?: Visit(id = visitId)).copy(title = title, date = date, dateExact = date.isNotBlank(), km = saved.first().km, kmExact = true,
                notes = saved.joinToString("\n") { it.notes }.trim(), manual = true, status = "done", paidCents = payment)
            repo.saveVisit(visit, saved.map { WorkLine("tyre:${it.id}", visitId, if (it.axis == "front") "Ruedas delanteras" else "Ruedas traseras", parts = it.notes) }, emptyList())
        }
        saved.forEach { dao.putTyreEvent(it) }
        saved.forEach { audit("tyre_saved", it.id, old.firstOrNull { item -> item.id == it.id }, it) }
        val after = Tyres.evaluate(dao.allTyreEvents(), repo.currentKm()).associate { it.axis to it.generation }
        saved.map { it.axis }.distinct().forEach { axis -> if (before[axis] != after[axis]) changed("tyres:$axis") }
    }
    suspend fun voidTyreEvent(id: String) = repo.db.withTransaction {
        val item = dao.allTyreEvents().firstOrNull { it.id == id } ?: error("Registro de ruedas no encontrado")
        if (!item.active) return@withTransaction
        if (!item.tracking) {
            dao.putTyreEvent(item.copy(active = false)); audit("historical_tyre_voided", id, item)
            return@withTransaction
        }
        item.visitId?.let { visitId ->
            require(dao.allTyreEvents().count { it.visitId == visitId && it.active } <= 1) { "Esta operación incluye ambos ejes; anula el trabajo conjunto" }
            repo.voidVisit(visitId)
        } ?: run { dao.putTyreEvent(item.copy(active = false)); audit("tyre_voided", id, item); changed("tyres:${item.axis}") }
    }
    suspend fun visitVoided(visitId: String) {
        dao.allTyreEvents().filter { it.visitId == visitId && it.active }.forEach {
            dao.putTyreEvent(it.copy(active = false)); audit("tyre_voided_with_visit", it.id, it); changed("tyres:${it.axis}")
        }
    }
    private fun id(value: String) { require(value.isNotBlank() && value.length <= 200 && !value.contains('\u0000')) }
}
