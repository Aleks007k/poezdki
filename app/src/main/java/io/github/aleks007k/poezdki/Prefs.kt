package io.github.aleks007k.poezdki

import android.content.Context

/** Мелкие настройки: расход, цена, шаги первого запуска, текущая поездка. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("poezdki", Context.MODE_PRIVATE)

    /** Все расходы машины (экран «Машина»). */
    var car: CarCosts
        get() = CarCosts.decode(p.getString("car", null)) ?: CarCosts(
            // Первая версия хранила только расход и цену.
            litersPer100 = p.getString("l100", null)?.toDoubleOrNull() ?: 0.0,
            pricePerLiter = p.getString("price", null)?.toDoubleOrNull() ?: 0.0,
        )
        set(v) = p.edit().putString("car", v.encode()).apply()

    val hasFuelSettings get() = car.let { it.litersPer100 > 0 && it.pricePerLiter > 0 }

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

    /** Автозапись молчит до этого времени (после «НЕТ», «Стоп», ложной тревоги). */
    var autoMuteUntil: Long
        get() = p.getLong("auto_mute", 0L)
        set(v) { p.edit().putLong("auto_mute", v).apply() }

    fun muteAuto(forMs: Long) {
        autoMuteUntil = System.currentTimeMillis() + forMs
    }

    /** Запасной канал: когда последняя проверка сказала «в машине» (0 — сказала «нет»). */
    var lastSampleInCar: Long
        get() = p.getLong("auto_sample_car", 0L)
        set(v) { p.edit().putLong("auto_sample_car", v).apply() }

    /** Вопрос «Едешь на своём авто?» уже показан — не повторять до этого времени. */
    var askedUntil: Long
        get() = p.getLong("auto_asked_until", 0L)
        set(v) { p.edit().putLong("auto_asked_until", v).apply() }

    /** Когда Android последний раз сказал «в машине» — для строки состояния. */
    var lastVehicleSignal: Long
        get() = p.getLong("auto_last_signal", 0L)
        set(v) { p.edit().putLong("auto_last_signal", v).apply() }

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
