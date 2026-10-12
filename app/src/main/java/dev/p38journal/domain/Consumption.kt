package dev.p38journal.domain

import dev.p38journal.data.FuelEntry
import java.math.BigDecimal
import java.math.RoundingMode

object DecimalInput {
    fun parseLitresMilli(text: String): Long {
        val normalized = text.trim().replace(',', '.')
        require(normalized.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) { "Indica litros con hasta tres decimales" }
        val value = BigDecimal(normalized).movePointRight(3).longValueExact()
        require(value > 0 && value <= 1_000_000_000L) { "Cantidad de litros no válida" }
        return value
    }
}

data class FuelInterval(val startDate: String, val endDate: String, val startKm: Int, val endKm: Int, val litresMilli: Long) {
    val distanceKm: Long get() = endKm.toLong() - startKm
    val consumption: BigDecimal get() = rate(litresMilli, distanceKm)
}

data class ConsumptionSummary(val average: BigDecimal?, val totalLitresMilli: Long, val totalKm: Long,
    val intervals: List<FuelInterval>, val pendingCount: Int, val invalidIntervals: Int = 0)

private fun rate(litresMilli: Long, km: Long): BigDecimal = BigDecimal.valueOf(litresMilli)
    .divide(BigDecimal.valueOf(km).multiply(BigDecimal.TEN), 2, RoundingMode.HALF_UP)

object Consumption {
    fun calculate(entries: List<FuelEntry>): ConsumptionSummary {
        val active = entries.filter { it.active }.sortedWith(compareBy<FuelEntry> { it.date }
            .thenBy { it.km ?: Int.MAX_VALUE }.thenBy { it.full }.thenBy { it.id })
        val unknownKilometreDates = active.filter { it.km == null }.map { it.date }.sorted()
        val intervals = mutableListOf<FuelInterval>()
        var reference: FuelEntry? = null
        var litres = 0L
        var invalid = false
        var lastKm: Int? = null
        var pending = 0
        var invalidIntervals = 0
        active.forEach { entry ->
            val start = reference
            if (start != null) {
                if (entry.litresMilli == null || entry.litresMilli <= 0 || entry.litresMilli > 1_000_000_000L ||
                    entry.km == null || entry.km < 0 || lastKm?.let { entry.km < it } == true) invalid = true
                entry.litresMilli?.takeIf { it > 0 && it <= 1_000_000_000L }?.let {
                    litres = runCatching { Math.addExact(litres, it) }.getOrElse { invalid = true; litres }
                }
                if (entry.full) {
                    val from = start.km
                    val to = entry.km
                    val position = unknownKilometreDates.binarySearch(start.date).let { if (it < 0) -it - 1 else it }
                    val ambiguous = unknownKilometreDates.getOrNull(position)?.let { it <= entry.date } == true
                    if (!invalid && !ambiguous && from != null && to != null && to > from && litres > 0) {
                        intervals += FuelInterval(start.date, entry.date, from, to, litres)
                    } else invalidIntervals++
                }
            }
            if (entry.full) {
                reference = entry
                litres = 0
                invalid = entry.km == null || entry.km < 0
                lastKm = entry.km
                pending = 0
            } else {
                pending++
                entry.km?.let { lastKm = it }
            }
        }
        val totalKm = intervals.sumOf { it.distanceKm }
        val totalLitres = intervals.sumOf { it.litresMilli }
        return ConsumptionSummary(if (totalKm > 0) rate(totalLitres, totalKm) else null,
            totalLitres, totalKm, intervals, pending, invalidIntervals)
    }
}
