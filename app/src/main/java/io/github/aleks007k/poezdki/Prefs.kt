package io.github.aleks007k.poezdki

import android.content.Context

/** Мелкие настройки: расход, цена, шаги первого запуска, текущая поездка. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("poezdki", Context.MODE_PRIVATE)

    var litersPer100: Double
        get() = p.getString("l100", null)?.toDoubleOrNull() ?: 0.0
        set(v) = p.edit().putString("l100", v.toString()).apply()

    var pricePerLiter: Double
        get() = p.getString("price", null)?.toDoubleOrNull() ?: 0.0
        set(v) = p.edit().putString("price", v.toString()).apply()

    val hasFuelSettings get() = litersPer100 > 0 && pricePerLiter > 0

    /** Шаги первого запуска, которые уже показывали (чтобы не спрашивать по кругу). */
    fun wasAsked(step: String) = p.getBoolean("asked_$step", false)
    fun markAsked(step: String) = p.edit().putBoolean("asked_$step", true).apply()

    // --- Текущая поездка (переживает перезапуск приложения) ---

    val activeStart: Long get() = p.getLong("trip_start", 0L)
    val hasActiveTrip get() = activeStart > 0

    val activeDestination: String get() = p.getString("trip_dest", "") ?: ""

    val activeTarget: LatLon?
        get() = if (p.contains("trip_lat")) {
            LatLon(
                java.lang.Double.longBitsToDouble(p.getLong("trip_lat", 0)),
                java.lang.Double.longBitsToDouble(p.getLong("trip_lon", 0)),
            )
        } else null

    fun startTrip(start: Long, destination: String, target: LatLon?) {
        p.edit().putLong("trip_start", start).putString("trip_dest", destination)
            .remove("trip_lat").remove("trip_lon").commit()
        target?.let { setTarget(it) }
    }

    fun setTarget(t: LatLon) {
        p.edit().putLong("trip_lat", java.lang.Double.doubleToRawLongBits(t.lat))
            .putLong("trip_lon", java.lang.Double.doubleToRawLongBits(t.lon)).commit()
    }

    fun clearTrip() {
        p.edit().remove("trip_start").remove("trip_dest").remove("trip_lat").remove("trip_lon").commit()
    }
}
