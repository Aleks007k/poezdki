package io.github.aleks007k.poezdki

import java.math.BigDecimal
import java.math.RoundingMode

/** Числа с десятичной запятой. Слова («км», «€») — в strings.xml. */
object Format {
    /** 87 км → "87"; меньше 10 км — с одной цифрой после запятой: "8,4". */
    fun km(meters: Double): String {
        val km = meters / 1000.0
        return if (km < 10) decimal(km, 1) else decimal(km, 0)
    }

    /** "11,20" */
    fun money(euro: Double): String = decimal(euro, 2)

    /** Часы и минуты (округление до минуты). */
    fun hoursMinutes(millis: Long): Pair<Int, Int> {
        val totalMin = ((millis + 30_000) / 60_000).toInt().coerceAtLeast(0)
        return totalMin / 60 to totalMin % 60
    }

    fun decimal(value: Double, digits: Int): String =
        BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    /** Разобрать то, что ввёл человек: "6,5" и "6.5" — одно и то же. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Для поля ввода: 6.5 → "6,5", 7.0 → "7". */
    fun forInput(value: Double): String =
        BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            .toPlainString().replace('.', ',')

    /** Стоимость = км × расход / 100 × цена литра. */
    fun cost(meters: Double, litersPer100: Double, pricePerLiter: Double): Double =
        meters / 1000.0 * litersPer100 / 100.0 * pricePerLiter
}
