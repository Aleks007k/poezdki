package io.github.aleks007k.poezdki

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Неделя — с понедельника по воскресенье, месяц — календарный. */
data class Period(val from: LocalDate, val toExclusive: LocalDate) {
    fun startMillis(zone: ZoneId) = from.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMillis(zone: ZoneId) = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** offset = 0 — текущая неделя, -1 — прошлая и т. д. */
        fun week(today: LocalDate, offset: Int): Period {
            val monday = today.with(DayOfWeek.MONDAY).plusWeeks(offset.toLong())
            return Period(monday, monday.plusWeeks(1))
        }

        fun month(today: LocalDate, offset: Int): Period {
            val ym = YearMonth.from(today).plusMonths(offset.toLong())
            return Period(ym.atDay(1), ym.plusMonths(1).atDay(1))
        }
    }
}
