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
    /** Расход за городом (или единственный, если городской не задан). */
    val litersPer100: Double = 0.0,
    /** Расход в городе. 0 — такой же, как за городом. */
    val litersCity: Double = 0.0,
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
    /**
     * Расход для поездки по средней скорости в движении: до 40 км/ч — город,
     * от 80 км/ч — трасса, между — плавно.
     */
    fun litersFor(movingSpeedKmh: Double): Double {
        if (litersCity <= 0) return litersPer100
        val k = ((movingSpeedKmh - Config.CITY_SPEED_KMH) / (Config.HIGHWAY_SPEED_KMH - Config.CITY_SPEED_KMH))
            .coerceIn(0.0, 1.0)
        return litersCity + (litersPer100 - litersCity) * k
    }

    /** Для «каждый км стоит»: средний между городом и трассой. */
    private val avgLiters get() = if (litersCity > 0) (litersCity + litersPer100) / 2 else litersPer100
    val fuelPerKm get() = avgLiters / 100.0 * pricePerLiter

    val wearSet get() = carValue > sellPrice && years > 0 && kmPerYear > 0
    val wearPerYear get() = if (wearSet) (carValue - sellPrice) / years else 0.0
    val wearPerKm get() = if (wearSet) wearPerYear / kmPerYear else 0.0

    val upkeepPerYear get() = insurance + tax + service + tires
    val upkeepSet get() = upkeepPerYear > 0 && kmPerYear > 0
    val upkeepPerKm get() = if (upkeepSet) upkeepPerYear / kmPerYear else 0.0

    val totalPerKm get() = fuelPerKm + wearPerKm + upkeepPerKm
    val effectiveFarePerKm get() = farePerKm ?: totalPerKm

    fun liters(meters: Double, movingSpeedKmh: Double = Config.HIGHWAY_SPEED_KMH) =
        meters / 1000.0 * litersFor(movingSpeedKmh) / 100.0
    fun fuel(meters: Double, movingSpeedKmh: Double = Config.HIGHWAY_SPEED_KMH) =
        liters(meters, movingSpeedKmh) * pricePerLiter
    fun wear(meters: Double) = meters / 1000.0 * wearPerKm
    fun upkeep(meters: Double) = meters / 1000.0 * upkeepPerKm
    fun total(meters: Double, movingSpeedKmh: Double = Config.HIGHWAY_SPEED_KMH) =
        fuel(meters, movingSpeedKmh) + wear(meters) + upkeep(meters)

    /** Как в такси: посадка + км × тариф + минуты × тариф. */
    fun fare(meters: Double, millis: Long) =
        fareBase + meters / 1000.0 * effectiveFarePerKm + millis / 60_000.0 * farePerMinute

    /** В строку для базы: «ключ=значение;…». */
    fun encode(): String = listOf(
        "l100" to litersPer100, "l100city" to litersCity, "price" to pricePerLiter,
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
                litersPer100 = v("l100"), litersCity = v("l100city"), pricePerLiter = v("price"),
                carValue = v("value"), sellPrice = v("sell"), years = v("years"), kmPerYear = v("kmYear"),
                insurance = v("ins"), tax = v("tax"), service = v("service"), tires = v("tires"),
                fareBase = v("fareBase"), farePerKm = m["fareKm"], farePerMinute = v("fareMin"),
            )
        }
    }
}
