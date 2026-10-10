package dev.p38journal.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()
private fun today() = LocalDate.now(ZoneId.of("Asia/Nicosia")).toString()

@Entity(tableName = "visits")
data class Visit(@PrimaryKey val id: String = newId(), val date: String = today(), val km: Int? = null,
    val kmExact: Boolean = true, val title: String = "", val workshop: String = "", val notes: String = "",
    val paidCents: Long? = null, val active: Boolean = true, val dateExact: Boolean = true,
    val operationalAnchor: Boolean = false)

@Entity(tableName = "work_lines", foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("visitId")])
data class WorkLine(@PrimaryKey val id: String = newId(), val visitId: String, val title: String = "",
    val ruleId: String? = null, val costCents: Long? = null, val category: String = "", val parts: String = "")

@Entity(tableName = "expenses", foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["visitId"], unique = true)])
data class Expense(@PrimaryKey val id: String = newId(), val date: String = today(), val cents: Long,
    val concept: String, val category: String = "other", val notes: String = "", val visitId: String? = null,
    val active: Boolean = true)

@Entity(tableName = "readings")
data class Reading(@PrimaryKey val id: String = newId(), val date: String = today(), val km: Int,
    val recordedAt: String = java.time.Instant.now().toString())

@Entity(tableName = "notes")
data class Note(@PrimaryKey val id: String = newId(), val kind: String, val title: String, val body: String = "",
    val status: String = "", val date: String = today(), val relatedId: String? = null)

@Entity(tableName = "documents")
data class Document(@PrimaryKey val id: String = newId(), val name: String, val mime: String, val path: String,
    val sha256: String, val size: Long, val tags: String = "")

@Entity(tableName = "inbox", indices = [Index(value = ["ruleId", "generation", "id"], unique = true)])
data class Inbox(@PrimaryKey val id: String, val ruleId: String, val title: String, val body: String,
    val generation: String, val state: String = "pending", val createdAt: String = java.time.Instant.now().toString())

@Entity(tableName = "rules")
data class RuleRecord(@PrimaryKey val id: String, val json: String, val revision: Int, val ownerOverride: Boolean = false)

@Entity(tableName = "rule_revisions")
data class RuleRevisionRecord(@PrimaryKey val id: String, val ruleId: String, val revision: Int, val json: String)

@Entity(tableName = "events", foreignKeys = [ForeignKey(entity = Visit::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = RuleRecord::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("visitId"), Index("ruleId")])
data class EventRecord(@PrimaryKey val id: String, val visitId: String, val ruleId: String, val date: String?,
    val km: Int?, val dateExact: Boolean, val kmExact: Boolean, val active: Boolean, val revision: Int,
    val workLineId: String? = null, val provenance: String = "owner_recorded", val operationalAnchor: Boolean = false)

@Entity(tableName = "audit")
data class Audit(@PrimaryKey val id: String = newId(), val at: String, val action: String, val recordId: String,
    val detail: String)

@Entity(tableName = "ledger")
data class Ledger(@PrimaryKey val id: String, val ruleId: String, val generation: String, val kind: String,
    val window: String, val state: String = "pending", val updatedAt: String)

@Entity(tableName = "vehicle")
data class VehicleRecord(@PrimaryKey val id: String, val json: String)
