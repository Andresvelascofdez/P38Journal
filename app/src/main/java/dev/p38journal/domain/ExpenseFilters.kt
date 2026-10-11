package dev.p38journal.domain

import java.time.LocalDate

data class ExpenseFilter(val month: Int? = null, val year: Int? = null, val category: String? = null) {
    init {
        require(month == null || month in 1..12)
        require(year == null || year in 1..9999)
    }

    fun matches(date: String, category: String): Boolean {
        val day = LocalDate.parse(date)
        return (month == null || day.monthValue == month) &&
            (year == null || day.year == year) && (this.category == null || this.category == category)
    }
}

data class ExpenseFilterResult<T>(val rows: List<T>, val subtotalCents: Long, val totalCents: Long, val years: List<Int>)

object ExpenseFilters {
    fun <T> apply(
        items: List<T>, filter: ExpenseFilter, date: (T) -> String,
        cents: (T) -> Long, category: (T) -> String, active: (T) -> Boolean = { true }
    ): ExpenseFilterResult<T> {
        val all = items.filter(active)
        val rows = all.filter { filter.matches(date(it), category(it)) }
        fun total(values: List<T>) = values.fold(0L) { sum, row -> Math.addExact(sum, cents(row)) }
        return ExpenseFilterResult(rows, total(rows), total(all),
            all.map { LocalDate.parse(date(it)).year }.distinct().sortedDescending())
    }
}
