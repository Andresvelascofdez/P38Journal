package dev.p38journal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF9ACBA8), onPrimary = Color(0xFF103B24),
        primaryContainer = Color(0xFF245B3B), onPrimaryContainer = Color(0xFFCCEAD2),
        secondary = Color(0xFFD7C39E), onSecondary = Color(0xFF3D301B),
        secondaryContainer = Color(0xFF4D4932), onSecondaryContainer = Color(0xFFF0E5C8),
        tertiary = Color(0xFFE3BFA5), onTertiary = Color(0xFF412C1C),
        tertiaryContainer = Color(0xFF5C4330), onTertiaryContainer = Color(0xFFFFDBC0),
        background = Color(0xFF111B16), onBackground = Color(0xFFF0EDE2),
        surface = Color(0xFF17251E), onSurface = Color(0xFFF0EDE2),
        surfaceVariant = Color(0xFF283B30), onSurfaceVariant = Color(0xFFBCCBBE),
        surfaceDim = Color(0xFF101A14), surfaceBright = Color(0xFF2E4035),
        surfaceContainerLowest = Color(0xFF0C1510), surfaceContainerLow = Color(0xFF15231B),
        surfaceContainer = Color(0xFF1B2C22), surfaceContainerHigh = Color(0xFF25392D),
        surfaceContainerHighest = Color(0xFF304538), surfaceTint = Color(0xFF9ACBA8),
        outline = Color(0xFF8A9B8D), outlineVariant = Color(0xFF405747),
        inverseSurface = Color(0xFFE5EBDF), inverseOnSurface = Color(0xFF243329), inversePrimary = Color(0xFF245B3B)
    ) else lightColorScheme(
        primary = Color(0xFF245B3B), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCDE4CA), onPrimaryContainer = Color(0xFF102F1C),
        secondary = Color(0xFF766343), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE7DFC5), onSecondaryContainer = Color(0xFF302A19),
        tertiary = Color(0xFF805E43), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF4DDC8), onTertiaryContainer = Color(0xFF352315),
        background = Color(0xFFFAF7EE), onBackground = Color(0xFF1C2C22),
        surface = Color(0xFFF9F6EC), onSurface = Color(0xFF1C2C22),
        surfaceVariant = Color(0xFFE4EADB), onSurfaceVariant = Color(0xFF485749),
        surfaceDim = Color(0xFFDEE3D4), surfaceBright = Color(0xFFFAF7EE),
        surfaceContainerLowest = Color(0xFFFFFDF6), surfaceContainerLow = Color(0xFFF4F3E7),
        surfaceContainer = Color(0xFFEDF0E2), surfaceContainerHigh = Color(0xFFE8EDDF),
        surfaceContainerHighest = Color(0xFFE1E8D7), surfaceTint = Color(0xFF245B3B),
        outline = Color(0xFF788A78), outlineVariant = Color(0xFFC1CDBB),
        inverseSurface = Color(0xFF2A382E), inverseOnSurface = Color(0xFFF2F2E7), inversePrimary = Color(0xFF9ACBA8)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable fun Heading(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }
@Composable fun Hint(text: String) { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, tag: String = label, numeric: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = !multiline, minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
fun Choice(label: String, value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(choices.firstOrNull { it.first == value }?.second ?: value, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onChange(id); open = false }) }
            }
        }
    }
}

@Composable
fun Check(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked, onCheckedChange = onChange)
            Text(text, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ItemCard(title: String, subtitle: String = "", onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit = {}) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Hint(subtitle)
            content()
        }
    }
}

@Composable
fun Confirm(title: String, body: String, confirm: String = "Confirmar", onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } })
}

fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).format(BigDecimal.valueOf(cents, 2))
fun moneyInput(cents: Long?): String = cents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') } ?: ""
fun parseMoney(text: String, optional: Boolean = false): Long? {
    if (optional && text.isBlank()) return null
    require(text.trim().matches(Regex("[0-9]+([,.][0-9]{1,2})?"))) { "Introduce un importe válido, como 12,50; máximo dos decimales." }
    return text.trim().replace(',', '.').toBigDecimal().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}
fun parseKm(text: String): Int? {
    if (text.isBlank()) return null
    require(text.trim().matches(Regex("[0-9]+"))) { "Introduce kilómetros enteros, sin separadores." }
    return text.trim().toInt().also { require(it >= 0) { "Los kilómetros deben ser positivos." } }
}
fun validateDate(text: String): String = LocalDate.parse(text.trim()).toString()
fun dateLabel(text: String?): String = text?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(it) } ?: "Fecha no conocida"
fun kmLabel(km: Int?): String = km?.let { "${NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(it)} km" } ?: "Kilómetros no registrados"
fun systemLabel(system: String): String = mapOf("engine" to "Motor", "cooling" to "Refrigeración", "transmission" to "Transmisión", "brakes" to "Frenos", "suspension" to "Suspensión", "electrical" to "Electricidad", "body" to "Carrocería", "steering" to "Dirección", "tyres" to "Neumáticos", "general" to "General", "exhaust" to "Escape", "fuel" to "Alimentación")[system] ?: system
fun statusLabel(status: String): String = when (status) { "INITIAL" -> "Mantenimiento inicial"; "DUE" -> "Vencido"; "SOON" -> "Próximamente"; else -> "Programado" }
