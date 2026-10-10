package dev.p38journal.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.p38journal.data.Document
import dev.p38journal.platform.Notifications
import dev.p38journal.platform.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

@Composable
fun DocumentsPage(model: JournalViewModel, documents: List<Document>, link: String, open: (Document) -> Unit) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf<String?>(null) }
    var selectExisting by rememberSaveable { mutableStateOf(false) }
    var editTags by rememberSaveable { mutableStateOf<String?>(null) }
    var tagDraft by rememberSaveable { mutableStateOf("") }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importDocument(uri, link) }
    Page {
        Button(onClick = { import.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Importar PDF o foto") }
        Hint("Los archivos se copian a almacenamiento privado y quedan disponibles sin conexión. Importa documentos que puedas usar legítimamente.")
        if (link.isNotEmpty()) {
            Heading("Adjuntos de este registro")
            OutlinedButton(onClick = { selectExisting = !selectExisting }, modifier = Modifier.fillMaxWidth()) { Text(if (selectExisting) "Ver adjuntos vinculados" else "Vincular un documento existente") }
        }
        Field(search, { search = it }, "Buscar nombre o etiqueta")
        Check("Solo favoritos", onlyFavorites) { onlyFavorites = it }
        val filtered = documents.filter { doc ->
            val tags = doc.tags.split(" ")
            (link.isEmpty() || selectExisting || link in tags) && (!onlyFavorites || "favorite" in tags) && (doc.name + " " + doc.tags).contains(search, true)
        }
        if (filtered.isEmpty()) Hint("No hay documentos en esta selección. Importa un archivo o cambia los filtros.")
        filtered.forEach { document ->
            val tags = document.tags.split(" ").filter { it.isNotBlank() }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(document.name, style = MaterialTheme.typography.titleMedium)
                    Hint("${if (document.mime == "application/pdf") "PDF" else "Imagen"} · ${document.size / 1024} KB")
                    Button(onClick = { open(document) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir documento") }
                    TextButton(onClick = { model.saveDocument(document.copy(tags = (if ("favorite" in tags) tags - "favorite" else tags + "favorite").joinToString(" "))) }) { Text(if ("favorite" in tags) "Quitar favorito" else "Marcar favorito") }
                    val visibleTags = tags.filterNot { it == "favorite" || Regex("[0-9a-fA-F-]{36}").matches(it) }
                    if (visibleTags.isNotEmpty()) Hint("Etiquetas: ${visibleTags.joinToString(", ")}")
                    TextButton(onClick = { editTags = document.id; tagDraft = visibleTags.joinToString(" ") }) { Text("Editar etiquetas") }
                    if (link.isNotBlank()) TextButton(onClick = {
                        model.saveDocument(document.copy(tags = (if (link in tags) tags - link else tags + link).joinToString(" ")))
                    }) { Text(if (link in tags) "Quitar vínculo a este registro" else "Vincular a este registro") }
                    TextButton(onClick = { delete = document.id }) { Text("Eliminar documento") }
                }
            }
        }
        if (link.isEmpty()) {
            Heading("Manuales del fabricante")
            Hint("Estos enlaces externos se abren solo cuando los pulses. El cálculo del plan no necesita descargarlos.")
            listOf(
                "Hoja de mantenimiento 04/00 (español)" to "RR_P38_Maintenance_Sheet_2000_ES.pdf",
                "Manual 2000/2001 LRL0314ENG" to "RR_P38_Owners_Handbook_2000_UK.pdf",
                "Manual 1999 (apoyo en español)" to "RR_P38_Owners_Handbook_1999_ES.pdf",
                "Suplemento 2000 (español)" to "RR_P38_Owners_Handbook_Supplement_2000_ES.pdf"
            ).forEach { (title, path) ->
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.fuddymuckers.co.uk/rtfm/RangeRover/P38/$path"))) }
                        .onFailure { model.announce("No hay una aplicación que pueda abrir este enlace.") }
                }, modifier = Modifier.fillMaxWidth()) { Text(title) }
            }
        }
    }
    delete?.let { id -> Confirm("Eliminar documento", "Se retirará de la biblioteca y de todos sus vínculos. Conserva una copia externa si quieres recuperarlo.", "Eliminar", { delete = null }) { delete = null; model.deleteDocument(id) } }
    editTags?.let { id ->
        AlertDialog(onDismissRequest = { editTags = null }, title = { Text("Etiquetas del documento") }, text = {
            Field(tagDraft, { tagDraft = it }, "Etiquetas separadas por espacios")
        }, confirmButton = { TextButton(onClick = {
            documents.firstOrNull { it.id == id }?.let { document ->
                val protected = document.tags.split(" ").filter { it == "favorite" || Regex("[0-9a-fA-F-]{36}").matches(it) }
                model.saveDocument(document.copy(tags = (protected + tagDraft.trim().split(Regex("\\s+")).filter { it.isNotBlank() }).distinct().joinToString(" ")))
            }
            editTags = null
        }) { Text("Guardar etiquetas") } }, dismissButton = { TextButton(onClick = { editTags = null }) { Text("Cancelar") } })
    }
}

