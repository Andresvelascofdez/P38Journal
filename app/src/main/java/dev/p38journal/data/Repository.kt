package dev.p38journal.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.p38journal.domain.Engine
import dev.p38journal.domain.Event
import dev.p38journal.domain.Rule
import dev.p38journal.domain.Task
import dev.p38journal.domain.PlanningReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Snapshot(
    val schemaVersion: Int = 4, val vehicle: List<VehicleRecord> = emptyList(),
    val rules: List<RuleRecord> = emptyList(), val revisions: List<RuleRevisionRecord> = emptyList(),
    val visits: List<Visit> = emptyList(), val lines: List<WorkLine> = emptyList(),
    val events: List<EventRecord> = emptyList(), val expenses: List<Expense> = emptyList(),
    val readings: List<Reading> = emptyList(), val notes: List<Note> = emptyList(),
    val documents: List<Document> = emptyList(), val inbox: List<Inbox> = emptyList(),
    val audit: List<Audit> = emptyList(), val planning: List<PlanningRecord> = emptyList(),
    val fuelEntries: List<FuelEntry> = emptyList(), val deadlines: List<Deadline> = emptyList(),
    val deadlineRenewals: List<DeadlineRenewal> = emptyList(), val tyreEvents: List<TyreEvent> = emptyList()
)

