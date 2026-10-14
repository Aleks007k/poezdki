package io.github.aleks007k.poezdki

/**
 * Всё, что машина стоит, — по схеме автоклубов: топливо, износ (машина
 * дешевеет), содержание за год (страховка, налог, ТО и ремонт, шины).
 * Плюс тариф для поездок за деньги. Пустое поле = 0 = не считается.
 *
 * Копия этих цифр запоминается в каждой поездке: изменения действуют
 * только на новые поездки.
 */
data class CarCosts(
    val litersPer100: Double = 0.0,
    val pricePerLiter: Double = 0.0,
    val carValue: Double = 0.0,
    val sellPrice: Double = 0.0,
    val years: Double = 0.0,
    val kmPerYear: Double = 0.0,
    val insurance: Double = 0.0,
    val tax: Double = 0.0,
    val service: Double = 0.0,
    val tires: Double = 0.0,
    val fareBase: Double = 0.0,
    /** null — «сколько км стоит мне». */
    val farePerKm: Double? = null,
    val farePerMinute: Double = 0.0,
) {
    val fuelPerKm get() = litersPer100 / 100.0 * pricePerLiter

    val wearSet get() = carValue > sellPrice && years > 0 && kmPerYear > 0
    val wearPerYear get() = if (wearSet) (carValue - sellPrice) / years else 0.0
    val wearPerKm get() = if (wearSet) wearPerYear / kmPerYear else 0.0

    val upkeepPerYear get() = insurance + tax + service + tires
    val upkeepSet get() = upkeepPerYear > 0 && kmPerYear > 0
    val upkeepPerKm get() = if (upkeepSet) upkeepPerYear / kmPerYear else 0.0

    val totalPerKm get() = fuelPerKm + wearPerKm + upkeepPerKm
    val effectiveFarePerKm get() = farePerKm ?: totalPerKm

    fun liters(meters: Double) = meters / 1000.0 * litersPer100 / 100.0
    fun fuel(meters: Double) = meters / 1000.0 * fuelPerKm
    fun wear(meters: Double) = meters / 1000.0 * wearPerKm
    fun upkeep(meters: Double) = meters / 1000.0 * upkeepPerKm
    fun total(meters: Double) = fuel(meters) + wear(meters) + upkeep(meters)

    /** Как в такси: посадка + км × тариф + минуты × тариф. */
    fun fare(meters: Double, millis: Long) =
        fareBase + meters / 1000.0 * effectiveFarePerKm + millis / 60_000.0 * farePerMinute

    /** В строку для базы: «ключ=значение;…». */
    fun encode(): String = listOf(
        "l100" to litersPer100, "price" to pricePerLiter,
        "value" to carValue, "sell" to sellPrice, "years" to years, "kmYear" to kmPerYear,
        "ins" to insurance, "tax" to tax, "service" to service, "tires" to tires,
        "fareBase" to fareBase, "fareKm" to farePerKm, "fareMin" to farePerMinute,
    ).filter { it.second != null }.joinToString(";") { "${it.first}=${it.second}" }

    companion object {
        fun decode(s: String?): CarCosts? {
            if (s.isNullOrBlank()) return null
            val m = s.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1).toDoubleOrNull()
            }.toMap()
            fun v(k: String) = m[k] ?: 0.0
            return CarCosts(
                litersPer100 = v("l100"), pricePerLiter = v("price"),
                carValue = v("value"), sellPrice = v("sell"), years = v("years"), kmPerYear = v("kmYear"),
                insurance = v("ins"), tax = v("tax"), service = v("service"), tires = v("tires"),
                fareBase = v("fareBase"), farePerKm = m["fareKm"], farePerMinute = v("fareMin"),
            )
        }
    }
}