@Composable
fun SettingsPage(model: JournalViewModel, settings: Settings, busy: Boolean, section: String = "all") {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(Notifications.permissionState(context)) }
    var zone by rememberSaveable { mutableStateOf(settings.zone) }
    var days by rememberSaveable { mutableStateOf(settings.leadDays.toString()) }
    var kilometres by rememberSaveable { mutableStateOf(settings.leadKm.toString()) }
    var hour by rememberSaveable { mutableStateOf(settings.hour.toString()) }
    var weekly by rememberSaveable { mutableStateOf(settings.weekly) }
    var checks by rememberSaveable { mutableStateOf(settings.checks) }
    var odometer by rememberSaveable { mutableStateOf(settings.odometerReminder) }
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var previewError by rememberSaveable { mutableStateOf<String?>(null) }
    var internal by remember { mutableStateOf<List<File>>(emptyList()) }
    var externalState by remember { mutableStateOf("Comprobando carpeta externa…") }
    val planning by model.planning.collectAsStateWithLifecycle()
    var baselineDate by rememberSaveable { mutableStateOf("") }
    var baselineKm by rememberSaveable { mutableStateOf("175000") }
    var planningDirty by rememberSaveable { mutableStateOf(false) }
    var planningConfirm by rememberSaveable { mutableStateOf(false) }
    var planningError by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(planning) { if (!planningDirty) planning?.let { baselineDate = it.baselineDate; baselineKm = it.baselineKm.toString() } }
    LaunchedEffect(settings) {
        if (!dirty) {
            zone = settings.zone; days = settings.leadDays.toString(); kilometres = settings.leadKm.toString(); hour = settings.hour.toString()
            weekly = settings.weekly; checks = settings.checks; odometer = settings.odometerReminder; enabled = settings.enabled
        }
    }
    LaunchedEffect(Unit) { internal = withContext(Dispatchers.IO) { model.files.internalBackups() } }
    LaunchedEffect(settings.externalFolderUri, busy) {
        if (!busy) externalState = withContext(Dispatchers.IO) { model.files.externalFolderState() }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permission = Notifications.permissionState(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = Notifications.permissionState(context)
        model.action(evaluateTrigger = "permission") {}
        model.announce(if (it) "Permiso concedido. Consulta los canales de Android." else "Permiso denegado. La bandeja sigue disponible.")
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) model.exportBackup(uri) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { preview = null; previewError = null; restoreUri = uri.toString() }
    }
    val externalFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.action("Carpeta externa elegida") { model.files.setExternalFolder(uri) }
    }
    LaunchedEffect(restoreUri) {
        restoreUri?.let { value ->
            try { preview = model.previewBackup(Uri.parse(value)) }
            catch (e: Exception) { previewError = e.message ?: "La copia no es válida."; restoreUri = null }
        }
    }
    fun values(turnOn: Boolean = enabled): Settings {
        ZoneId.of(zone.trim())
        val leadDays = requireNotNull(days.toIntOrNull()) { "Introduce días enteros." }
        val leadKm = requireNotNull(kilometres.toIntOrNull()) { "Introduce kilómetros enteros." }
        val preferredHour = requireNotNull(hour.toIntOrNull()) { "Introduce una hora entre 0 y 23." }
        require(leadDays in 0..365 && leadKm in 0..50000 && preferredHour in 0..23) { "Días: 0–365; kilómetros: 0–50.000; hora: 0–23." }
        return settings.copy(zone = zone.trim(), leadDays = leadDays, leadKm = leadKm, hour = preferredHour, enabled = turnOn, weekly = weekly, checks = checks, odometerReminder = odometer)
    }
    Page {
        if (section != "copies") {
        Heading("Referencia del plan")
        Hint("El plan empieza en esta fecha y estos kilómetros cuando no hay un trabajo posterior. Es una referencia elegida: no registra una lectura ni un mantenimiento realizado.")
        Field(baselineDate, { baselineDate = it; planningDirty = true }, "Fecha inicial del plan (AAAA-MM-DD)", "planning_date")
        Field(baselineKm, { baselineKm = it; planningDirty = true }, "Kilómetros iniciales del plan", "planning_km", numeric = true)
        planningError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = {
            runCatching { validateDate(baselineDate); requireNotNull(parseKm(baselineKm)) { "Introduce los kilómetros de referencia." } }
                .onSuccess { planningError = null; planningConfirm = true }
                .onFailure { planningError = it.message ?: "Revisa la referencia." }
        }, enabled = !busy && planning != null, modifier = Modifier.fillMaxWidth()) { Text("Guardar referencia del plan") }
        Heading("Avisos locales")
        Text(permission)
        Text(if (settings.enabled) "Evaluación de avisos activada" else "Avisos desactivados")
        Button(onClick = {
            runCatching { values(true) }.onSuccess { value ->
                model.updateSettings(value) {
                    dirty = false; enabled = true
                    if (Build.VERSION.SDK_INT >= 33) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { permission = Notifications.permissionState(context); Notifications.refresh(context) }
                }
            }.onFailure { error = it.message ?: "Revisa los ajustes." }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Activar avisos") }
        OutlinedButton(onClick = {
            runCatching { Notifications.test(context) }.onSuccess { model.announce("Aviso de prueba solicitado. Comprueba la bandeja de notificaciones de Android.") }.onFailure { model.announce(it.message ?: "No se pudo solicitar el aviso.") }
            permission = Notifications.permissionState(context)
        }, modifier = Modifier.fillMaxWidth()) { Text("Probar aviso") }
        OutlinedButton(onClick = { Notifications.openSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Ajustes de notificaciones de Android") }
        Check("Evaluar avisos de mantenimiento", enabled) { enabled = it; dirty = true }
        Field(zone, { zone = it; dirty = true }, "Zona horaria (por ejemplo Asia/Nicosia)")
        Field(hour, { hour = it; dirty = true }, "Hora preferida (0–23)", numeric = true)
        Hint("Ventana aproximada de envío. Android puede diferir las comprobaciones. Forzar detención desde Ajustes requiere abrir de nuevo la app.")
        Field(days, { days = it; dirty = true }, "Avisar días antes", numeric = true)
        Field(kilometres, { kilometres = it; dirty = true }, "Avisar kilómetros antes", numeric = true)
        Hint("Son preferencias de aviso, no intervalos del fabricante. Las lecturas nuevas evalúan los pendientes en cuanto se guardan.")
        Check("Recordar pendientes semanalmente", weekly) { weekly = it; dirty = true }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { runCatching { values() }.onSuccess { model.updateSettings(it) { dirty = false } }.onFailure { error = it.message ?: "Revisa los ajustes." } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar ajustes") }
        }
        if (section != "preferences") {
        Heading("Copias externas")
        Hint("Incluyen registros, gastos, documentos y ajustes. Guarda el archivo fuera del teléfono para protegerte ante pérdida o desinstalación.")
        Button(onClick = { export.launch("P38-Journal-${model.today()}.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia completa") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Validar copia y restaurar") }
        Heading("Carpeta para copias automáticas")
        Text(externalState)
        Hint("Elige una carpeta con el selector de Android. La copia diaria depende del acceso concedido y del trabajo diferible del sistema.")
        OutlinedButton(onClick = { externalFolder.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Elegir carpeta externa") }
        if (settings.externalFolderUri.isNotBlank()) TextButton(onClick = { model.action("Copias a carpeta desactivadas") { model.files.clearExternalFolder() } }, enabled = !busy) { Text("Desactivar copia a esta carpeta") }
        previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (restoreUri != null && preview == null) { CircularProgressIndicator(); Hint("Validando la copia y sus documentos…") }
        Heading("Recuperación interna")
        Hint("Hasta tres copias internas, una por día. No sobreviven a desinstalar ni a perder este teléfono. La copia externa es la protección completa.")
        if (internal.isEmpty()) Hint("Aún no hay copias internas disponibles.")
        internal.forEach { file -> OutlinedButton(onClick = { preview = null; previewError = null; restoreUri = Uri.fromFile(file).toString() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Revisar ${file.name}") } }
        }
        Heading("Privacidad")
        Hint("Español · EUR · km. Sin cuenta ni servidor. Los documentos y los datos quedan en almacenamiento privado de esta instalación.")
    }
    if (planningConfirm) Confirm("Cambiar referencia del plan", "Se recalcularán los mantenimientos que dependen de esta referencia. Los trabajos y las lecturas reales se conservarán.", "Guardar referencia", { planningConfirm = false }) {
        planningConfirm = false
        model.updatePlanning(validateDate(baselineDate), requireNotNull(parseKm(baselineKm)))
        planningDirty = false
    }
    preview?.let { info -> Confirm("Restaurar copia validada", "$info\n\nSe integrarán los registros nuevos de la copia y se conservarán los identificadores ya existentes. No revierte ediciones ni anulaciones actuales. Para recuperar todos los datos originales, restaura en una instalación vacía. Después tendrás que activar los avisos en este teléfono.", "Restaurar", { preview = null; restoreUri = null }) {
        val selected = restoreUri
        preview = null; restoreUri = null
        if (selected != null) model.restoreBackup(Uri.parse(selected)) { dirty = false; enabled = false }
    } }
}
