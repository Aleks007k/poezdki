package io.github.aleks007k.poezdki

/** Заправка. Сумма — в валюте чека; rate — сколько единиц этой валюты за 1 €. */
data class Refuel(
    val id: Long,
    val time: Long,
    val liters: Double,
    val total: Double,
    val currency: String,
    val rate: Double,
    val odometer: Double?,
    val full: Boolean,
    val photo: String?,
) {
    val totalEur get() = if (rate > 0) total / rate else total
    val pricePerLiter get() = if (liters > 0) total / liters else 0.0
    val priceEur get() = if (liters > 0) totalEur / liters else 0.0
}

object FuelMath {
    /**
     * Реальный расход, л/100 км, по полным бакам: между двумя «полными» заправками
     * всё залитое (включая неполные между ними) ÷ пробег по одометру × 100.
     * Берём последние до 3 таких промежутков. null — данных мало.
     */
    fun realConsumption(refuels: List<Refuel>): Double? {
        val withOdo = refuels.filter { it.odometer != null }.sortedBy { it.odometer }
        val fullIdx = withOdo.indices.filter { withOdo[it].full }
        if (fullIdx.size < 2) return null
        var liters = 0.0
        var km = 0.0
        for (k in fullIdx.size - 1 downTo maxOf(1, fullIdx.size - 3)) {
            val from = fullIdx[k - 1]
            val to = fullIdx[k]
            val dist = withOdo[to].odometer!! - withOdo[from].odometer!!
            if (dist <= 0) continue
            liters += (from + 1..to).sumOf { withOdo[it].liters }
            km += dist
        }
        if (km < 50) return null
        return liters / km * 100
    }
}

/** Черновик заправки в форме: всё строками, как ввёл человек. */
data class RefuelDraft(
    val id: Long = 0,
    val date: java.time.LocalDate,
    val liters: String = "",
    val price: String = "",
    val total: String = "",
    val currency: String = "EUR",
    val rate: String = "",
    val odometer: String = "",
    val full: Boolean = true,
    val photo: String? = null,
    val scanning: Boolean = false,
    /** Фото прочитали, но цифр не нашли — подсказать вписать руками. */
    val scanEmpty: Boolean = false,
) {
    /**
     * Готовая заправка или null, если цифр не хватает. Из трёх чисел (литры, цена, сумма)
     * достаточно двух — третье посчитаем.
     */
    fun toRefuel(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Refuel? {
        val l = Format.parse(liters)
        val p = Format.parse(price)
        val t = Format.parse(total)
        val litersV = l ?: if (t != null && p != null) t / p else null
        val totalV = t ?: if (litersV != null && p != null) litersV * p else null
        if (litersV == null || totalV == null || litersV <= 0 || totalV <= 0) return null
        val rateV = if (currency == "EUR") 1.0 else Format.parse(rate) ?: return null
        return Refuel(
            id = id,
            time = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            liters = litersV,
            total = totalV,
            currency = currency,
            rate = rateV,
            odometer = Format.parse(odometer),
            full = full,
            photo = photo,
        )
    }

    /** Дописать то, что прочитали с чека, — не трогая уже введённое. */
    fun merge(r: ParsedReceipt, rates: Map<String, Double>): RefuelDraft {
        fun fill(old: String, v: Double?, digits: Int) = if (old.isBlank() && v != null) Format.number(v, digits) else old
        val cur = r.currency ?: currency
        return copy(
            date = r.date ?: date,
            liters = fill(liters, r.liters, 2),
            price = fill(price, r.pricePerLiter, 3),
            total = fill(total, r.total, 2),
            currency = cur,
            rate = if (cur != currency || rate.isBlank()) rates[cur]?.let { Format.number(it, 4) } ?: rate else rate,
            scanning = false,
            scanEmpty = r.total == null && r.liters == null,
        )
    }

    companion object {
        fun of(r: Refuel, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) = RefuelDraft(
            id = r.id,
            date = java.time.Instant.ofEpochMilli(r.time).atZone(zone).toLocalDate(),
            liters = Format.number(r.liters, 2),
            price = Format.number(r.pricePerLiter, 3),
            total = Format.number(r.total, 2),
            currency = r.currency,
            rate = if (r.currency == "EUR") "" else Format.number(r.rate, 4),
            odometer = r.odometer?.let { Format.number(it, 0) }.orEmpty(),
            full = r.full,
            photo = r.photo,
        )
    }
}
