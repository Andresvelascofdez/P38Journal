package dev.p38journal.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM visits WHERE active = 1 ORDER BY date DESC, id") fun observeVisits(): Flow<List<Visit>>
    @Query("SELECT * FROM expenses WHERE active = 1 ORDER BY date DESC, id") fun observeExpenses(): Flow<List<Expense>>
    @Query("SELECT * FROM readings ORDER BY date DESC, recordedAt DESC") fun observeReadings(): Flow<List<Reading>>
    @Query("SELECT * FROM notes ORDER BY date DESC, id") fun observeNotes(): Flow<List<Note>>
    @Query("SELECT * FROM documents ORDER BY name") fun observeDocuments(): Flow<List<Document>>
    @Query("SELECT * FROM inbox ORDER BY createdAt DESC") fun observeInbox(): Flow<List<Inbox>>
    @Query("SELECT * FROM events") fun observeEvents(): Flow<List<EventRecord>>
    @Query("SELECT * FROM rules") fun observeRules(): Flow<List<RuleRecord>>
    @Query("SELECT * FROM visits") suspend fun allVisits(): List<Visit>
    @Query("SELECT * FROM work_lines") suspend fun allLines(): List<WorkLine>
    @Query("SELECT * FROM work_lines WHERE visitId = :visitId") suspend fun visitLines(visitId: String): List<WorkLine>
    @Query("SELECT * FROM expenses") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM readings ORDER BY date DESC, recordedAt DESC") suspend fun allReadings(): List<Reading>
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>
    @Query("SELECT * FROM documents") suspend fun allDocuments(): List<Document>
    @Query("SELECT * FROM inbox") suspend fun allInbox(): List<Inbox>
    @Query("SELECT * FROM events") suspend fun allEvents(): List<EventRecord>
    @Query("SELECT * FROM rules") suspend fun allRules(): List<RuleRecord>
    @Query("SELECT * FROM rule_revisions") suspend fun allRevisions(): List<RuleRevisionRecord>
    @Query("SELECT * FROM audit") suspend fun allAudit(): List<Audit>
    @Query("SELECT * FROM ledger") suspend fun allLedger(): List<Ledger>
    @Query("SELECT * FROM vehicle") suspend fun allVehicles(): List<VehicleRecord>
    @Query("SELECT COALESCE(SUM(cents),0) FROM expenses WHERE active = 1") suspend fun totalExpenses(): Long
    @Upsert suspend fun putVisit(value: Visit)
    @Upsert suspend fun putLine(value: WorkLine)
    @Upsert suspend fun putExpense(value: Expense)
    @Upsert suspend fun putReading(value: Reading)
    @Upsert suspend fun putNote(value: Note)
    @Upsert suspend fun putDocument(value: Document)
    @Upsert suspend fun putInbox(value: Inbox)
    @Upsert suspend fun putEvent(value: EventRecord)
    @Upsert suspend fun putRule(value: RuleRecord)
    @Upsert suspend fun putRevision(value: RuleRevisionRecord)
    @Upsert suspend fun putAudit(value: Audit)
    @Upsert suspend fun putLedger(value: Ledger)
    @Upsert suspend fun putVehicle(value: VehicleRecord)
    @Query("DELETE FROM work_lines WHERE visitId = :visitId") suspend fun deleteVisitLines(visitId: String)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: String)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: String)
    @Query("DELETE FROM ledger WHERE id = :id") suspend fun deleteLedger(id: String)
    @Query("DELETE FROM ledger") suspend fun clearLedger()
    @Query("UPDATE inbox SET state = 'cancelled' WHERE ruleId = :ruleId AND state != 'read'") suspend fun cancelInbox(ruleId: String)
    @Query("UPDATE ledger SET state = 'cancelled' WHERE ruleId = :ruleId") suspend fun cancelLedger(ruleId: String)
}

@Database(entities = [Visit::class, WorkLine::class, Expense::class, Reading::class, Note::class,
    Document::class, Inbox::class, RuleRecord::class, RuleRevisionRecord::class, EventRecord::class,
    Audit::class, Ledger::class, VehicleRecord::class], version = 2, exportSchema = true)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun dao(): JournalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS audit (id TEXT NOT NULL PRIMARY KEY, at TEXT NOT NULL, action TEXT NOT NULL, recordId TEXT NOT NULL, detail TEXT NOT NULL)")
            }
        }
    }
}
