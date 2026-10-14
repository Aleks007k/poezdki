package io.github.aleks007k.poezdki

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Расстояние по поверхности Земли, в метрах. */
fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

fun distanceM(a: LatLon, b: LatLon) = distanceM(a.lat, a.lon, b.lat, b.lon)
