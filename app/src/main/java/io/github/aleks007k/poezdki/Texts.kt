package io.github.aleks007k.poezdki

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Название места: «Дом», «Работа» или своё. */
fun placeName(context: Context, id: Int, place: Place?): String = when (id) {
    1 -> context.getString(R.string.home)
    2 -> context.getString(R.string.work)
    else -> place?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.custom_place)
}

/** Точка ближе 200 м к адресу места — его название, иначе улица и дом. */
fun nearPlace(at: LatLon?, places: Map<Int, Place>): Place? = at?.let { p ->
    places.values
        .filter { it.coords != null && !it.address.isNullOrBlank() }
        .minByOrNull { distanceM(p, it.coords!!) }
        ?.takeIf { distanceM(p, it.coords!!) <= Config.PLACE_RADIUS_M }
}

fun pointLabel(context: Context, at: LatLon?, address: String?, places: Map<Int, Place>): String =
    nearPlace(at, places)?.let { placeName(context, it.id, it) }
        ?: address ?: context.getString(R.string.unknown_place)

fun routeText(context: Context, t: Trip, places: Map<Int, Place>) = context.getString(
    R.string.route,
    pointLabel(context, t.start, t.startAddress, places),
    pointLabel(context, t.end, t.endAddress, places),
)

fun durationText(context: Context, millis: Long): String {
    val (h, m) = Format.hoursMinutes(millis)
    return if (h > 0) context.getString(R.string.duration_hm, h, m) else context.getString(R.string.duration_m, m)
}

fun minutesText(context: Context, millis: Long): String =
    context.getString(R.string.duration_m, ((millis + 30_000) / 60_000).toInt())

fun kmText(context: Context, meters: Double) = context.getString(R.string.km_value, Format.km(meters))
fun moneyText(context: Context, euro: Double) = context.getString(R.string.money_value, Format.money(euro))
fun litersText(context: Context, liters: Double) = context.getString(R.string.liters_value, Format.decimal(liters, 1))
fun speedText(context: Context, kmh: Double) = context.getString(R.string.speed_value, Format.speed(kmh))

private val zone get() = ZoneId.systemDefault()
fun timeOf(millis: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))
fun shortDate(millis: Long): String =
    DateTimeFormatter.ofPattern("EE, d MMM").format(Instant.ofEpochMilli(millis).atZone(zone)).replaceFirstChar { it.uppercase() }
fun longDate(millis: Long): String =
    DateTimeFormatter.ofPattern("EE, d MMMM").format(Instant.ofEpochMilli(millis).atZone(zone)).replaceFirstChar { it.uppercase() }

// ---------- «Как посчитано» ----------

enum class CostKind { FUEL, WEAR, UPKEEP, FARE }

/** Шаг расчёта: подпись, формула словами, та же формула с цифрами. */
data class Step(val caption: String, val words: String, val numbers: String)

/** Откуда цифра: «18 000 €» «стоит сейчас» «ты ввёл · Машина». */
data class Source(val value: String, val label: String, val origin: String)

data class Explanation(
    val title: String,
    val amount: String,
    val color: Color,
    val steps: List<Step>,
    val sources: List<Source>,
    val notSet: Boolean,
)

