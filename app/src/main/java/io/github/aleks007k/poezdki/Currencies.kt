package io.github.aleks007k.poezdki

/** Валюта: код, знак на экране и как её узнать на чеке (латиницей — кириллицу распознавание не читает). */
data class Currency(val code: String, val sign: String, val marks: List<String>)

object Currencies {
    val EUR = Currency("EUR", "€", listOf("EUR", "€"))

    /** Валюты Европы и соседей, где можно заправиться. */
    val all = listOf(
        EUR,
        Currency("PLN", "zł", listOf("PLN", "ZŁ", "ZL")),
        Currency("CZK", "Kč", listOf("CZK", "KČ", "KC")),
        Currency("HUF", "Ft", listOf("HUF", "FT")),
        Currency("CHF", "CHF", listOf("CHF", "FR.")),
        Currency("GBP", "£", listOf("GBP", "£")),
        Currency("DKK", "kr (DK)", listOf("DKK")),
        Currency("SEK", "kr (SE)", listOf("SEK")),
        Currency("NOK", "kr (NO)", listOf("NOK")),
        Currency("RON", "lei", listOf("RON", "LEI")),
        Currency("RSD", "din", listOf("RSD", "DIN")),
        Currency("BAM", "KM", listOf("BAM", "KM")),
        Currency("MKD", "den", listOf("MKD", "DEN")),
        Currency("ALL", "L (AL)", listOf("ALL", "LEK")),
        Currency("MDL", "lei (MD)", listOf("MDL")),
        Currency("UAH", "₴", listOf("UAH", "₴", "GRN")),
        Currency("TRY", "₺", listOf("TRY", "₺", "TL")),
        Currency("GEL", "₾", listOf("GEL", "₾")),
        Currency("ISK", "kr (IS)", listOf("ISK")),
    )

    fun byCode(code: String?) = all.firstOrNull { it.code == code } ?: EUR

    /** Какая валюта упомянута на чеке. null — не нашли (тогда берём последнюю использованную). */
    fun detect(text: String): String? {
        val up = text.uppercase()
        // Сначала коды из трёх букв и знаки — они однозначны; короткие («FT», «KM», «TL») — как отдельные слова.
        for (c in all) {
            for (m in c.marks) {
                val found = if (m.length >= 3 || !m.all { it.isLetter() }) {
                    if (m.all { it.isLetter() }) Regex("(?<![A-Z])${Regex.escape(m)}(?![A-Z])").containsMatchIn(up)
                    else up.contains(m)
                } else {
                    Regex("(?<![A-Z])${Regex.escape(m)}(?![A-Z])").containsMatchIn(up)
                }
                if (found) return c.code
            }
        }
        return null
    }
}
