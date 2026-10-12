package dev.p38journal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.p38journal.data.*
import dev.p38journal.domain.Tyres
import java.time.LocalDate

fun axisLabel(axis: String): String = if (axis == "front") "Ruedas delanteras" else "Ruedas traseras"
private fun tyreKindLabel(kind: String): String = when (kind) { "mount" -> "Montaje inicial"; "review" -> "Revisadas"; else -> "Cambiadas" }

@Composable
fun TyresPage(events: List<TyreEvent>, currentKm: Int?, open: (String) -> Unit, both: () -> Unit) {
    Page {
        Heading("Ruedas por eje")
        Hint("El uso y los avisos se calculan con el odómetro que introduces manualmente. Un aviso propone revisar y valorar el cambio.")
        Tyres.evaluate(events, currentKm).forEach { state ->
            ItemCard(axisLabel(state.axis), state.mount?.let { "Montaje: ${kmLabel(it.km)}" } ?: "Montaje sin registrar", { open(state.axis) }) {
                state.usedKm?.let { Text("Uso del juego actual: ${kmLabel(it)}") }
                state.dueKm?.let { Text("Próximo aviso: ${kmLabel(it)}") }
                state.remainingKm?.let { Hint(if (it > 0) "Faltan ${kmLabel(it)}" else "Toca revisar y valorar el cambio") }
            }
        }
        OutlinedButton(onClick = both, modifier = Modifier.fillMaxWidth()) { Text("Cambiar ambas") }
    }
}

@Composable
fun TyreDetail(axis: String, events: List<TyreEvent>, currentKm: Int?, action: (String, String) -> Unit, edit: (TyreEvent) -> Unit) {
    val state = Tyres.evaluate(events, currentKm).first { it.axis == axis }
    Page {
        Heading(axisLabel(axis))
        val mount = state.mount
        if (mount == null) {
            Hint("Registra los kilómetros reales del montaje del juego actual.")
            Button(onClick = { action(axis, "mount") }, modifier = Modifier.fillMaxWidth()) { Text("Registrar montaje") }
        } else {
            Text("Montaje del juego actual: ${kmLabel(mount.km)}")
            Text("Uso del juego actual: ${state.usedKm?.let(::kmLabel) ?: "pendiente de lectura actual"}")
            state.review?.let { Text("Última revisión: ${kmLabel(it.km)}") }
            state.dueKm?.let { Text("Próximo aviso: ${kmLabel(it)}") }
            state.remainingKm?.let { Text(if (it > 0) "Faltan ${kmLabel(it)}" else "Toca revisar y valorar el cambio") }
            if (mount.notes.isNotBlank()) Text(mount.notes)
            Button(onClick = { action(axis, "review") }, modifier = Modifier.fillMaxWidth()) { Text("Revisadas") }
            OutlinedButton(onClick = { action(axis, "change") }, modifier = Modifier.fillMaxWidth()) { Text("Cambiadas") }
        }
        Heading("Historial del eje")
        events.filter { it.axis == axis }.sortedByDescending { it.sequence }.forEach { event ->
            ItemCard(tyreKindLabel(event.kind), "${kmLabel(event.km)} · ${dateLabel(event.date)}${if (!event.active) " · Anulado" else ""}", { edit(event) }) {
                Hint("Aviso tras ${kmLabel(event.intervalKm)}")
                if (event.notes.isNotBlank()) Text(event.notes)
                if (!event.tracking) Hint("Histórico incorporado; se conserva el seguimiento local")
            }
        }
    }
}

