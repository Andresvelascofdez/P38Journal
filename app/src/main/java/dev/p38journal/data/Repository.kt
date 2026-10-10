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
    val schemaVersion: Int = 3, val vehicle: List<VehicleRecord> = emptyList(),
    val rules: List<RuleRecord> = emptyList(), val revisions: List<RuleRevisionRecord> = emptyList(),
    val visits: List<Visit> = emptyList(), val lines: List<WorkLine> = emptyList(),
    val events: List<EventRecord> = emptyList(), val expenses: List<Expense> = emptyList(),
    val readings: List<Reading> = emptyList(), val notes: List<Note> = emptyList(),
    val documents: List<Document> = emptyList(), val inbox: List<Inbox> = emptyList(),
    val audit: List<Audit> = emptyList(), val planning: List<PlanningRecord> = emptyList()
)

class Repository(
    context: Context, database: JournalDatabase? = null,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Nicosia"))
) {
    private val context = context.applicationContext
    val db: JournalDatabase by lazy { database ?: getDatabase(this.context) }
    val dao get() = db.dao()
    private val gson = Gson()
    @Volatile private var cataloguePolicy: Map<String, Rule> = emptyMap()
    private fun <T> observe(query: () -> Flow<T>): Flow<T> = flow { withContext(Dispatchers.IO) { db }; emitAll(query()) }
    val observeVisits = observe { dao.observeVisits() }
    val observeExpenses = observe { dao.observeExpenses() }
    val observeReadings = observe { dao.observeReadings() }
    val observeNotes = observe { dao.observeNotes() }
    val observeDocuments = observe { dao.observeDocuments() }
    val observeInbox = observe { dao.observeInbox() }
    fun observeEvents(): Flow<List<Event>> = observe { dao.observeEvents() }.map { rows -> rows.map { it.toEvent() } }
    fun observeRules(): Flow<List<Rule>> = observe { dao.observeRules() }.map { rows -> rows.map { decodeRule(it.json) } }
    fun observePlanningReference(): Flow<PlanningReference?> = observe { dao.observePlanningReference() }.map { it?.toDomain() }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val catalogue = context.assets.open("maintenance-catalogue.json").bufferedReader().use { it.readText() }
        val rules = JsonParser.parseString(catalogue).asJsonObject.getAsJsonArray("rules").map { gson.fromJson(it, Rule::class.java) }
        cataloguePolicy = rules.associateBy { it.id }
        val vehicleJson = context.assets.open("vehicle.json").bufferedReader().use { it.readText() }
        val vehicle = JsonParser.parseString(vehicleJson).asJsonObject
        db.withTransaction {
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
    suspend fun allRules(): List<Rule> = dao.allRules().map { decodeRule(it.json) }
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

    suspend fun saveVisit(visit: Visit, lines: List<WorkLine>, ruleIds: List<String>, existingExpenseId: String? = null, updateCurrentReading: Reading? = null) {
        validateVisit(visit)
        require(lines.all { it.visitId == visit.id && it.title.isNotBlank() && (it.costCents == null || it.costCents >= 0) }) { "Líneas de trabajo no válidas" }
        require(lines.map { it.id }.distinct().size == lines.size)
        require(lines.all { it.ruleId == null || it.ruleId in ruleIds }) { "Cada operación debe vincularse explícitamente" }
        require(ruleIds.all { id -> lines.any { it.ruleId == id } }) { "Cada operación realizada necesita una línea de trabajo explícita" }
        if (visit.paidCents != null && lines.isNotEmpty() && lines.all { it.costCents != null }) {
            require(lines.fold(0L) { total, line -> Math.addExact(total, line.costCents!!) } == visit.paidCents) { "El reparto debe sumar el importe pagado" }
        }
        db.withTransaction {
            val rules = allRules().associateBy { it.id }
            require(ruleIds.all { it in rules }) { "Operación desconocida" }
            val previous = dao.allVisits().find { it.id == visit.id }
            val previousLines = dao.visitLines(visit.id)
            val oldEvents = dao.allEvents().filter { it.visitId == visit.id && it.active }
            val affectedRules = (oldEvents.map { it.ruleId } + ruleIds).distinct()
            val previousGenerations = scheduleGenerations(affectedRules)
            val currentPaymentId = dao.allExpenses().find { it.visitId == visit.id }?.id
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
            if (visit.active) ruleIds.distinct().forEach { ruleId ->
                val rule = rules.getValue(ruleId)
                val line = lines.firstOrNull { it.ruleId == ruleId }
                dao.putEvent(EventRecord("${visit.id}:$ruleId:$revision", visit.id, ruleId, visit.date, visit.km,
                    visit.dateExact, visit.kmExact, true, rule.revision, line?.id, "owner_recorded", visit.operationalAnchor))
            }
            val expenses = dao.allExpenses()
            val linked = expenses.find { it.visitId == visit.id }
            val selected = existingExpenseId?.let { id -> expenses.find { it.id == id } ?: error("Gasto elegido no encontrado") }
            require(selected == null || selected.visitId == null || selected.visitId == visit.id) { "El gasto ya pertenece a otra visita" }
            if (selected != null && linked != null && selected.id != linked.id) dao.putExpense(linked.copy(active = false, visitId = null))
            val payment = selected ?: linked
            if (visit.paidCents != null) {
                val expense = Expense(payment?.id ?: "${visit.id}:payment", visit.date, visit.paidCents,
                    visit.title, "workshop", payment?.notes ?: "", visit.id, visit.active)
                dao.putExpense(expense)
            } else if (payment != null) dao.putExpense(payment.copy(active = false))
            val nextGenerations = scheduleGenerations(affectedRules)
            affectedRules.filter { previousGenerations[it] != nextGenerations[it] }.forEach { invalidate(it) }
            audit(if (previous == null) "visit_created" else "visit_edited", visit.id, gson.toJson(previous) + " -> " + gson.toJson(visit))
            if (updateCurrentReading != null) saveReading(updateCurrentReading)
        }
    }

    suspend fun voidVisit(id: String) = db.withTransaction {
        val visit = dao.allVisits().find { it.id == id } ?: error("Visita no encontrada")
        if (!visit.active) return@withTransaction
        val affectedRules = dao.allEvents().filter { it.visitId == id && it.active }.map { it.ruleId }.distinct()
        val previousGenerations = scheduleGenerations(affectedRules)
        dao.putVisit(visit.copy(active = false))
        dao.allEvents().filter { it.visitId == id && it.active }.forEach { dao.putEvent(it.copy(active = false)) }
        val nextGenerations = scheduleGenerations(affectedRules)
        affectedRules.filter { previousGenerations[it] != nextGenerations[it] }.forEach { invalidate(it) }
        dao.allExpenses().filter { it.visitId == id }.forEach { dao.putExpense(it.copy(active = false)) }
        audit("visit_voided", id, gson.toJson(visit))
    }

    suspend fun saveExpense(expense: Expense) = db.withTransaction {
        validateExpense(expense)
        val existing = dao.allExpenses()
        require(expense.visitId == null || existing.none { it.visitId == expense.visitId && it.id != expense.id }) { "La visita ya tiene un gasto; edita o vincula el existente" }
        expense.visitId?.let { id -> require(dao.allVisits().any { it.id == id && it.active }) { "Visita no válida" } }
        val previous = existing.find { it.id == expense.id }
        if (previous == expense) return@withTransaction
        previous?.visitId?.takeIf { it != expense.visitId }?.let { previousVisitId ->
            dao.allVisits().firstOrNull { it.id == previousVisitId }?.let { dao.putVisit(it.copy(paidCents = null)) }
        }
        dao.putExpense(expense)
        expense.visitId?.let { id -> val visit = dao.allVisits().first { it.id == id }; dao.putVisit(visit.copy(paidCents = if (expense.active) expense.cents else null)) }
        audit(if (previous == null) "expense_created" else "expense_edited", expense.id, gson.toJson(previous) + " -> " + gson.toJson(expense))
    }
    suspend fun voidExpense(id: String) = db.withTransaction {
        val expense = dao.allExpenses().find { it.id == id } ?: error("Gasto no encontrado")
        if (expense.active) {
            dao.putExpense(expense.copy(active = false))
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
    suspend fun saveRule(rule: Rule) = db.withTransaction {
        validateRule(rule)
        val highest = dao.allRevisions().filter { it.ruleId == rule.id }.maxOfOrNull { it.revision } ?: 0
        val revised = rule.copy(revision = maxOf(rule.revision, highest + 1))
        dao.putRule(RuleRecord(revised.id, gson.toJson(revised), revised.revision, true))
        dao.putRevision(RuleRevisionRecord("${revised.id}:${revised.revision}:owner", revised.id, revised.revision, gson.toJson(revised)))
        invalidate(rule.id); audit("rule_override", rule.id, gson.toJson(revised))
    }
    suspend fun resetRule(id: String) {
        val original = withContext(Dispatchers.IO) {
            val catalogue = context.assets.open("maintenance-catalogue.json").bufferedReader().use { it.readText() }
            JsonParser.parseString(catalogue).asJsonObject.getAsJsonArray("rules")
                .map { gson.fromJson(it, Rule::class.java) }.firstOrNull { it.id == id } ?: error("Regla original no disponible")
        }
        db.withTransaction {
            dao.putRule(RuleRecord(original.id, gson.toJson(original), original.revision, false))
            invalidate(id); audit("rule_reset_to_catalogue", id, gson.toJson(original))
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
    private suspend fun scheduleGenerations(ruleIds: List<String>): Map<String, String> {
        if (ruleIds.isEmpty()) return emptyMap()
        val plan = planningReference()
        return Engine.evaluate(allRules().filter { it.id in ruleIds }, allEvents(), LocalDate.now(clock), currentKm() ?: plan.baselineKm, plan = plan)
            .associate { it.rule.id to it.generation }
    }
    private suspend fun audit(action: String, id: String, detail: String) = dao.putAudit(Audit(at = Instant.now(clock).toString(), action = action, recordId = id, detail = detail))

    suspend fun allRecords(): Snapshot = db.withTransaction {
        planningReference()
        Snapshot(vehicle = dao.allVehicles(), rules = dao.allRules(), revisions = dao.allRevisions(), visits = dao.allVisits(),
            lines = dao.allLines(), events = dao.allEvents(), expenses = dao.allExpenses(), readings = dao.allReadings(),
            notes = dao.allNotes(), documents = dao.allDocuments(), inbox = dao.allInbox(), audit = dao.allAudit(), planning = dao.allPlanningReferences())
    }
    suspend fun exportSnapshot(): String = gson.toJson(allRecords())
    fun validateSnapshot(json: String) { parseSnapshot(json) }
    private fun parseSnapshot(json: String): Snapshot {
        require(json.toByteArray().size <= 20 * 1024 * 1024) { "Copia demasiado grande" }
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("schemaVersion")?.asInt
        require(version in listOf(2, 3)) { "Versión de copia no compatible" }
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
        (listOf("vehicle", "rules", "revisions", "visits", "lines", "events", "expenses", "readings", "notes", "documents", "inbox", "audit") + "planning").forEach {
            require(root.has(it) && root.get(it).isJsonArray && root.getAsJsonArray(it).size() <= 100_000) { "Tabla de copia no válida: $it" }
            val ids = root.getAsJsonArray(it).map { row -> row.asJsonObject.get("id").asString.also(::validId) }
            require(ids.distinct().size == ids.size) { "Identificadores duplicados en $it" }
        }
        val snapshot = gson.fromJson(root, Snapshot::class.java)
        if (version == 3) require(snapshot.planning.size == 1) { "La copia no incluye la referencia completa del plan" }
        val visits = snapshot.visits.associateBy { it.id }
        val rules = snapshot.rules.associateBy { it.id }
        snapshot.visits.forEach(::validateVisit)
        snapshot.rules.forEach { record -> val rule = decodeRule(record.json); validateRule(rule); require(rule.id == record.id && rule.revision == record.revision) }
        snapshot.expenses.forEach { validateExpense(it); require(it.visitId == null || it.visitId in visits) }
        require(snapshot.expenses.mapNotNull { it.visitId }.distinct().size == snapshot.expenses.count { it.visitId != null })
        snapshot.lines.forEach { require(it.visitId in visits && it.title.isNotBlank() && (it.costCents == null || it.costCents >= 0)); require(it.ruleId == null || it.ruleId in rules) }
        snapshot.events.forEach { require(it.visitId in visits && it.ruleId in rules); if (it.date != null) date(it.date); require(it.km == null || it.km >= 0); require(it.workLineId == null || snapshot.lines.any { line -> line.id == it.workLineId && line.visitId == it.visitId } || !it.active) }
        snapshot.readings.forEach { date(it.date); Instant.parse(it.recordedAt); require(it.km >= 0) }
        snapshot.notes.forEach { require(it.title.isNotBlank() && it.kind in listOf("fault", "part", "history", "appointment")); if(it.date.isNotBlank()) date(it.date) }
        snapshot.documents.forEach(::validateDocument)
        snapshot.revisions.forEach { require(it.ruleId in rules); validateRule(decodeRule(it.json)) }
        snapshot.inbox.forEach { require(it.ruleId in rules || it.ruleId in listOf("summary", "odometer")); Instant.parse(it.createdAt) }
        snapshot.audit.forEach { Instant.parse(it.at); require(it.action.isNotBlank()) }
        snapshot.planning.forEach { require(it.id == "main" && it.baselineKm >= 0 && it.policyVersion == 3 && it.provenance.isNotBlank()); date(it.baselineDate) }
        return snapshot
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
            val existingPaymentVisits = current.expenses.mapNotNull { it.visitId }.toSet()
            merge(incoming.expenses.filter { it.visitId == null || it.visitId !in existingPaymentVisits && it.visitId !in existingVisitIds }, current.expenses, { it.id }, dao::putExpense)
            merge(incoming.readings, current.readings, { it.id }, dao::putReading)
            merge(incoming.notes, current.notes, { it.id }, dao::putNote)
            merge(incoming.documents, current.documents, { it.id }, dao::putDocument)
            merge(incoming.inbox.map { it.copy(state = if (it.origin == "automatic" && it.policyVersion < 3 && legacyNotificationKind(it.id, it.ruleId, it.generation) == "INITIAL") "cancelled" else if (it.state == "read") "read" else "pending") }, current.inbox, { it.id }, dao::putInbox)
            merge(incoming.audit, current.audit, { it.id }, dao::putAudit)
            dao.clearLedger()
            audit("backup_restored", "snapshot", "Restauración validada; se conservan registros y personalizaciones locales. Personalizaciones copiadas sustituyen reglas estándar; revisiones estándar solo se actualizan")
        }
    }

    private fun validateVisit(value: Visit) { validId(value.id); date(value.date); require(value.title.isNotBlank()); require(value.km == null || value.km >= 0); require(value.paidCents == null || value.paidCents >= 0) }
    private fun validateExpense(value: Expense) { validId(value.id); date(value.date); require(value.cents >= 0 && value.concept.isNotBlank()); require(value.category in listOf("workshop", "extras", "fuel", "other")) }
    private fun validateDocument(value: Document) { validId(value.id); require(value.name.isNotBlank() && value.mime.isNotBlank() && value.size >= 0); require(value.sha256.matches(Regex("[0-9a-fA-F]{64}"))); require(value.path.isNotBlank() && !value.path.contains("..") && !value.path.startsWith("/") && !value.path.contains(":")) { "Ruta de documento no válida" } }
    private fun validateRule(value: Rule) {
        validId(value.id); require(value.title.isNotBlank() && value.source.isNotBlank() && value.sourceUrl.isNotBlank())
        require(value.days == null || value.days > 0); require(value.months == null || value.months > 0)
        require(value.km == null || value.km > 0); require(value.revision > 0)
        require(value.firstKm == null || value.firstKm >= 0); require(value.firstMonths == null || value.firstMonths > 0)
        require(value.scheduleVisibility in listOf("maintenance", "service", "manual", "conditional", "inactive"))
    }
    private fun decodeRule(json: String): Rule {
        val objectJson = JsonParser.parseString(json).asJsonObject
        val policy = cataloguePolicy[objectJson.get("id")?.asString]
        if (!objectJson.has("scheduleVisibility") || objectJson.get("scheduleVisibility").isJsonNull) objectJson.addProperty("scheduleVisibility", policy?.scheduleVisibility ?: "maintenance")
        if (!objectJson.has("serviceGroup") || objectJson.get("serviceGroup").isJsonNull) objectJson.addProperty("serviceGroup", policy?.serviceGroup ?: "")
        if (!objectJson.has("firstKm") && policy?.firstKm != null) objectJson.addProperty("firstKm", policy.firstKm)
        if (!objectJson.has("firstMonths") && policy?.firstMonths != null) objectJson.addProperty("firstMonths", policy.firstMonths)
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
                    .addMigrations(JournalDatabase.MIGRATION_1_2, JournalDatabase.MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
