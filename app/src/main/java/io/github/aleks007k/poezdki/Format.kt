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

    /** Разобрать то, что ввёл человек: "6,5" и "6.5" — одно и то же; пробелы не мешают. */
    fun parse(text: String): Double? =
        text.filterNot { it.isWhitespace() || it == '\u00A0' }.replace(',', '.')
            .toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Для необязательных полей: пусто или ерунда — 0. */
    fun parseOrZero(text: String): Double = parse(text) ?: 0.0

    /**
     * Число для объяснений: 18000 → "18 000", 0.13333 → "0,133" (maxDigits = 3),
     * 7.0 → "7". Лишние нули после запятой убираются.
     */
    fun number(value: Double, maxDigits: Int): String {
        val bd = BigDecimal(value).setScale(maxDigits, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = bd.toPlainString()
        val negative = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val intPart = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString("\u00A0").reversed()
        return (if (negative) "-" else "") + grouped + (if (frac.isNotEmpty()) ",$frac" else "")
    }

    /** Скорость: 52.4 → "52". */
    fun speed(kmh: Double): String = decimal(kmh, 0)

    /** Для поля ввода: 6.5 → "6,5", 7.0 → "7". */
    fun forInput(value: Double): String =
        BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
            .toPlainString().replace('.', ',')
}