class Repository(
    context: Context, database: JournalDatabase? = null,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Nicosia"))
) {
    private val context = context.applicationContext
    val db: JournalDatabase by lazy { database ?: getDatabase(this.context) }
    val dao get() = db.dao()
    private val gson = Gson()
    private val expanded by lazy { ExpandedRecords(this, clock) }
    @Volatile private var cataloguePolicy: Map<String, Rule> = emptyMap()
    private fun <T> observe(query: () -> Flow<T>): Flow<T> = flow { withContext(Dispatchers.IO) { db }; emitAll(query()) }
    val observeVisits = observe { dao.observeVisits() }
    val observeWorks = observe { dao.observeWorks() }
    val observeFuelEntries = observe { dao.observeFuelEntries() }
    val observeDeadlines = observe { dao.observeDeadlines() }
    val observeDeadlineRenewals = observe { dao.observeDeadlineRenewals() }
    val observeTyreEvents = observe { dao.observeTyreEvents() }
    val observeExpenses = observe { dao.observeExpenses() }
    val observeReadings = observe { dao.observeReadings() }
    val observeNotes = observe { dao.observeNotes() }
    val observeDocuments = observe { dao.observeDocuments() }
    val observeInbox = observe { dao.observeInbox() }
    fun observeEvents(): Flow<List<Event>> = observe { dao.observeEvents() }.map { rows -> rows.map { it.toEvent() } }
    fun observeRules(): Flow<List<Rule>> = observe { dao.observeRules() }.map { rows -> rows.map { decodeRule(it.json, it.ownerOverride) } }
    fun observePlanningReference(): Flow<PlanningReference?> = observe { dao.observePlanningReference() }.map { it?.toDomain() }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val catalogue = context.assets.open("maintenance-catalogue.json").bufferedReader().use { it.readText() }
        val rules = JsonParser.parseString(catalogue).asJsonObject.getAsJsonArray("rules").map { decodeRule(it.toString()) }
        cataloguePolicy = rules.associateBy { it.id }
        val vehicleJson = context.assets.open("vehicle.json").bufferedReader().use { it.readText() }
        val vehicle = JsonParser.parseString(vehicleJson).asJsonObject
        db.withTransaction {
            normalizeExpenseLinks()
            if (dao.allPlanningReferences().isEmpty()) {
                val recovered = dao.allAudit().filter { it.action in listOf("plan_created", "planning_reference_created") }.minByOrNull { it.at }
                val baselineDate = recovered?.at?.let { runCatching { Instant.parse(it).atZone(clock.zone).toLocalDate().toString() }.getOrNull() } ?: LocalDate.now(clock).toString()
                dao.putPlanningReference(PlanningRecord(baselineDate = baselineDate))
                audit("planning_reference_created", "main", "Referencia operativa; no acredita servicios ni lectura real")
            }
            val existing = dao.allRules().associateBy { it.id }
            rules.forEach { rule ->
                validateRule(rule)
                val json = gson.toJson(rule)
                val record = existing[rule.id]
                if (record == null || (!record.ownerOverride && record.revision < rule.revision)) dao.putRule(RuleRecord(rule.id, json, rule.revision))
                if (dao.allRevisions().none { it.ruleId == rule.id && it.revision == rule.revision }) dao.putRevision(RuleRevisionRecord("${rule.id}:${rule.revision}", rule.id, rule.revision, json))
            }
            val vehicleId = vehicle.getAsJsonObject("vehicle").get("id").asString
            if (dao.allVehicles().none { it.id == vehicleId }) dao.putVehicle(VehicleRecord(vehicleId, vehicleJson))
            val existingNotes = (dao.allNotes().map { it.id } + dao.allAudit().filter { it.action == "note_deleted" }.map { it.recordId }).toSet()
            vehicle.getAsJsonArray("prepurchase_history").forEach { entry ->
                val obj = entry.asJsonObject
                val id = "seed-history:${obj.get("id").asString}"
                if (id !in existingNotes) dao.putNote(Note(id, "history", obj.get("title").asString, entry.toString(), "historical", ""))
            }
            vehicle.getAsJsonArray("historical_notes").forEach { entry ->
                val obj = entry.asJsonObject
                val id = "seed-note:${obj.get("id").asString}"
                if (id !in existingNotes) dao.putNote(Note(id, "history", obj.get("description").asString, entry.toString(), "historical", ""))
            }
        }
    }

    suspend fun tasks(today: LocalDate = LocalDate.now(clock), leadDays: Int = 30, leadKm: Int = 1000): List<Task> = db.withTransaction {
        val plan = planningReference()
        Engine.evaluate(allRules(), allEvents(), today, currentKm() ?: plan.baselineKm, leadDays, leadKm, plan)
    }
    suspend fun currentKm(): Int? = dao.allReadings().firstOrNull()?.km
    suspend fun allEvents(): List<Event> = dao.allEvents().map { it.toEvent() }
    suspend fun allRules(): List<Rule> = dao.allRules().map { decodeRule(it.json, it.ownerOverride) }
    suspend fun planningReference(): PlanningReference {
        return dao.allPlanningReferences().firstOrNull { it.id == "main" }?.toDomain()
            ?: PlanningReference(baselineDate = LocalDate.now(clock).toString()).also {
                db.withTransaction { if (dao.allPlanningReferences().isEmpty()) dao.putPlanningReference(PlanningRecord(baselineDate = it.baselineDate)) }
            }
    }
    suspend fun updatePlanningReference(date: String, km: Int = 175000) = db.withTransaction {
        LocalDate.parse(date); require(km >= 0)
        val previous = planningReference()
        if (previous.baselineDate == date && previous.baselineKm == km) return@withTransaction
        dao.putPlanningReference(PlanningRecord(baselineKm = km, baselineDate = date))
        allRules().forEach { invalidate(it.id) }
        audit("planning_reference_edited", "main", gson.toJson(previous) + " -> " + gson.toJson(PlanningReference(baselineKm = km, baselineDate = date)))
    }
    suspend fun workLines(visitId: String) = dao.visitLines(visitId)
    suspend fun visitLines(visitId: String) = dao.visitLines(visitId)
    suspend fun totalExpenses(): Long = dao.totalExpenses()
    suspend fun saveManualWork(visit: Visit, existingExpenseId: String? = null) = expanded.saveManualWork(visit, existingExpenseId)
    suspend fun saveFuelEntry(entry: FuelEntry) = expanded.saveFuelEntry(entry)
    suspend fun voidFuelEntry(id: String) = expanded.voidFuelEntry(id)
    suspend fun saveDeadline(value: Deadline) = expanded.saveDeadline(value)
    suspend fun renewDeadline(id: String, newDate: String) = expanded.renewDeadline(id, newDate)
    suspend fun saveTyreEvents(events: List<TyreEvent>, paidCents: Long? = null) = expanded.saveTyreEvents(events, paidCents)
    suspend fun voidTyreEvent(id: String) = expanded.voidTyreEvent(id)

    suspend fun saveVisit(visit: Visit, lines: List<WorkLine>, ruleIds: List<String>, existingExpenseId: String? = null, updateCurrentReading: Reading? = null) {
        validateVisit(visit)
        require(lines.all { it.visitId == visit.id && it.title.isNotBlank() && (it.costCents == null || it.costCents >= 0) }) { "Líneas de trabajo no válidas" }
        require(lines.map { it.id }.distinct().size == lines.size)
        require(lines.all { it.ruleId == null || it.ruleId in ruleIds }) { "Cada operación debe vincularse explícitamente" }
        require(ruleIds.all { id -> lines.any { it.ruleId == id } }) { "Cada operación realizada necesita una línea de trabajo explícita" }
        require(ruleIds.isEmpty() || visit.status == "done" && (visit.date.isNotBlank() && visit.dateExact || visit.km != null && visit.kmExact)) { "La operación necesita una fecha o kilometraje real precisos" }
        db.withTransaction {
            normalizeExpenseLinks()
            val rules = allRules().associateBy { it.id }
            require(ruleIds.all { it in rules }) { "Operación desconocida" }
            val previous = dao.allVisits().find { it.id == visit.id }
            val tyres = dao.allTyreEvents().filter { it.visitId == visit.id && it.active }
            require(tyres.isEmpty() || visit.active && visit.status == "done" && tyres.all { it.km == visit.km && it.date.orEmpty() == visit.date }) { "Corrige fecha o kilómetros desde la operación conjunta de ruedas" }
            val previousLines = dao.visitLines(visit.id)
            if (visit.paidCents != null && lines.isNotEmpty() && lines.all { it.costCents != null }) {
                val total = lines.fold(0L) { sum, line -> Math.addExact(sum, line.costCents!!) }
                val canonical = dao.allExpenses().firstOrNull { it.active && it.visitId == visit.id }
                val allocationUnchanged = previous != null && previousLines.associate { it.id to it.costCents } == lines.associate { it.id to it.costCents }
                require(total == visit.paidCents || allocationUnchanged && canonical?.cents == visit.paidCents) { "El reparto debe sumar el importe pagado" }
            }
            val oldEvents = dao.allEvents().filter { it.visitId == visit.id && it.active }
            val affectedRules = (oldEvents.map { it.ruleId } + ruleIds).distinct()
            val previousGenerations = scheduleGenerations(affectedRules)
            val currentPaymentId = dao.allExpenses().find { it.active && it.visitId == visit.id }?.id
            if (previous == visit && previousLines.toSet() == lines.toSet() && oldEvents.map { it.ruleId }.toSet() == ruleIds.toSet()
                && (existingExpenseId == null || existingExpenseId == currentPaymentId)) {
                if (updateCurrentReading != null) saveReading(updateCurrentReading)
                return@withTransaction
            }
            dao.putVisit(visit)
            dao.deleteVisitLines(visit.id)
            lines.forEach { dao.putLine(it) }
            oldEvents.forEach { dao.putEvent(it.copy(active = false)) }
            val revision = (dao.allEvents().filter { it.visitId == visit.id }.maxOfOrNull { it.id.substringAfterLast(':').toIntOrNull() ?: 0 } ?: 0) + 1
            if (visit.active && visit.status == "done") ruleIds.distinct().forEach { ruleId ->
                val rule = rules.getValue(ruleId)
                val line = lines.firstOrNull { it.ruleId == ruleId }
                dao.putEvent(EventRecord("${visit.id}:$ruleId:$revision", visit.id, ruleId, visit.date.takeIf { it.isNotBlank() }, visit.km,
                    visit.dateExact, visit.kmExact, true, rule.revision, line?.id, "owner_recorded", visit.operationalAnchor))
            }
            val expenses = dao.allExpenses()
            val linked = expenses.find { it.active && it.visitId == visit.id }
            val selected = existingExpenseId?.let { id -> expenses.find { it.id == id } ?: error("Gasto elegido no encontrado") }
            require(selected == null || selected.active) { "El gasto elegido está anulado" }
            require(selected?.fuelId == null) { "El gasto pertenece a un repostaje" }
            require(selected == null || selected.visitId == null || selected.visitId == visit.id) { "El gasto ya pertenece a otra visita" }
            if (selected != null && linked != null && selected.id != linked.id) dao.putExpense(linked.copy(active = false, visitId = null))
            val payment = selected ?: linked
            val paidCents = if (!visit.active) null else if (visit.status == "planned") selected?.cents ?: linked?.cents else visit.paidCents ?: selected?.cents
            if (paidCents != null) {
                require(payment != null || visit.date.isNotBlank()) { "Indica fecha real para crear el gasto, o vincula uno existente" }
                val paymentId = payment?.id ?: "${visit.id}:payment".takeIf { id -> expenses.none { it.id == id } } ?: java.util.UUID.randomUUID().toString()
                val expense = payment?.copy(cents = paidCents, visitId = visit.id, active = true)
                    ?: Expense(paymentId, visit.date, paidCents, visit.title, "workshop", visitId = visit.id)
                dao.putExpense(expense)
            } else if (payment != null) dao.putExpense(payment.copy(active = false, visitId = null))
            if (visit.paidCents != paidCents) dao.putVisit(visit.copy(paidCents = paidCents))
            val nextGenerations = scheduleGenerations(affectedRules)
            affectedRules.filter { previousGenerations[it] != nextGenerations[it] }.forEach { invalidate(it) }
            audit(if (previous == null) "visit_created" else "visit_edited", visit.id, gson.toJson(previous) + " -> " + gson.toJson(visit))
            if (updateCurrentReading != null) saveReading(updateCurrentReading)
        }
    }

    suspend fun voidVisit(id: String) = db.withTransaction {
        normalizeExpenseLinks()
        val visit = dao.allVisits().find { it.id == id } ?: error("Visita no encontrada")
        if (!visit.active) return@withTransaction
        val affectedRules = dao.allEvents().filter { it.visitId == id && it.active }.map { it.ruleId }.distinct()
        val previousGenerations = scheduleGenerations(affectedRules)
        dao.putVisit(visit.copy(active = false, paidCents = null))
        dao.allEvents().filter { it.visitId == id && it.active }.forEach { dao.putEvent(it.copy(active = false)) }
        val nextGenerations = scheduleGenerations(affectedRules)
        affectedRules.filter { previousGenerations[it] != nextGenerations[it] }.forEach { invalidate(it) }
        dao.allExpenses().filter { it.visitId == id }.forEach { dao.putExpense(it.copy(active = false, visitId = null)) }
        expanded.visitVoided(id)
        audit("visit_voided", id, gson.toJson(visit))
    }

    suspend fun saveExpense(expense: Expense) = db.withTransaction {
        validateExpense(expense)
        normalizeExpenseLinks()
        val normalized = if (expense.active) expense else expense.copy(visitId = null, fuelId = null)
        val existing = dao.allExpenses()
        require(normalized.visitId == null || existing.none { it.active && it.visitId == normalized.visitId && it.id != expense.id }) { "La visita ya tiene un gasto; edita o vincula el existente" }
        normalized.visitId?.let { id -> require(dao.allVisits().any { it.id == id && it.active }) { "Trabajo no válido" } }
        val previous = existing.find { it.id == expense.id }
        expanded.validateFuelExpense(expense, previous)
        if (previous == normalized) return@withTransaction
        previous?.visitId?.takeIf { it != normalized.visitId }?.let { previousVisitId ->
            dao.allVisits().firstOrNull { it.id == previousVisitId }?.let { dao.putVisit(it.copy(paidCents = null)) }
        }
        dao.putExpense(normalized)
        expanded.expenseSaved(previous, normalized)
        normalized.visitId?.let { id -> val visit = dao.allVisits().first { it.id == id }; dao.putVisit(visit.copy(paidCents = normalized.cents)) }
        audit(if (previous == null) "expense_created" else "expense_edited", expense.id, gson.toJson(previous) + " -> " + gson.toJson(normalized))
    }
    suspend fun voidExpense(id: String) = db.withTransaction {
        normalizeExpenseLinks()
        val expense = dao.allExpenses().find { it.id == id } ?: error("Gasto no encontrado")
        if (expense.active) {
            val revised = expense.copy(active = false, visitId = null, fuelId = null)
            dao.putExpense(revised)
            expanded.expenseSaved(expense, revised)
            expense.visitId?.let { visitId -> dao.allVisits().find { it.id == visitId }?.let { dao.putVisit(it.copy(paidCents = null)) } }
            audit("expense_voided", id, gson.toJson(expense))
        }
    }

    suspend fun saveReading(reading: Reading, allowCorrection: Boolean = false) = db.withTransaction {
        validId(reading.id); date(reading.date); require(reading.km >= 0)
        val readings = dao.allReadings()
        if (readings.any { it.id == reading.id && it.date == reading.date && it.km == reading.km }) return@withTransaction
        val others = readings.filter { it.id != reading.id }
        val prior = others.filter { it.date <= reading.date }.maxByOrNull { it.date + it.recordedAt }
        val next = others.filter { it.date > reading.date }.minByOrNull { it.date + it.recordedAt }
        val decreasing = (prior != null && prior.km > reading.km) || (next != null && next.km < reading.km)
        require(allowCorrection || !decreasing) { "El kilometraje retrocede: confirma una corrección explícita" }
        dao.putReading(reading.copy(recordedAt = Instant.now(clock).toString()))
        dao.putLedger(Ledger("v3-reconcile", "system", reading.id, "reading", "change", "pending", Instant.now(clock).toString()))
        audit(if (allowCorrection) "odometer_corrected" else "odometer_recorded", reading.id, gson.toJson(reading))
    }
    suspend fun saveNote(note: Note) = db.withTransaction {
        validId(note.id); require(note.title.isNotBlank()); if (note.date.isNotBlank()) date(note.date)
        require(note.kind in listOf("fault", "part", "history", "appointment"))
        dao.putNote(note); audit("note_saved", note.id, gson.toJson(note))
    }
    suspend fun deleteNote(id: String) = db.withTransaction { val old = dao.allNotes().find { it.id == id }; dao.deleteNote(id); audit("note_deleted", id, gson.toJson(old)) }
    suspend fun saveDocument(document: Document) = db.withTransaction { validateDocument(document); dao.putDocument(document); audit("document_saved", document.id, gson.toJson(document)) }
    suspend fun deleteDocument(id: String) = db.withTransaction { val old = dao.allDocuments().find { it.id == id }; dao.deleteDocument(id); audit("document_deleted", id, gson.toJson(old)) }
    suspend fun saveInbox(inbox: Inbox) = dao.putInbox(inbox)
    suspend fun saveRule(rule: Rule) {
        if (originalRule(rule.id) != null) saveRuleOverride(rule) else saveCustomRule(rule)
    }
    suspend fun originalRule(id: String): Rule? = withContext(Dispatchers.IO) {
        if (cataloguePolicy.isEmpty()) {
            val catalogue = context.assets.open("maintenance-catalogue.json").bufferedReader().use { it.readText() }
            cataloguePolicy = JsonParser.parseString(catalogue).asJsonObject.getAsJsonArray("rules")
                .map { decodeRule(it.toString()) }.associateBy { it.id }
        }
        cataloguePolicy[id]
    }
    suspend fun saveCustomRule(rule: Rule) {
        require(originalRule(rule.id) == null) { "La operación pertenece al catálogo del fabricante" }
        persistRule(rule.copy(origin = "custom", manufacturerRevision = null, firstKm = null, firstMonths = null))
    }
    suspend fun saveRuleOverride(rule: Rule) {
        val original = originalRule(rule.id) ?: error("Regla original no disponible")
        db.withTransaction {
            val previous = allRules().firstOrNull { it.id == rule.id } ?: original
            val intervalChanged = previous.days != rule.days || previous.months != rule.months || previous.km != rule.km
            val simple = original.recurrence in listOf("phase", "phase_b") && rule.recurrence != "once" && (intervalChanged || rule.recurrence == "component")
            val clearOffsets = simple || rule.recurrence == "once" && (rule.dueDate != null || rule.dueKm != null)
            val personalized = rule.copy(origin = "personalized", source = original.source, sourceUrl = original.sourceUrl,
                manufacturerRevision = original.revision, recurrence = if (simple) "component" else rule.recurrence,
                firstKm = if (clearOffsets) null else rule.firstKm, firstMonths = if (clearOffsets) null else rule.firstMonths)
            if (dao.allRevisions().none { it.ruleId == original.id && it.revision == original.revision }) {
                dao.putRevision(RuleRevisionRecord("${original.id}:${original.revision}", original.id, original.revision, gson.toJson(original)))
            }
            persistRule(personalized)
        }
    }
    suspend fun setRuleEnabled(id: String, enabled: Boolean) {
        val rule = allRules().firstOrNull { it.id == id } ?: error("Operación no encontrada")
        if (rule.enabled != enabled) saveRule(rule.copy(enabled = enabled))
    }
    private suspend fun persistRule(rule: Rule) = db.withTransaction {
        validateRule(rule)
        val previous = dao.allRules().firstOrNull { it.id == rule.id }?.let { decodeRule(it.json, it.ownerOverride) }
        if (previous != null && previous.copy(revision = rule.revision) == rule) return@withTransaction
        val previousGeneration = scheduleGenerations(listOf(rule.id))[rule.id]
        val highest = dao.allRevisions().filter { it.ruleId == rule.id }.maxOfOrNull { it.revision } ?: 0
        val revised = rule.copy(revision = maxOf(rule.revision, highest + 1))
        dao.putRule(RuleRecord(revised.id, gson.toJson(revised), revised.revision, true))
        dao.putRevision(RuleRevisionRecord("${revised.id}:${revised.revision}:owner", revised.id, revised.revision, gson.toJson(revised)))
        if (previousGeneration != scheduleGenerations(listOf(rule.id))[rule.id]) invalidate(rule.id)
        audit("rule_override", rule.id, gson.toJson(revised))
    }
    suspend fun resetRule(id: String) {
        val original = originalRule(id) ?: error("Regla original no disponible")
        db.withTransaction {
            val previousGeneration = scheduleGenerations(listOf(id))[id]
            dao.putRule(RuleRecord(original.id, gson.toJson(original), original.revision, false))
            if (previousGeneration != scheduleGenerations(listOf(id))[id]) invalidate(id)
            audit("rule_reset_to_catalogue", id, gson.toJson(original))
        }
    }
    suspend fun adoptOperationalAnchor(eventId: String) = db.withTransaction {
        val event = dao.allEvents().firstOrNull { it.id == eventId && it.active } ?: error("Operación activa no encontrada")
        require((event.dateExact && event.date != null) || (event.kmExact && event.km != null)) { "La referencia debe tener fecha o kilometraje precisos" }
        if (event.operationalAnchor) return@withTransaction
        val updated = event.copy(operationalAnchor = true)
        dao.putEvent(updated); invalidate(event.ruleId)
        audit("operational_anchor_adopted", eventId, gson.toJson(event) + " -> " + gson.toJson(updated))
    }
    private suspend fun invalidate(ruleId: String) { dao.cancelInbox(ruleId); dao.cancelLedger(ruleId) }
    private suspend fun normalizeExpenseLinks() {
        val visits = dao.allVisits().associateBy { it.id }
        dao.allExpenses().filter { it.visitId != null && (!it.active || visits[it.visitId]?.active != true) }.forEach { old ->
            dao.putExpense(old.copy(visitId = null, active = false))
            audit("expense_link_normalized", old.id, gson.toJson(old))
        }
        val linked = dao.allExpenses().filter { it.active && it.visitId != null }.associateBy { it.visitId }
        visits.values.forEach { visit ->
            val cents = linked[visit.id]?.cents
            if (visit.paidCents != cents) {
                dao.putVisit(visit.copy(paidCents = cents))
                audit("visit_payment_normalized", visit.id, gson.toJson(visit))
            }
        }
        expanded.normalizeFuelLinks()
    }
    private suspend fun scheduleGenerations(ruleIds: List<String>): Map<String, String> {
        if (ruleIds.isEmpty()) return emptyMap()
        val plan = planningReference()
        return Engine.evaluate(allRules().filter { it.id in ruleIds }, allEvents(), LocalDate.now(clock), currentKm() ?: plan.baselineKm, plan = plan)
            .associate { it.rule.id to it.generation }
    }
    private suspend fun audit(action: String, id: String, detail: String) = dao.putAudit(Audit(at = Instant.now(clock).toString(), action = action, recordId = id, detail = detail))

    suspend fun allRecords(): Snapshot = db.withTransaction {
        normalizeExpenseLinks()
        planningReference()
        Snapshot(vehicle = dao.allVehicles(), rules = dao.allRules(), revisions = dao.allRevisions(), visits = dao.allVisits(),
            lines = dao.allLines(), events = dao.allEvents(), expenses = dao.allExpenses(), readings = dao.allReadings(),
            notes = dao.allNotes(), documents = dao.allDocuments(), inbox = dao.allInbox(), audit = dao.allAudit(), planning = dao.allPlanningReferences(),
            fuelEntries = dao.allFuelEntries(), deadlines = dao.allDeadlines(), deadlineRenewals = dao.allDeadlineRenewals(), tyreEvents = dao.allTyreEvents())
    }
    suspend fun exportSnapshot(): String = gson.toJson(allRecords())
    fun validateSnapshot(json: String) { parseSnapshot(json) }
    private fun parseSnapshot(json: String): Snapshot {
        require(json.toByteArray().size <= 20 * 1024 * 1024) { "Copia demasiado grande" }
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("schemaVersion")?.asInt
        require(version in listOf(2, 3, 4)) { "Versión de copia no compatible" }
        if (version != 4) listOf("fuelEntries", "deadlines", "deadlineRenewals", "tyreEvents").forEach { if (!root.has(it)) root.add(it, com.google.gson.JsonArray()) }
        root.getAsJsonArray("visits")?.forEach { row -> row.asJsonObject.apply {
            if (!has("status") || get("status").isJsonNull) addProperty("status", "done")
            if (!has("manual") || get("manual").isJsonNull) addProperty("manual", false)
        } }
        root.getAsJsonArray("tyreEvents")?.forEach { row -> row.asJsonObject.apply {
            if (!has("tracking") || get("tracking").isJsonNull) addProperty("tracking", true)
            if (!has("active") || get("active").isJsonNull) addProperty("active", true)
            if (!has("notes") || get("notes").isJsonNull) addProperty("notes", "")
        } }
        root.getAsJsonArray("fuelEntries")?.forEach { row -> row.asJsonObject.apply {
            if (!has("active") || get("active").isJsonNull) addProperty("active", true)
            if (!has("full") || get("full").isJsonNull) addProperty("full", false)
        } }
        root.getAsJsonArray("deadlines")?.forEach { row -> row.asJsonObject.apply {
            if (!has("active") || get("active").isJsonNull) addProperty("active", true)
            if (!has("enabled") || get("enabled").isJsonNull) addProperty("enabled", true)
            if (!has("leadDays") || get("leadDays").isJsonNull) addProperty("leadDays", 30)
            if (!has("notes") || get("notes").isJsonNull) addProperty("notes", "")
        } }
        if (version == 2) {
            root.add("planning", com.google.gson.JsonArray())
            root.getAsJsonArray("inbox")?.forEach { row -> row.asJsonObject.apply {
                if (!has("origin")) {
                    val encoded = legacyNotificationKind(get("id")?.asString, get("ruleId")?.asString, get("generation")?.asString) != null
                    addProperty("origin", if (encoded) "automatic" else "legacy_unknown")
                }
                if (!has("policyVersion")) addProperty("policyVersion", 2)
            } }
        }
        (listOf("vehicle", "rules", "revisions", "visits", "lines", "events", "expenses", "readings", "notes", "documents", "inbox", "audit", "planning", "fuelEntries", "deadlines", "deadlineRenewals", "tyreEvents")).forEach {
            require(root.has(it) && root.get(it).isJsonArray && root.getAsJsonArray(it).size() <= 100_000) { "Tabla de copia no válida: $it" }
            val ids = root.getAsJsonArray(it).map { row -> row.asJsonObject.get("id").asString.also(::validId) }
            require(ids.distinct().size == ids.size) { "Identificadores duplicados en $it" }
        }
        val rawSnapshot = gson.fromJson(root, Snapshot::class.java)
        val rawVisits = rawSnapshot.visits.associateBy { it.id }
        val detached = rawSnapshot.expenses.filter { it.visitId != null && (!it.active || rawVisits[it.visitId]?.active == false) }
        val detachedIds = detached.map { it.id }.toSet()
        val expenses = rawSnapshot.expenses.map { if (it.id in detachedIds) it.copy(active = false, visitId = null) else it }
        val payments = expenses.filter { it.active && it.visitId != null }.associateBy { it.visitId }
        val normalizationAudit = detached.map { Audit("backup-normalize:${it.id}", Instant.now(clock).toString(), "expense_link_normalized", it.id, gson.toJson(it)) } +
            rawSnapshot.visits.filter { it.paidCents != payments[it.id]?.cents }.map { Audit("backup-normalize-visit:${it.id}", Instant.now(clock).toString(), "visit_payment_normalized", it.id, gson.toJson(it)) }
        val snapshot = rawSnapshot.copy(expenses = expenses,
            visits = rawSnapshot.visits.map { it.copy(paidCents = payments[it.id]?.cents) },
            audit = rawSnapshot.audit + normalizationAudit.filter { added -> rawSnapshot.audit.none { it.id == added.id } })
        if (version in listOf(3, 4)) require(snapshot.planning.size == 1) { "La copia no incluye la referencia completa del plan" }
        val visits = snapshot.visits.associateBy { it.id }
        val rules = snapshot.rules.associateBy { it.id }
        snapshot.visits.forEach(::validateVisit)
        snapshot.rules.forEach { record -> val rule = decodeRule(record.json, record.ownerOverride); validateRule(rule); require(rule.id == record.id && rule.revision == record.revision) }
        snapshot.expenses.forEach { validateExpense(it); require(it.visitId == null || it.visitId in visits) }
        require(snapshot.expenses.mapNotNull { it.visitId }.distinct().size == snapshot.expenses.count { it.visitId != null })
        snapshot.lines.forEach { require(it.visitId in visits && it.title.isNotBlank() && (it.costCents == null || it.costCents >= 0)); require(it.ruleId == null || it.ruleId in rules) }
        snapshot.events.forEach { require(it.visitId in visits && it.ruleId in rules); if (it.date != null) date(it.date); require(!it.active || visits.getValue(it.visitId).status == "done"); require(it.km == null || it.km >= 0); require(it.workLineId == null || snapshot.lines.any { line -> line.id == it.workLineId && line.visitId == it.visitId } || !it.active) }
        snapshot.readings.forEach { date(it.date); Instant.parse(it.recordedAt); require(it.km >= 0) }
        snapshot.notes.forEach { require(it.title.isNotBlank() && it.kind in listOf("fault", "part", "history", "appointment")); if(it.date.isNotBlank()) date(it.date) }
        snapshot.documents.forEach(::validateDocument)
        snapshot.revisions.forEach { require(it.ruleId in rules); validateRule(decodeRule(it.json)) }
        snapshot.inbox.forEach { require(it.ruleId in rules || it.ruleId in listOf("summary", "odometer", "tyres:front", "tyres:rear") || it.ruleId.startsWith("deadline:") && snapshot.deadlines.any { value -> "deadline:${value.id}" == it.ruleId }); Instant.parse(it.createdAt) }
        snapshot.audit.forEach { Instant.parse(it.at); require(it.action.isNotBlank()) }
        snapshot.planning.forEach { require(it.id == "main" && it.baselineKm >= 0 && it.policyVersion == 3 && it.provenance.isNotBlank()); date(it.baselineDate) }
        validateExpandedSnapshot(snapshot)
        return snapshot
    }
    private fun validateExpandedSnapshot(snapshot: Snapshot) {
        val fuel = snapshot.fuelEntries.associateBy { it.id }
        val expenses = snapshot.expenses.associateBy { it.id }
        val visits = snapshot.visits.associateBy { it.id }
        snapshot.fuelEntries.forEach { entry ->
            expanded.validateFuel(entry)
            require(entry.expenseId == null || entry.expenseId in expenses)
            if (entry.active) {
                val payment = expenses[entry.expenseId] ?: error("El repostaje no tiene gasto")
                require(payment.active && payment.fuelId == entry.id && payment.visitId == null && payment.category == "fuel" && payment.cents == entry.totalCents && payment.date == entry.date)
            }
        }
        require(snapshot.fuelEntries.mapNotNull { it.expenseId }.distinct().size == snapshot.fuelEntries.count { it.expenseId != null })
        snapshot.expenses.forEach { expense -> expense.fuelId?.let { fuelId ->
            require(fuelId in fuel && fuel.getValue(fuelId).expenseId == expense.id)
            require(expense.category == "fuel" && expense.visitId == null && (!expense.active || fuel.getValue(fuelId).active))
        } }
        require(snapshot.expenses.mapNotNull { it.fuelId }.distinct().size == snapshot.expenses.count { it.fuelId != null })
        snapshot.deadlines.forEach(expanded::validateDeadline)
        val deadlineIds = snapshot.deadlines.map { it.id }.toSet()
        snapshot.deadlineRenewals.forEach { renewal ->
            require(renewal.deadlineId in deadlineIds); date(renewal.previousDueDate); date(renewal.newDueDate); Instant.parse(renewal.renewedAt)
        }
        snapshot.tyreEvents.forEach { event ->
            expanded.validateTyre(event); require(event.sequence > 0)
            require(event.visitId == null || event.visitId in visits)
            if (event.active && event.tracking && event.visitId != null) {
                val visit = visits.getValue(event.visitId)
                require(visit.active && visit.status == "done" && visit.km == event.km && visit.date == event.date.orEmpty())
            }
            require(event.kind != "mount" || event.visitId == null)
        }
    }
    suspend fun restoreSnapshot(json: String) {
        val incoming = withContext(Dispatchers.IO) { parseSnapshot(json) }
        db.withTransaction {
            val current = allRecords()
            suspend fun <T> merge(values: List<T>, old: List<T>, id: (T) -> String, insert: suspend (T) -> Unit) {
                val known = old.map(id).toSet(); values.filter { id(it) !in known }.forEach { insert(it) }
            }
            merge(incoming.vehicle, current.vehicle, { it.id }, dao::putVehicle)
            val localPlanEdited = current.audit.any { it.action == "planning_reference_edited" }
            if (!localPlanEdited) incoming.planning.firstOrNull()?.let { dao.putPlanningReference(it) }
            if (!localPlanEdited && incoming.schemaVersion == 2 && incoming.planning.isEmpty()) {
                incoming.audit.filter { it.action in listOf("plan_created", "planning_reference_created") }.minByOrNull { it.at }?.let {
                    val recoveredDate = Instant.parse(it.at).atZone(clock.zone).toLocalDate().toString()
                    dao.putPlanningReference(PlanningRecord(baselineDate = recoveredDate))
                }
            }
            val currentRules = current.rules.associateBy { it.id }
            incoming.rules.forEach { copied ->
                val local = currentRules[copied.id]
                if (local == null || (!local.ownerOverride && copied.ownerOverride)
                    || (!local.ownerOverride && !copied.ownerOverride && copied.revision > local.revision)) {
                    dao.putRule(copied)
                }
            }
            merge(incoming.revisions, current.revisions, { it.id }, dao::putRevision)
            val existingVisitIds = current.visits.map { it.id }.toSet()
            merge(incoming.visits, current.visits, { it.id }, dao::putVisit)
            merge(incoming.lines.filter { it.visitId !in existingVisitIds }, current.lines, { it.id }, dao::putLine)
            merge(incoming.events.filter { it.visitId !in existingVisitIds }, current.events, { it.id }, dao::putEvent)
            merge(incoming.deadlines, current.deadlines, { it.id }, dao::putDeadline)
            merge(incoming.deadlineRenewals, current.deadlineRenewals, { it.id }, dao::putDeadlineRenewal)
            val existingFuelIds = current.fuelEntries.map { it.id }.toSet()
            val existingFuelExpenses = current.fuelEntries.mapNotNull { it.expenseId }.toSet()
            incoming.fuelEntries.filter { it.id !in existingFuelIds }.forEach { entry ->
                if (entry.expenseId != null && entry.expenseId in existingFuelExpenses) {
                    dao.putFuelEntry(entry.copy(expenseId = null, active = false)); audit("backup_fuel_link_detached", entry.id, gson.toJson(entry))
                } else dao.putFuelEntry(entry)
            }
            val existingPaymentVisits = current.expenses.filter { it.active }.mapNotNull { it.visitId }.toSet()
            val targetVisits = dao.allVisits().associateBy { it.id }
            val targetFuel = dao.allFuelEntries().associateBy { it.id }
            val copiedExpenses = incoming.expenses.map { expense ->
                if (expense.visitId != null && (expense.visitId in existingPaymentVisits || targetVisits[expense.visitId]?.active == false)) {
                    if (current.expenses.none { it.id == expense.id }) audit("backup_expense_link_detached", expense.id, gson.toJson(expense))
                    expense.copy(visitId = null, active = expense.active && targetVisits[expense.visitId]?.active != false)
                } else if (expense.fuelId != null && (targetFuel[expense.fuelId]?.active != true || targetFuel[expense.fuelId]?.expenseId != expense.id)) {
                    if (current.expenses.none { it.id == expense.id }) audit("backup_fuel_expense_detached", expense.id, gson.toJson(expense))
                    expense.copy(fuelId = null, active = expense.active && (targetFuel[expense.fuelId]?.active != false || expense.fuelId !in existingFuelIds))
                } else expense
            }
            merge(copiedExpenses, current.expenses, { it.id }, dao::putExpense)
            val targetTyres = dao.allTyreEvents()
            val preservedTyreAxes = current.tyreEvents.filter { it.active && it.tracking }.map { it.axis }.toSet()
            incoming.tyreEvents.filter { item -> targetTyres.none { it.id == item.id } }.forEach { event ->
                val visit = event.visitId?.let(targetVisits::get)
                val coherent = event.visitId == null || visit != null && visit.active && visit.status == "done" && visit.km == event.km && visit.date == event.date.orEmpty()
                val tracking = event.tracking && coherent && event.axis !in preservedTyreAxes
                dao.putTyreEvent(event.copy(tracking = tracking))
                if (event.tracking && !tracking) audit("backup_tyre_tracking_conflict", event.id, gson.toJson(event))
            }
            merge(incoming.readings, current.readings, { it.id }, dao::putReading)
            merge(incoming.notes, current.notes, { it.id }, dao::putNote)
            merge(incoming.documents, current.documents, { it.id }, dao::putDocument)
            merge(incoming.inbox.map { it.copy(state = if (it.origin == "automatic" && it.policyVersion < 3 && legacyNotificationKind(it.id, it.ruleId, it.generation) == "INITIAL") "cancelled" else if (it.state == "read") "read" else "pending") }, current.inbox, { it.id }, dao::putInbox)
            merge(incoming.audit, current.audit, { it.id }, dao::putAudit)
            normalizeExpenseLinks()
            dao.clearLedger()
            audit("backup_restored", "snapshot", "Restauración validada; se conservan registros y personalizaciones locales. Personalizaciones copiadas sustituyen reglas estándar; revisiones estándar solo se actualizan")
        }
    }

    private fun validateVisit(value: Visit) {
        validId(value.id); require(value.status in setOf("planned", "done"))
        if (value.date.isNotBlank()) date(value.date) else require(value.manual && !value.dateExact) { "Una fecha desconocida no debe figurar como precisa" }
        value.targetDate?.let(::date); require(value.estimatedCents == null || value.estimatedCents >= 0)
        require(value.title.isNotBlank()); require(value.km == null || value.km >= 0); require(value.paidCents == null || value.paidCents >= 0)
    }
    private fun validateExpense(value: Expense) { validId(value.id); date(value.date); require(value.cents >= 0 && value.concept.isNotBlank()); require(value.category in listOf("workshop", "extras", "fuel", "other")); require(value.visitId == null || value.fuelId == null) }
    private fun validateDocument(value: Document) { validId(value.id); require(value.name.isNotBlank() && value.mime.isNotBlank() && value.size >= 0); require(value.sha256.matches(Regex("[0-9a-fA-F]{64}"))); require(value.path.isNotBlank() && !value.path.contains("..") && !value.path.startsWith("/") && !value.path.contains(":")) { "Ruta de documento no válida" } }
    private fun validateRule(value: Rule) {
        validId(value.id); require(value.title.isNotBlank())
        require(value.origin in listOf("manufacturer", "personalized", "custom"))
        require(value.origin == "custom" || value.source.isNotBlank() && value.sourceUrl.isNotBlank())
        require(value.days == null || value.days > 0); require(value.months == null || value.months > 0)
        require(value.km == null || value.km > 0); require(value.revision > 0)
        require(value.firstKm == null || value.firstKm >= 0); require(value.firstMonths == null || value.firstMonths > 0)
        require(value.scheduleVisibility in listOf("maintenance", "service", "manual", "conditional", "inactive"))
        require(value.dueKm == null || value.dueKm >= 0); value.dueDate?.let(::date)
        require(value.manufacturerRevision == null || value.manufacturerRevision > 0)
        if (value.origin == "custom") {
            if (value.recurrence == "once") require(value.dueDate != null || value.dueKm != null) { "Indica fecha o kilometraje de la tarea única" }
            else require(value.days != null || value.months != null || value.km != null) { "Indica un intervalo" }
        }
        if (value.origin == "personalized" && value.recurrence == "once" && value.firstKm == null) require(value.dueDate != null || value.dueKm != null)
    }
    private fun decodeRule(json: String, ownerOverride: Boolean = false): Rule {
        val objectJson = JsonParser.parseString(json).asJsonObject
        val policy = cataloguePolicy[objectJson.get("id")?.asString]
        if (!objectJson.has("scheduleVisibility") || objectJson.get("scheduleVisibility").isJsonNull) objectJson.addProperty("scheduleVisibility", policy?.scheduleVisibility ?: "maintenance")
        if (!objectJson.has("serviceGroup") || objectJson.get("serviceGroup").isJsonNull) objectJson.addProperty("serviceGroup", policy?.serviceGroup ?: "")
        if (objectJson.get("recurrence")?.asString in listOf("phase", "phase_b")) {
            if (!objectJson.has("firstKm") && policy?.firstKm != null) objectJson.addProperty("firstKm", policy.firstKm)
            if (!objectJson.has("firstMonths") && policy?.firstMonths != null) objectJson.addProperty("firstMonths", policy.firstMonths)
        }
        if (!objectJson.has("origin") || objectJson.get("origin").isJsonNull) objectJson.addProperty("origin", if (ownerOverride) if (policy != null) "personalized" else "custom" else "manufacturer")
        if (!objectJson.has("notes") || objectJson.get("notes").isJsonNull) objectJson.addProperty("notes", "")
        if (!objectJson.has("enabled") || objectJson.get("enabled").isJsonNull) objectJson.addProperty("enabled", true)
        if (!objectJson.has("source") || objectJson.get("source").isJsonNull) objectJson.addProperty("source", "")
        if (!objectJson.has("sourceUrl") || objectJson.get("sourceUrl").isJsonNull) objectJson.addProperty("sourceUrl", "")
        if (objectJson.get("origin").asString == "custom" && objectJson.get("recurrence")?.asString == "once"
            && (!objectJson.has("dueDate") || objectJson.get("dueDate").isJsonNull)
            && (!objectJson.has("dueKm") || objectJson.get("dueKm").isJsonNull)
            && objectJson.has("firstKm") && !objectJson.get("firstKm").isJsonNull) objectJson.add("dueKm", objectJson.get("firstKm"))
        return gson.fromJson(objectJson, Rule::class.java)
    }
    private fun validId(id: String) { require(id.isNotBlank() && id.length <= 200 && !id.contains('\u0000')) }
    private fun date(value: String) { LocalDate.parse(value) }
    private fun EventRecord.toEvent() = Event(id, visitId, ruleId, date, km, dateExact, kmExact, active, revision, workLineId, provenance, operationalAnchor)

    companion object {
        @Volatile private var instance: JournalDatabase? = null
        private fun getDatabase(context: Context): JournalDatabase = instance ?: synchronized(this) {
            instance ?: run {
                PreMigrationBackup.ensure(context)
                Room.databaseBuilder(context, JournalDatabase::class.java, "p38-journal.db")
                    .addMigrations(JournalDatabase.MIGRATION_1_2, JournalDatabase.MIGRATION_2_3, JournalDatabase.MIGRATION_3_4).build().also { instance = it }
            }
        }
    }
}