@Composable
fun TyreForm(model: JournalViewModel, axes: List<String>, kind: String, originals: List<TyreEvent>, allEvents: List<TyreEvent>, expenses: List<Expense>, busy: Boolean, after: () -> Unit, expense: (String) -> Unit) {
    val ids by rememberSaveable { mutableStateOf(axes.joinToString("|") { axis -> originals.firstOrNull { it.axis == axis }?.id ?: freshId() }) }
    val first = originals.firstOrNull()
    val historical = originals.isNotEmpty() && originals.all { !it.tracking }
    var km by rememberSaveable { mutableStateOf(first?.km?.toString() ?: "") }
    var date by rememberSaveable { mutableStateOf(first?.date ?: "") }
    var notes by rememberSaveable { mutableStateOf(first?.notes ?: "") }
    val states = Tyres.evaluate(allEvents, null)
    var frontInterval by rememberSaveable { mutableStateOf((originals.firstOrNull { it.axis == "front" }?.intervalKm ?: states.first { it.axis == "front" }.let { it.review ?: it.mount }?.intervalKm)?.toString() ?: "") }
    var rearInterval by rememberSaveable { mutableStateOf((originals.firstOrNull { it.axis == "rear" }?.intervalKm ?: states.first { it.axis == "rear" }.let { it.review ?: it.mount }?.intervalKm)?.toString() ?: "") }
    val payment = expenses.firstOrNull { it.active && it.visitId == first?.visitId && first?.visitId != null }
    var paid by rememberSaveable { mutableStateOf(moneyInput(payment?.cents)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var cancel by rememberSaveable { mutableStateOf(false) }
    val canonicalPayment = "${payment?.id}/${payment?.cents}"
    var loadedPayment by rememberSaveable { mutableStateOf(canonicalPayment) }
    LaunchedEffect(canonicalPayment) {
        if (canonicalPayment != loadedPayment) { paid = moneyInput(payment?.cents); loadedPayment = canonicalPayment }
    }
    fun values(): List<TyreEvent> {
        val actualKm = requireNotNull(parseKm(km)) { "Introduce los kilómetros reales." }
        val actualDate = date.takeIf { it.isNotBlank() }?.let(::validateDate)
        require(actualDate == null || LocalDate.parse(actualDate) <= LocalDate.parse(model.today())) { "El registro de ruedas no puede tener fecha futura." }
        val amount = if (kind == "mount" || historical) null else parseMoney(paid, true)
        require(amount == null || actualDate != null || payment != null) { "Introduce una fecha real para registrar un nuevo pago." }
        val savedIds = ids.split("|")
        return axes.mapIndexed { index, axis ->
            val interval = requireNotNull((if (axis == "front") frontInterval else rearInterval).toIntOrNull()?.takeIf { it > 0 }) { "El intervalo de aviso debe ser mayor que cero para cada eje." }
            val base = originals.firstOrNull { it.axis == axis } ?: TyreEvent(id = savedIds[index], axis = axis, kind = kind, km = actualKm, intervalKm = interval)
            base.copy(km = actualKm, intervalKm = interval, date = actualDate, notes = notes.trim(), active = true)
        }
    }
    Page {
        Heading(if (axes.size == 2) if (kind == "review") "Revisar ambas" else "Cambiar ambas" else "${axisLabel(axes.first())} · ${tyreKindLabel(kind)}")
        Field(km, { km = it }, "Kilómetros reales", "tyre_km", numeric = true)
        Field(date, { date = it }, "Fecha (AAAA-MM-DD, si se conoce)", "tyre_date")
        if ("front" in axes) Field(frontInterval, { frontInterval = it }, "Aviso de delanteras tras kilómetros", "tyre_interval", numeric = true)
        if ("rear" in axes) Field(rearInterval, { rearInterval = it }, "Aviso de traseras tras kilómetros", if (axes.size == 1) "tyre_interval" else "tyre_interval_rear", numeric = true)
        Field(notes, { notes = it }, "Nota (opcional)", "tyre_notes", multiline = true)
        Hint(if (kind == "review") "Revisar conserva el montaje y el uso del juego. Solo cambia el próximo aviso desde estos kilómetros." else if (kind == "mount") "Referencia del juego actual. No crea un cambio histórico ni un gasto." else "El juego anterior se conserva en el historial. Esta operación no modifica el odómetro.")
        if (originals.isNotEmpty()) Hint("Corregir conserva la posición de esta operación en el historial; no sustituye un juego posterior.")
        if (originals.any { !it.active }) Hint("Operación anulada; se conserva como historial.")
        if (historical) Hint("Este histórico no cambia el seguimiento actual")
        if (kind != "mount" && !historical) {
            Field(paid, { paid = it }, "Importe real total EUR (opcional)", "tyre_paid", numeric = true)
            if (axes.size == 2) Hint("Ambos ejes compartirán un solo trabajo y un solo gasto.")
        }
        payment?.let { if (historical) Hint("Gasto relacionado: ${money(it.cents)}") else OutlinedButton(onClick = { expense(it.id) }, modifier = Modifier.fillMaxWidth()) { Text("Consultar gasto vinculado") } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { runCatching { values() }.onSuccess { error = null; confirm = true }.onFailure { error = it.message } }, enabled = !busy && originals.none { !it.active }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ruedas") }
        if (originals.any { it.active }) OutlinedButton(onClick = { cancel = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Anular operación de ruedas") }
    }
    if (confirm) Confirm("Confirmar registro de ruedas", "${axes.joinToString(" y ") { axisLabel(it) }}\n${tyreKindLabel(kind)} · ${kmLabel(parseKm(km))}\nNo se actualizará el odómetro.", "Guardar", { confirm = false }) {
        confirm = false; model.saveTyres(values(), if (kind == "mount" || historical) null else parseMoney(paid, true), after)
    }
    if (cancel) Confirm("Anular operación de ruedas", if (historical) "Se anulará este histórico incorporado. Se conservarán el seguimiento local, el trabajo y el gasto relacionados." else if (originals.size > 1) "Se anularán expresamente ambos ejes de esta operación conjunta, su trabajo y su único gasto." else "Se retirará este registro del cálculo y se conservará la auditoría.", "Anular", { cancel = false }) {
        cancel = false
        if (historical) model.voidHistoricalTyres(originals.map { it.id }, after)
        else if (originals.size > 1 && first?.visitId != null) model.voidVisit(first.visitId, after) else originals.firstOrNull()?.let { model.voidTyre(it.id, after) }
    }
}
