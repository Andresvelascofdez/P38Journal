package dev.p38journal.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.p38journal.data.Document
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DocumentViewer(document: Document, onBack: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable(document.id) { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var texts by remember { mutableStateOf<List<String>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var favourites by rememberSaveable(document.id) { mutableStateOf("") }
    var pageInput by rememberSaveable(document.id) { mutableStateOf("") }
    var favouriteLoaded by remember { mutableStateOf(false) }
    val isPdf = document.mime == "application/pdf"
    LaunchedEffect(document.id) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("document-favourites", 0)
            val saved = prefs.all[document.id]
            favourites = saved?.toString() ?: ""; favouriteLoaded = true
            if (isPdf) {
                try {
                    val file = File(context.filesDir, document.path)
                    PDDocument.load(file).use { pdf ->
                        require(pdf.numberOfPages <= 1000) { "PDF superior al límite de 1.000 páginas para búsqueda" }
                        val stripper = PDFTextStripper()
                        texts = (1..pdf.numberOfPages).map { n ->
                            stripper.startPage = n; stripper.endPage = n; stripper.getText(pdf).take(100000)
                        }
                    }
                } catch (e: Exception) { texts = emptyList() }
            }
        }
    }
    LaunchedEffect(document.id, page) {
        error = null; bitmap = null
        try {
            val image = withContext(Dispatchers.IO) {
                val file = File(context.filesDir, document.path)
                require(file.canonicalPath.startsWith(File(context.filesDir, "documents").canonicalPath + File.separator)) { "Ruta inválida" }
                if (isPdf) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            pageCount = renderer.pageCount
                            renderer.openPage(page.coerceIn(0, pageCount - 1)).use { pdfPage ->
                                val width = 1400; val height = (pdfPage.height.toDouble() * width / pdfPage.width).toInt().coerceAtMost(4000)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE)
                                    pdfPage.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                } else {
                    val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, info)
                    val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(info.outWidth, info.outHeight) / 1800) }
                    BitmapFactory.decodeFile(file.path, options) ?: error("No se pudo leer la imagen")
                }
            }
            bitmap = image
        } catch (e: Exception) { error = e.message ?: "No se pudo abrir el documento" }
    }
    val matches = remember(texts, query) { if (query.isBlank()) emptyList() else texts?.mapIndexedNotNull { i, text -> if (text.contains(query, true)) i else null } ?: emptyList() }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        TextButton(onClick = onBack) { Text("Volver a documentos") }
        Text(document.name, style = MaterialTheme.typography.titleLarge)
        if (isPdf) {
            Text("Página PDF ${page + 1} de $pageCount. La numeración impresa puede diferir.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("Anterior") }
                TextButton(onClick = { if (page + 1 < pageCount) page++ }, enabled = page + 1 < pageCount) { Text("Siguiente") }
                TextButton(onClick = {
                    favourites = (favourites.split(',').mapNotNull { it.toIntOrNull() }.filter { it >= 0 } + page).distinct().sorted().joinToString(",")
                    context.getSharedPreferences("document-favourites", 0).edit().putString(document.id, favourites).apply()
                }, enabled = favouriteLoaded) { Text("Marcar página") }
                favourites.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until pageCount }.forEach { n -> TextButton(onClick = { page = n }) { Text("Favorita ${n + 1}") } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pageInput, onValueChange = { pageInput = it.filter(Char::isDigit).take(4) }, label = { Text("Página PDF") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { pageInput.toIntOrNull()?.let { if (it in 1..pageCount) page = it - 1 } }, enabled = pageInput.toIntOrNull()?.let { it in 1..pageCount } == true) { Text("Ir") }
            }
            when {
                texts == null -> Text("Preparando búsqueda de texto…", style = MaterialTheme.typography.bodySmall)
                texts!!.none { it.isNotBlank() } -> Text("Búsqueda no disponible: PDF sin texto extraíble, protegido o no compatible. No se ha aplicado OCR.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("pdf_search_unavailable"))
                else -> {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar texto en el PDF") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (query.isNotBlank()) {
                        Text("${matches.size} páginas con coincidencias", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("pdf_match_count"))
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            matches.forEach { n -> TextButton(onClick = { page = n }) { Text("PDF ${n + 1}") } }
                        }
                    }
                }
            }
        }
        Row {
            TextButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.75f) }) { Text("Reducir") }
            TextButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(3f) }) { Text("Ampliar") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (bitmap == null && error == null) CircularProgressIndicator()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val visibleWidth = maxWidth
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            bitmap?.let { image ->
                Image(image.asImageBitmap(), contentDescription = "${document.name}, página PDF ${page + 1}",
                    modifier = Modifier.width(visibleWidth * zoom).aspectRatio(image.width.toFloat() / image.height))
            }
            }
        }
    }
}