fun explain(context: Context, t: Trip, kind: CostKind): Explanation {
    val c = t.costs
    fun s(id: Int) = context.getString(id)
    fun n(v: Double, digits: Int = 2) = Format.number(v, digits)
    val km = t.distanceM / 1000.0
    val kmN = n(km, 1)
    val you = s(R.string.src_you)
    val gps = s(R.string.src_gps)
    val tripKm = Source(kmN, s(R.string.src_trip_km), gps)
    return when (kind) {
        CostKind.FUEL -> Explanation(
            s(R.string.cost_fuel), moneyText(context, t.fuel), C.Amber,
            listOf(
                Step(s(R.string.exp_fuel_step1), s(R.string.exp_fuel_f1), "$kmN × ${n(c.litersPer100)} ÷ 100 = ${n(t.liters)} л"),
                Step(s(R.string.exp_fuel_step2), s(R.string.exp_fuel_f2), "${n(t.liters)} × ${n(c.pricePerLiter, 3)} = ${Format.money(t.fuel)} €"),
            ),
            listOf(
                Source("${n(c.litersPer100)} л", s(R.string.src_l100), s(R.string.src_trip_day)),
                Source("${n(c.pricePerLiter, 3)} €", s(R.string.src_price), s(R.string.src_trip_day)),
                tripKm,
            ),
            notSet = false,
        )
        CostKind.WEAR -> Explanation(
            s(R.string.cost_wear), moneyText(context, t.wear), C.Blue,
            listOf(
                Step(s(R.string.exp_per_km_step), s(R.string.exp_wear_f1),
                    "(${n(c.carValue)} − ${n(c.sellPrice)}) ÷ ${n(c.years)} ÷ ${n(c.kmPerYear)} = ${n(c.wearPerKm, 3)} €"),
                Step(s(R.string.exp_trip_step), s(R.string.exp_trip_f), "$kmN × ${n(c.wearPerKm, 3)} = ${Format.money(t.wear)} €"),
            ),
            listOf(
                Source("${n(c.carValue)} €", s(R.string.src_value), you),
                Source("${n(c.sellPrice)} €", s(R.string.src_sell), you),
                Source(n(c.years), s(R.string.src_years), you),
                Source(n(c.kmPerYear), s(R.string.src_km_year), you),
                tripKm,
            ),
            notSet = !c.wearSet,
        )
        CostKind.UPKEEP -> Explanation(
            s(R.string.cost_upkeep), moneyText(context, t.upkeep), C.Teal,
            listOf(
                Step(s(R.string.exp_per_km_step), s(R.string.exp_upkeep_f1),
                    "(${n(c.insurance)} + ${n(c.tax)} + ${n(c.service)} + ${n(c.tires)}) ÷ ${n(c.kmPerYear)} = ${n(c.upkeepPerKm, 3)} €"),
                Step(s(R.string.exp_trip_step), s(R.string.exp_trip_f), "$kmN × ${n(c.upkeepPerKm, 3)} = ${Format.money(t.upkeep)} €"),
            ),
            listOf(
                Source("${n(c.insurance)} €", s(R.string.insurance).lowercase(), you),
                Source("${n(c.tax)} €", s(R.string.tax).lowercase(), you),
                Source("${n(c.service)} €", s(R.string.service), you),
                Source("${n(c.tires)} €", s(R.string.tires).lowercase(), you),
                Source(n(c.kmPerYear), s(R.string.src_km_year), you),
                tripKm,
            ),
            notSet = !c.upkeepSet,
        )
        CostKind.FARE -> {
            val minutes = t.durationMs / 60_000.0
            Explanation(
                s(R.string.to_pay), moneyText(context, t.fare), C.Amber,
                listOf(
                    Step(s(R.string.exp_fare_step), s(R.string.exp_fare_f),
                        "${n(c.fareBase)} + $kmN × ${n(c.effectiveFarePerKm, 3)} + ${n(minutes, 0)} × ${n(c.farePerMinute)} = ${Format.money(t.fare)} €"),
                ),
                listOf(
                    Source("${n(c.fareBase)} €", s(R.string.src_fare_base), you),
                    Source("${n(c.effectiveFarePerKm, 3)} €", s(R.string.src_fare_km),
                        if (c.farePerKm == null) s(R.string.src_own_cost) else you),
                    Source("${n(c.farePerMinute)} €", s(R.string.src_fare_min), you),
                    tripKm,
                    Source(n(minutes, 0), s(R.string.src_trip_min), gps),
                ),
                notSet = false,
            )
        }
    }
}
