package dev.p38journal.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import dev.p38journal.data.Document
import dev.p38journal.data.Repository
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Files(private val context: Context, private val repo: Repository) {
    companion object {
        private const val MAX_DOCUMENT = 32L * 1024 * 1024
        private const val MAX_BACKUP = 256L * 1024 * 1024
        private val backupMutex = Mutex()
        fun checksum(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
    private val gson = Gson()
    private fun documentFile(document: Document): File {
        require(Regex("documents/[a-zA-Z0-9_-]+\\.(pdf|jpg|png|webp)").matches(document.path)) { "Ruta de documento no válida" }
        return File(context.filesDir, document.path)
    }
    suspend fun importDocument(uri: Uri): Document = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Documento"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                name = it.getString(0)?.take(200) ?: name
                if (!it.isNull(1)) require(it.getLong(1) in 1..MAX_DOCUMENT) { "Archivo vacío o superior a 32 MB" }
            }
        }
        val temp = File(context.cacheDir, "import-${UUID.randomUUID()}")
        try {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(8192); var count = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= MAX_DOCUMENT) { "Archivo superior a 32 MB" }; output.write(buffer, 0, n) }
            } } ?: error("No se pudo abrir el archivo")
            val header = ByteArray(12)
            temp.inputStream().use { it.read(header) }
            val ext = when {
                header.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-" -> "pdf"
                header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() -> "jpg"
                header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte() -> "png"
                String(header, Charsets.US_ASCII).startsWith("RIFF") && String(header, Charsets.US_ASCII).endsWith("WEBP") -> "webp"
                else -> error("Solo se admiten PDF e imágenes JPG, PNG o WebP válidas")
            }
            if (ext == "pdf") {
                android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { require(it.pageCount > 0) { "PDF sin p\u00e1ginas" } }
                }
            } else {
                val info = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temp.path, info)
                require(info.outWidth in 1..50000 && info.outHeight in 1..50000) { "Imagen no v\u00e1lida o dimensiones excesivas" }
            }
            require(temp.length() > 0 && context.filesDir.usableSpace > temp.length() * 2) { "Espacio insuficiente" }
            val id = UUID.randomUUID().toString()
            val path = "documents/$id.$ext"
            val target = File(context.filesDir, path); target.parentFile!!.mkdirs()
            temp.copyTo(target)
            val mime = if (ext == "pdf") "application/pdf" else "image/${if (ext == "jpg") "jpeg" else ext}"
            val doc = Document(id = id, name = name, mime = mime, path = path, sha256 = checksum(target), size = target.length())
            try { repo.saveDocument(doc) } catch (e: Exception) { target.delete(); throw e }
            doc
        } finally { temp.delete() }
    }
    fun openDocument(document: Document) {
        val file = documentFile(document)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    fun shareDocument(document: Document) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", documentFile(document))
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(document.mime)
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir documento")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private suspend fun writeBackup(file: File) {
        val snapshot = repo.exportSnapshot()
        val root = JsonParser.parseString(snapshot).asJsonObject
        val documents = root.getAsJsonArray("documents").map { gson.fromJson(it, Document::class.java) }
        val records = snapshot.toByteArray(Charsets.UTF_8)
        val settings = gson.toJson(Preferences(context).current().copy(externalFolderUri = "")).toByteArray(Charsets.UTF_8)
        val favourites = gson.toJson(context.getSharedPreferences("document-favourites", 0).all.filter { (key, value) -> documents.any { it.id == key } && (value is Int || value is String) }).toByteArray(Charsets.UTF_8)
        val manifest = com.google.gson.JsonObject().apply {
            addProperty("format", "p38-journal-backup")
            addProperty("version", 1)
            addProperty("createdAt", Instant.now().toString())
            addProperty("recordsSha256", digest(records))
            addProperty("settingsSha256", digest(settings))
            addProperty("favouritesSha256", digest(favourites))
        }
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun entry(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            entry("manifest.json", gson.toJson(manifest).toByteArray(Charsets.UTF_8))
            entry("records.json", records); entry("settings.json", settings); entry("favourites.json", favourites)
            var total = records.size.toLong() + settings.size
            for (doc in documents) {
                val attachment = documentFile(doc)
                require(attachment.exists() && attachment.length() == doc.size && checksum(attachment) == doc.sha256) { "Documento ausente o alterado: ${doc.name}" }
                total += attachment.length(); require(total <= MAX_BACKUP) { "La copia supera 256 MB" }
                zip.putNextEntry(ZipEntry(doc.path)); attachment.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
    private fun digest(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) { backupMutex.withLock {
        val temp = File(context.cacheDir, "export-${UUID.randomUUID()}.zip")
        try {
            writeBackup(temp)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> temp.inputStream().use { it.copyTo(output) } } ?: error("No se pudo escribir la copia")
        } finally { temp.delete() }
    } }
    private data class Prepared(val stage: File, val records: String, val settings: Settings, val documents: List<Document>, val favourites: Map<String, String>, val summary: String)
    private fun readLimited(file: File): String { require(file.length() <= 8 * 1024 * 1024) { "índice de copia demasiado grande" }; return file.readText() }
    private fun prepare(uri: Uri): Prepared {
        val stage = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val names = mutableSetOf<String>(); var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && (name in setOf("manifest.json", "records.json", "settings.json", "favourites.json") || Regex("documents/[a-zA-Z0-9_-]+\\.(pdf|jpg|png|webp)").matches(name))) { "Ruta no permitida en la copia" }
                    require(names.add(name) && names.size <= 2000) { "Entradas duplicadas o demasiados archivos" }
                    val target = File(stage, name); target.parentFile!!.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192); var size = 0L
                        while (true) { val n = zip.read(buffer); if (n < 0) break; size += n; total += n
                            require(total <= MAX_BACKUP && size <= if (name.startsWith("documents/")) MAX_DOCUMENT else 8L * 1024 * 1024) { "Copia demasiado grande" }
                            output.write(buffer, 0, n)
                        }
                    }; zip.closeEntry()
                }
            } } ?: error("No se pudo leer la copia")
            require(names.containsAll(listOf("manifest.json", "records.json", "settings.json"))) { "Copia incompleta" }
            val manifest = JsonParser.parseString(readLimited(File(stage, "manifest.json"))).asJsonObject
            require(manifest.get("format").asString == "p38-journal-backup" && manifest.get("version").asInt == 1) { "Formato/versión no compatible" }
            val records = readLimited(File(stage, "records.json")); val settingsJson = readLimited(File(stage, "settings.json"))
            require(digest(records.toByteArray()) == manifest.get("recordsSha256").asString && digest(settingsJson.toByteArray()) == manifest.get("settingsSha256").asString) { "Checksum del índice incorrecto" }
            repo.validateSnapshot(records)
            val settings = gson.fromJson(settingsJson, Settings::class.java)
            java.time.ZoneId.of(settings.zone)
            require(settings.leadDays in 0..365 && settings.leadKm in 0..50000 && settings.hour in 0..23) { "Preferencias inválidas" }
            val root = JsonParser.parseString(records).asJsonObject
            val documents = root.getAsJsonArray("documents").map { gson.fromJson(it, Document::class.java) }
            require(names == (documents.map { it.path } + listOf("manifest.json", "records.json", "settings.json") + if ("favourites.json" in names) listOf("favourites.json") else emptyList()).toSet()) { "Archivos no relacionados con registros" }
            val favourites = mutableMapOf<String, String>()
            if ("favourites.json" in names) {
                val data = readLimited(File(stage, "favourites.json"))
                require(manifest.has("favouritesSha256") && digest(data.toByteArray()) == manifest.get("favouritesSha256").asString) { "Checksum de marcadores incorrecto" }
                for ((key, value) in JsonParser.parseString(data).asJsonObject.entrySet()) {
                    require(documents.any { it.id == key } && value.isJsonPrimitive) { "Marcador sin documento" }
                    val pages = value.asString
                    require(pages.length <= 10000 && pages.split(',').all { it.toIntOrNull()?.let { n -> n in -1..10000 } == true }) { "Marcador de página inválido" }
                    favourites[key] = pages
                }
            }
            for (doc in documents) {
                documentFile(doc)
                val file = File(stage, doc.path)
                require(doc.size in 1..MAX_DOCUMENT && file.length() == doc.size && checksum(file) == doc.sha256) { "Checksum de documento incorrecto" }
                val existing = File(context.filesDir, doc.path)
                require(!existing.exists() || checksum(existing) == doc.sha256) { "Conflicto de adjunto; no se sobrescribirá" }
            }
            require(context.filesDir.usableSpace > total * 2) { "Espacio insuficiente para restaurar" }
            val counts = listOf("visits" to "trabajos", "expenses" to "gastos", "readings" to "lecturas", "notes" to "notas", "documents" to "archivos", "fuelEntries" to "repostajes", "deadlines" to "vencimientos", "deadlineRenewals" to "renovaciones", "tyreEvents" to "registros de ruedas")
                .joinToString(" · ") { (key, label) -> "$label: ${root.getAsJsonArray(key)?.size() ?: 0}" }
            return Prepared(stage, records, settings.copy(externalFolderUri = ""), documents, favourites, "Copia validada. $counts. Se integrarán por identificador; los registros con el mismo ID se conservarán tal como están en este teléfono. Los registros locales ausentes en la copia se conservarán. Las reglas personalizadas de la copia sustituyen reglas precargadas; las personalizaciones locales se conservan y el catálogo estándar no retrocede de revisión. Los avisos se recalcularán.")
        } catch (e: Exception) { stage.deleteRecursively(); throw e }
    }
    suspend fun previewBackup(uri: Uri): String = withContext(Dispatchers.IO) {
        val p = prepare(uri); try { p.summary } finally { p.stage.deleteRecursively() }
    }
    suspend fun restoreBackup(uri: Uri) = withContext(Dispatchers.IO) { backupMutex.withLock {
        val p = prepare(uri); val created = mutableListOf<File>()
        val preferences = Preferences(context); val previous = preferences.current()
        try {
            for (doc in p.documents) {
                val target = documentFile(doc)
                if (!target.exists()) { target.parentFile!!.mkdirs(); File(p.stage, doc.path).copyTo(target); created.add(target) }
            }
            preferences.update(p.settings.copy(enabled = false))
            try { repo.restoreSnapshot(p.records) } catch (e: Exception) { preferences.update(previous); throw e }
        } catch (e: Exception) { created.forEach { it.delete() }; throw e }
        finally { p.stage.deleteRecursively() }
        val favouriteStore = context.getSharedPreferences("document-favourites", 0)
        val edit = favouriteStore.edit()
        p.favourites.forEach { (key, pages) -> if (!favouriteStore.contains(key)) edit.putString(key, pages) }
        edit.commit()
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        Notifications.schedule(context); Notifications.refresh(context)
    } }
    suspend fun rotateInternalBackup() = withContext(Dispatchers.IO) { backupMutex.withLock {
        val directory = File(context.filesDir, "recovery").apply { mkdirs() }
        val latest = directory.listFiles()?.filter { it.name.startsWith("recovery-") }?.maxByOrNull { it.lastModified() }
        if (latest != null && System.currentTimeMillis() - latest.lastModified() < 24 * 60 * 60 * 1000L) { copyExternalIfSelected(latest); return@withLock }
        val temp = File(directory, "pending.zip")
        try {
            writeBackup(temp)
            val target = File(directory, "recovery-${System.currentTimeMillis()}.zip")
            require(temp.renameTo(target)) { "No se pudo guardar recuperación interna" }
            copyExternalIfSelected(target)
            directory.listFiles()?.filter { it.name.startsWith("recovery-") }?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
        } finally { temp.delete() }
    } }
    suspend fun setExternalFolder(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val preferences = Preferences(context)
        preferences.update(preferences.current().copy(externalFolderUri = uri.toString()))
    }
    suspend fun clearExternalFolder() {
        val preferences = Preferences(context); val old = preferences.current()
        if (old.externalFolderUri.isNotBlank()) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old.externalFolderUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        preferences.update(old.copy(externalFolderUri = ""))
    }
    suspend fun externalFolderState(): String = withContext(Dispatchers.IO) {
        val chosen = Preferences(context).current().externalFolderUri
        if (chosen.isBlank()) return@withContext "Carpeta externa automática sin elegir"
        val allowed = context.contentResolver.persistedUriPermissions.any { it.uri.toString() == chosen && it.isWritePermission }
        if (!allowed) return@withContext "Acceso a carpeta externa revocado; no se está copiando"
        context.getSharedPreferences("external-backup-status", 0).getString("status", "Carpeta elegida; pendiente de primera copia")!!
    }
    private suspend fun copyExternalIfSelected(file: File) {
        val chosen = Preferences(context).current().externalFolderUri
        if (chosen.isBlank()) return
        val status = context.getSharedPreferences("external-backup-status", 0)
        val allowed = context.contentResolver.persistedUriPermissions.any { it.uri.toString() == chosen && it.isWritePermission }
        if (!allowed) { status.edit().putString("status", "Acceso revocado; copia externa no realizada").apply(); return }
        if (status.getString("copied", "") == chosen + file.name) return
        try {
            val tree = Uri.parse(chosen)
            val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
            val uri = android.provider.DocumentsContract.createDocument(context.contentResolver, parent, "application/zip", file.name) ?: error("No se pudo crear la copia externa")
            try {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            } catch (e: Exception) { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }; throw e }
            status.edit().putString("copied", chosen + file.name).putString("status", "Última copia externa: ${Instant.now()}").apply()
        } catch (e: Exception) { status.edit().putString("status", "Copia externa fallida: ${e.message ?: "acceso no disponible"}").apply() }
    }
    fun internalBackups(): List<File> = File(context.filesDir, "recovery").listFiles()?.filter { it.name.startsWith("recovery-") }?.sortedByDescending { it.lastModified() } ?: emptyList()
}
