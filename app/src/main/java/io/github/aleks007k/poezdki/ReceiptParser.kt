package io.github.aleks007k.poezdki

import java.time.LocalDate
import kotlin.math.abs

/** Что удалось прочитать с чека. Любое поле может быть null — тогда его впишешь сам. */
data class ParsedReceipt(
    val date: LocalDate? = null,
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val total: Double? = null,
    val currency: String? = null,
)

/**
 * Разбор чека с заправки по ЦИФРАМ, а не по словам — работает на любом языке:
 * ищем три числа, где литры × цена за литр = сумма (42,30 × 1,849 = 78,21).
 */
object ReceiptParser {
    private data class Num(val value: Double, val decimals: Int, val line: Int, val col: Int)

    private val numberRe = Regex("""(?<![\d.,])(\d{1,3}(?:[ .]\d{3})+|\d+)([.,](\d{1,3}))?(?![\d])""")
    private val dateRes = listOf(
        Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{4})(?!\d)""") to "dmy4",
        Regex("""(?<!\d)(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?!\d)""") to "ymd",
        Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{2})(?!\d)""") to "dmy2",
    )
    private val litersWord = Regex("""(?i)(?<![a-z])(l|ltr|liter|litre|litr|lt)(?![a-z])""")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedReceipt {
        val lines = text.lines()
        val date = findDate(lines, today)
        // Числа без дат и времени (12:34), чтобы 14.10.2026 не стало «14,10».
        val nums = mutableListOf<Num>()
        lines.forEachIndexed { i, raw ->
            var line = raw
            dateRes.forEach { (re, _) -> line = re.replace(line, " ") }
            line = Regex("""\d{1,2}:\d{2}(:\d{2})?""").replace(line, " ")
            numberRe.findAll(line).forEach { m ->
                val intPart = m.groupValues[1].replace(" ", "").replace(".", "")
                val frac = m.groupValues[3]
                val v = (intPart + if (frac.isNotEmpty()) ".$frac" else "").toDoubleOrNull()
                if (v != null && v > 0) nums += Num(v, frac.length, i, m.range.first)
            }
        }
        val triple = findTriple(nums, lines)
        val total = triple?.third ?: fallbackTotal(nums)
        return ParsedReceipt(
            date = date,
            liters = triple?.first,
            pricePerLiter = triple?.second,
            total = total,
            currency = Currencies.detect(text),
        )
    }

    private fun findDate(lines: List<String>, today: LocalDate): LocalDate? {
        for (line in lines) for ((re, kind) in dateRes) {
            val m = re.find(line) ?: continue
            val g = m.groupValues
            val d = try {
                when (kind) {
                    "dmy4" -> LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
                    "ymd" -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                    else -> LocalDate.of(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt())
                }
            } catch (e: Exception) {
                null
            } ?: continue
            // Чек не из будущего и не старше 2 лет.
            if (!d.isAfter(today) && d.isAfter(today.minusYears(2))) return d
        }
        return null
    }

    /** литры, цена, сумма — где литры × цена ≈ сумма (с точностью до копейки округления). */
    private fun findTriple(nums: List<Num>, lines: List<String>): Triple<Double, Double, Double>? {
        val withDecimals = nums.filter { it.decimals > 0 }
        var best: Triple<Double, Double, Double>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (a in withDecimals) for (b in withDecimals) {
            if (a === b) continue
            val product = a.value * b.value
            for (c in withDecimals) {
                if (c === a || c === b || c.decimals != 2) continue
                val tolerance = maxOf(0.011, c.value * 0.002)
                if (abs(product - c.value) > tolerance) continue
                // a — литры, b — цена. Литры от 1 до 200; на чеке «литры × цена» — литры пишут первыми.
                if (a.value !in 1.0..200.0) continue
                var score = c.value // предпочитаем крупную сумму (итог, а не скидку)
                if (a.line == b.line && a.col < b.col) score += 1_000
                if (litersWord.containsMatchIn(lines[a.line])) score += 300
                if (b.decimals == 3 && b.value < 10) score += 200 // цена вроде 1,849 €/л
                if (score > bestScore) {
                    bestScore = score
                    best = Triple(a.value, b.value, c.value)
                }
            }
        }
        return best
    }

    /** Тройку не нашли — суммой считаем самое крупное число с двумя знаками после запятой. */
    private fun fallbackTotal(nums: List<Num>) = nums.filter { it.decimals == 2 }.maxOfOrNull { it.value }
}
