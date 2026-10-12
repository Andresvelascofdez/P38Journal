package dev.p38journal.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.p38journal.data.*
import dev.p38journal.domain.Task
import dev.p38journal.platform.Files
import dev.p38journal.platform.Notifications
import dev.p38journal.platform.Preferences
import dev.p38journal.platform.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    val repository = Repository(application)
    val preferences = Preferences(application)
    val files = Files(application, repository)
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _workLines = MutableStateFlow<List<WorkLine>>(emptyList())
    val workLines = _workLines.asStateFlow()
    val settings = preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val visits = repository.observeVisits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val works = repository.observeWorks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fuelEntries = repository.observeFuelEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deadlines = repository.observeDeadlines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deadlineRenewals = repository.observeDeadlineRenewals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tyreEvents = repository.observeTyreEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses = repository.observeExpenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val readings = repository.observeReadings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = repository.observeNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val documents = repository.observeDocuments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inbox = repository.observeInbox.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val planning = repository.observePlanningReference().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.initialize() }
                _ready.value = true
                Notifications.schedule(application)
                Notifications.refresh(application)
                launch { repository.observeInbox.drop(1).collect { reloadTasks() } }
                launch { repository.observePlanningReference().drop(1).collect { reloadTasks() } }
                combine(repository.observeVisits, repository.observeReadings, preferences.flow, repository.observeEvents(), repository.observeRules()) { _, _, _, _, _ -> Unit }
                    .collect { reloadTasks() }
            } catch (e: Exception) {
                _message.value = readable(e)
            }
        }
    }

    fun today(): String = LocalDate.now(ZoneId.of(settings.value.zone)).toString()
    fun clearMessage() { _message.value = null }
    fun announce(value: String) { _message.value = value }
    fun resume() {
        if (!_ready.value) return
        viewModelScope.launch { runCatching { reloadTasks() }.onFailure { _message.value = readable(it as? Exception ?: Exception(it)) } }
        Notifications.refresh(getApplication())
    }
    suspend fun reloadTasks() {
        val config = preferences.flow.first()
        _tasks.value = withContext(Dispatchers.IO) {
            repository.tasks(LocalDate.now(ZoneId.of(config.zone)), config.leadDays, config.leadKm)
        }
        _workLines.value = withContext(Dispatchers.IO) { repository.dao.allLines() }
    }

    fun action(success: String = "Guardado", after: () -> Unit = {}, evaluateTrigger: String? = null, work: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { work() }
                reloadTasks()
                if (evaluateTrigger != null) {
                    try { withContext(Dispatchers.IO) { Notifications.evaluateNow(getApplication(), evaluateTrigger) } }
                    catch (_: Exception) { Notifications.refresh(getApplication()) }
                }
                Notifications.refresh(getApplication())
                _message.value = success
                after()
            } catch (e: Exception) {
                _message.value = readable(e)
            } finally { _busy.value = false }
        }
    }

    fun saveVisit(visit: Visit, lines: List<WorkLine>, selected: List<String>, linkedExpense: String?, currentReading: Reading?, after: () -> Unit) =
        action(after = after, evaluateTrigger = "work") { repository.saveVisit(visit, lines, selected, linkedExpense, currentReading) }
    fun voidVisit(id: String, after: () -> Unit) = action("Trabajo anulado", after, "work") { repository.voidVisit(id) }
    fun saveExpense(expense: Expense, after: () -> Unit) = action(after = after) { repository.saveExpense(expense) }
    fun voidExpense(id: String, after: () -> Unit) = action("Gasto anulado", after) { repository.voidExpense(id) }
    fun saveReading(reading: Reading, correction: Boolean, after: () -> Unit) = action(after = after, evaluateTrigger = "reading") { repository.saveReading(reading, correction) }
    fun saveManualWork(work: Visit, expenseId: String?, after: () -> Unit) = action(after = after, evaluateTrigger = "work") { repository.saveManualWork(work, expenseId) }
    fun saveFuel(entry: FuelEntry, reading: Reading?, after: () -> Unit) = action(after = after, evaluateTrigger = if (reading == null) "change" else "reading") {
        repository.db.withTransaction {
            repository.saveFuelEntry(entry)
            if (reading != null) repository.saveReading(reading)
        }
    }
    fun voidFuel(id: String, after: () -> Unit) = action("Repostaje anulado", after, "change") { repository.voidFuelEntry(id) }
    fun saveDeadline(entry: Deadline, after: () -> Unit) = action(after = after, evaluateTrigger = "change") { repository.saveDeadline(entry) }
    fun renewDeadline(id: String, date: String, after: () -> Unit) = action("Renovación guardada", after, "change") { repository.renewDeadline(id, date) }
    fun saveTyres(entries: List<TyreEvent>, paid: Long?, after: () -> Unit) = action(after = after, evaluateTrigger = "work") { repository.saveTyreEvents(entries, paid) }
    fun voidTyre(id: String, after: () -> Unit) = action("Registro de ruedas anulado", after, "work") { repository.voidTyreEvent(id) }
    fun voidHistoricalTyres(ids: List<String>, after: () -> Unit) = action("Histórico de ruedas anulado", after, "change") {
        repository.db.withTransaction { ids.forEach { repository.voidTyreEvent(it) } }
    }
    fun updatePlanning(date: String, km: Int) = action("Referencia del plan guardada", evaluateTrigger = "change") { repository.updatePlanningReference(date, km) }
    fun saveMaintenance(rule: dev.p38journal.domain.Rule, custom: Boolean, after: () -> Unit) = action("Mantenimiento guardado", after, "change") {
        if (custom) repository.saveCustomRule(rule) else repository.saveRuleOverride(rule)
    }
    fun saveNote(note: Note, after: () -> Unit) = action(after = after) { repository.saveNote(note) }
    fun deleteNote(id: String, after: () -> Unit) = action("Registro eliminado", after) { repository.deleteNote(id) }
    fun updateSettings(value: Settings, after: () -> Unit = {}) = action("Ajustes guardados", after, "permission") {
        ZoneId.of(value.zone)
        preferences.update(value)
        Notifications.schedule(getApplication())
    }
    fun importDocument(uri: Uri, link: String) = action("Documento importado") {
        val document = files.importDocument(uri)
        repository.saveDocument(document.copy(tags = listOf(document.tags, link).filter { it.isNotBlank() }.joinToString(" ")))
    }
    fun saveDocument(document: Document) = action { repository.saveDocument(document) }
    fun deleteDocument(id: String) = action("Documento eliminado") { repository.deleteDocument(id) }
    fun exportBackup(uri: Uri) = action("Copia externa guardada") { files.exportBackup(uri) }
    fun restoreBackup(uri: Uri, after: () -> Unit) = action("Copia restaurada", after) { files.restoreBackup(uri) }
    suspend fun previewBackup(uri: Uri): String = withContext(Dispatchers.IO) { files.previewBackup(uri) }
    suspend fun visitLines(id: String): List<WorkLine> = withContext(Dispatchers.IO) { repository.visitLines(id) }
    suspend fun eventsForVisit(id: String): List<String> = withContext(Dispatchers.IO) {
        repository.allEvents().filter { it.visitId == id && it.active }.map { it.ruleId }
    }

    private fun readable(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la acción. Inténtalo de nuevo."
}

fun freshId(): String = UUID.randomUUID().toString()
