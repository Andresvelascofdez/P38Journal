package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.p38journal.data.*
import dev.p38journal.domain.Consumption
import dev.p38journal.domain.DecimalInput
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

fun consumptionLabel(value: BigDecimal?): String = value?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',')?.let { "$it L/100 km" } ?: "Datos insuficientes"
private fun litresInput(value: Long?): String = value?.let { BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString().replace('.', ',') } ?: ""
private fun litresLabel(value: Long?): String = value?.let { "${litresInput(it)} L" } ?: "Litros no conocidos"

@Composable
fun FuelPage(entries: List<FuelEntry>, add: () -> Unit, edit: (FuelEntry) -> Unit) {
    val summary = Consumption.calculate(entries)
    Page {
        Heading("Consumo medio")
        Text(consumptionLabel(summary.average), style = MaterialTheme.typography.headlineSmall)
        if (summary.totalKm > 0) Hint("Calculado con ${java.text.NumberFormat.getIntegerInstance(java.util.Locale("es", "ES")).format(summary.totalKm)} km de intervalos completos válidos.")
        if (summary.intervals.isNotEmpty()) {
            val first = summary.intervals.first()
            val last = summary.intervals.last()
            Hint("Período utilizado: ${dateLabel(first.startDate)} a ${dateLabel(last.endDate)}")
        }
        Hint("El primer depósito lleno sirve de referencia. Los parciales posteriores se suman al cerrar con el siguiente lleno.")
        if (summary.pendingCount > 0) Hint("${summary.pendingCount} repostajes pendientes del siguiente cierre válido.")
        if (summary.invalidIntervals > 0) Hint("${summary.invalidIntervals} intervalos con datos insuficientes excluidos del cálculo.")
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text("Registrar repostaje") }
        entries.filter { it.active }.sortedWith(compareByDescending<FuelEntry> { it.date }.thenByDescending { it.km }).forEach { entry ->
            ItemCard("${dateLabel(entry.date)} · ${kmLabel(entry.km)}", if (entry.full) "Depósito lleno" else "Repostaje parcial", { edit(entry) }) {
                Text("${litresLabel(entry.litresMilli)} · ${money(entry.totalCents)}")
                if (entry.km == null || entry.litresMilli == null) Hint("Datos insuficientes para cerrar un intervalo de consumo")
            }
        }
        if (summary.intervals.isNotEmpty()) {
            Heading("Intervalos completos")
            summary.intervals.forEach { interval ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("${dateLabel(interval.startDate)} → ${dateLabel(interval.endDate)}")
                    Text("${kmLabel(interval.startKm)} → ${kmLabel(interval.endKm)}")
                    Hint("${litresLabel(interval.litresMilli)} · ${consumptionLabel(interval.consumption)}")
                } }
            }
        }
    }
}

@Composable
fun FuelForm(model: JournalViewModel, original: FuelEntry?, currentReadingKm: Int?, busy: Boolean, after: () -> Unit, expense: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: freshId() }
    val readingId = rememberSaveable { freshId() }
    var date by rememberSaveable { mutableStateOf(original?.date ?: model.today()) }
    var km by rememberSaveable { mutableStateOf(original?.km?.toString() ?: "") }
    var litres by rememberSaveable { mutableStateOf(litresInput(original?.litresMilli)) }
    var amount by rememberSaveable { mutableStateOf(moneyInput(original?.totalCents)) }
    var full by rememberSaveable { mutableStateOf(original?.full ?: false) }
    var updateReading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var cancel by rememberSaveable { mutableStateOf(false) }
    val canonical = original?.let { "${it.date}/${it.km}/${it.litresMilli}/${it.totalCents}/${it.full}" }
    var loadedCanonical by rememberSaveable { mutableStateOf(canonical) }
    LaunchedEffect(canonical) {
        if (original != null && canonical != loadedCanonical) {
            val previous = loadedCanonical?.split('/')
            if (previous?.getOrNull(0) != original.date) date = original.date
            if (previous?.getOrNull(1) != original.km.toString()) km = original.km?.toString() ?: ""
            if (previous?.getOrNull(2) != original.litresMilli.toString()) litres = litresInput(original.litresMilli)
            if (previous?.getOrNull(3) != original.totalCents.toString()) amount = moneyInput(original.totalCents)
            if (previous?.getOrNull(4) != original.full.toString()) full = original.full
            loadedCanonical = canonical
        }
    }
    val parsedKm = runCatching { parseKm(km) }.getOrNull()
    val readingAllowed = date == model.today() && parsedKm != null && (currentReadingKm == null || parsedKm >= currentReadingKm)
    Page {
        if (original?.active == false) Hint("Repostaje anulado. Registra uno nuevo para añadir otro pago real.")
        Field(date, { date = it }, "Fecha (AAAA-MM-DD)", "fuel_date")
        Field(km, { km = it }, "Kilómetros del odómetro (si se conocen)", "fuel_km", numeric = true)
        Field(litres, { litres = it }, "Litros (si se conocen)", "fuel_litres", numeric = true)
        Field(amount, { amount = it }, "Importe total EUR", "fuel_amount", numeric = true)
        Check("Depósito lleno", full, "fuel_full") { full = it }
        Hint("Este importe se registra una sola vez en Gastos, como Gasolina. Sin litros o kilómetros no se calculará un consumo para ese tramo.")
        if (readingAllowed) Check("Actualizar también el odómetro con estos kilómetros", updateReading, "fuel_update_reading") { updateReading = it }
        Hint("Los kilómetros del repostaje no cambian el odómetro salvo que lo elijas expresamente para una lectura actual.")
        original?.expenseId?.let { expenseId -> OutlinedButton(onClick = { expense(expenseId) }, modifier = Modifier.fillMaxWidth()) { Text("Consultar gasto de gasolina") } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                val actualDate = validateDate(date)
                require(LocalDate.parse(actualDate) <= LocalDate.parse(model.today())) { "Un repostaje no puede tener fecha futura." }
                FuelEntry(id = id, date = actualDate, km = parseKm(km), litresMilli = litres.takeIf { it.isNotBlank() }?.let(DecimalInput::parseLitresMilli),
                    totalCents = requireNotNull(parseMoney(amount)), full = full, expenseId = original?.expenseId, active = true)
            }.onSuccess { entry ->
                val reading = if (updateReading && readingAllowed) Reading(id = readingId, date = entry.date, km = requireNotNull(entry.km)) else null
                model.saveFuel(entry, reading, after)
            }.onFailure { error = it.message ?: "Revisa los datos del repostaje." }
        }, enabled = !busy && original?.active != false, modifier = Modifier.fillMaxWidth()) { Text("Guardar repostaje") }
        if (original?.active == true) OutlinedButton(onClick = { cancel = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Anular repostaje") }
    }
    if (cancel) Confirm("Anular repostaje", "Se anulará su único gasto vinculado y se recalculará el consumo medio.", "Anular", { cancel = false }) { cancel = false; model.voidFuel(id, after) }
}
