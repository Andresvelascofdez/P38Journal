package dev.p38journal.domain

import dev.p38journal.data.FuelEntry
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ConsumptionTest {
    private fun fill(id: String, km: Int?, litres: Long?, full: Boolean, date: String = "2026-10-12", active: Boolean = true) =
        FuelEntry(id = id, date = date, km = km, litresMilli = litres, totalCents = 1000, full = full, expenseId = null, active = active)

    @Test fun firstFullIsReferencePartialAndFinalSumToFourteen() {
        val result = Consumption.calculate(listOf(fill("a",175000,999000,true), fill("b",175200,20000,false), fill("c",175500,50000,true)))
        assertEquals(0, BigDecimal("14").compareTo(result.average))
        assertEquals(70000, result.totalLitresMilli)
        assertEquals(500, result.totalKm)
        assertEquals(1, result.intervals.size)
        assertEquals(0, result.pendingCount)
    }

    @Test fun aggregateIsWeightedByDistanceAndPendingPartialDoesNotEnterIt() {
        val result = Consumption.calculate(listOf(fill("a",175000,70000,true), fill("b",175500,70000,true),
            fill("c",176500,60000,true), fill("d",176600,20000,false)))
        assertEquals(BigDecimal("8.67"), result.average)
        assertEquals(1500, result.totalKm)
        assertEquals(130000, result.totalLitresMilli)
        assertEquals(1, result.pendingCount)
        assertEquals(listOf(BigDecimal("14.00"),BigDecimal("6.00")), result.intervals.map { it.consumption })
    }

    @Test fun editsAndCancellationRecalculateWithoutIncludingOldReferenceLitres() {
        val a=fill("a",175000,70000,true)
        val partial=fill("b",175200,20000,false)
        val end=fill("c",175500,50000,true)
        assertEquals(BigDecimal("16.00"), Consumption.calculate(listOf(a,partial.copy(litresMilli=30000),end)).average)
        assertNull(Consumption.calculate(listOf(a,partial,end.copy(active=false))).average)
        assertEquals(1, Consumption.calculate(listOf(a,partial,end.copy(active=false))).pendingCount)
        assertEquals(BigDecimal("14.00"), Consumption.calculate(listOf(a.copy(litresMilli=null),partial,end)).average)
    }

    @Test fun zeroNegativeAndIncompleteDistancesNeverProduceConsumption() {
        val a=fill("a",175000,70000,true,"2026-10-01")
        assertNull(Consumption.calculate(listOf(a,fill("b",175000,50000,true))).average)
        assertNull(Consumption.calculate(listOf(a,fill("b",174999,50000,true))).average)
        assertNull(Consumption.calculate(listOf(a,fill("b",null,50000,true))).average)
        assertNull(Consumption.calculate(listOf(a,fill("b",175200,null,false),fill("c",175500,50000,true))).average)
        assertNull(Consumption.calculate(listOf(fill("a",null,70000,true),fill("b",175500,50000,true))).average)
    }

    @Test fun invalidClosedIntervalDoesNotPoisonTheNextValidReference() {
        val result=Consumption.calculate(listOf(fill("a",175000,70000,true),fill("b",175200,null,false),
            fill("c",175500,50000,true),fill("d",176000,50000,true)))
        assertEquals(BigDecimal("10.00"), result.average)
        assertEquals(1,result.invalidIntervals)
        assertEquals(500,result.totalKm)
    }

    @Test fun incompletePartialOnClosingDayIsNotSilentlyOmitted() {
        assertNull(Consumption.calculate(listOf(fill("a",175000,70000,true),fill("b",null,20000,false),fill("c",175500,50000,true))).average)
    }

    @Test fun noFullOrOnlyOneFullIsInsufficient() {
        assertNull(Consumption.calculate(emptyList()).average)
        assertNull(Consumption.calculate(listOf(fill("a",175000,70000,true))).average)
        assertNull(Consumption.calculate(listOf(fill("a",175000,20000,false),fill("b",175200,20000,false))).average)
    }

    @Test fun commaAndPointDecimalLitresAreExactAndOverprecisionRejected() {
        assertEquals(20125, DecimalInput.parseLitresMilli("20,125"))
        assertEquals(20125, DecimalInput.parseLitresMilli("20.125"))
        assertThrows(IllegalArgumentException::class.java) { DecimalInput.parseLitresMilli("20.1234") }
        assertThrows(IllegalArgumentException::class.java) { DecimalInput.parseLitresMilli("1,234.5") }
        assertThrows(IllegalArgumentException::class.java) { DecimalInput.parseLitresMilli("0") }
    }
}
