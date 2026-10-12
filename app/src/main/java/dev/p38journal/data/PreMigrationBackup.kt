package dev.p38journal.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant

object PreMigrationBackup {
    fun ensure(context: Context, databaseName: String = "p38-journal.db"): File? {
        val source = context.getDatabasePath(databaseName)
        if (!source.exists() || source.length() < 100) return null
        val header = ByteArray(100)
        source.inputStream().use { require(it.read(header) == 100) }
        val headerVersion = ByteBuffer.wrap(header, 60, 4).int
        if (headerVersion !in 1..3 && !(headerVersion == 0 && File(source.path + "-wal").exists())) return null
        val sourceFiles = listOf(source, File(source.path + "-wal")).filter { it.exists() }
        val currentChecksums = sourceFiles.associate { it.name to checksum(it) }
        val previous = File(context.filesDir, "migration-backups").listFiles()?.firstOrNull { directory ->
            runCatching {
                val manifest = com.google.gson.JsonParser.parseString(File(directory, "manifest.json").readText()).asJsonObject
                val saved = manifest.getAsJsonObject("checksums").entrySet().associate { it.key to it.value.asString }
                manifest.get("verifiedIntegrity").asBoolean && saved == currentChecksums && saved.all { (name, hash) ->
                    val file = File(directory, name); file.exists() && checksum(file) == hash
                }
            }.getOrDefault(false)
        }
        if (previous != null) return previous
        val folder = File(context.filesDir, "migration-backups/pre-v4-${Instant.now().toEpochMilli()}-${java.util.UUID.randomUUID()}")
        require(folder.mkdirs()) { "No se pudo preparar copia previa a la migración" }
        try {
            val checksums = linkedMapOf<String, String>()
            sourceFiles.forEach { input ->
                val output = File(folder, input.name)
                input.copyTo(output)
                val checksum = checksum(input)
                check(checksum == checksum(output)) { "La copia previa no supera la comprobación de integridad" }
                checksums[input.name] = checksum
            }
            val copy = SQLiteDatabase.openDatabase(File(folder, databaseName).path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            val copyVersion: Int
            try {
                copy.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) { "La base previa no supera integrity_check; migración detenida" }
                }
                copyVersion = copy.version
            } finally { copy.close() }
            if (copyVersion >= 4) { folder.deleteRecursively(); return null }
            File(folder, "manifest.json").writeText(Gson().toJson(mapOf(
                "format" to "p38-pre-migration", "sourceSchema" to copyVersion,
                "targetSchema" to 4, "createdAt" to Instant.now().toString(),
                "checksums" to checksums, "verifiedIntegrity" to true,
                "attachments" to "Unmodified private documents remain in their original location")))
            return folder
        } catch (error: Exception) { folder.deleteRecursively(); throw error }
    }
    private fun checksum(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(8192)
        while (true) { val count = input.read(bytes); if(count < 0) break; digest.update(bytes, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
