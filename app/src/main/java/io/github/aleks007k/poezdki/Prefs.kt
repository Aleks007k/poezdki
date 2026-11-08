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

    /**
     * Секретная метка текущего вопроса «Едешь?». Кнопка «ДА» несёт её с собой — так
     * другое приложение на телефоне не сможет само включить запись. Пусто — вопроса нет.
     */
    var driveToken: String
        get() = p.getString("drive_token", "") ?: ""
        set(v) { p.edit().putString("drive_token", v).apply() }

    /** Автозапись молчит до этого времени (после «НЕТ», «Стоп», ложной тревоги). */
    var autoMuteUntil: Long
        get() = p.getLong("auto_mute", 0L)
        set(v) { p.edit().putLong("auto_mute", v).apply() }

    fun muteAuto(forMs: Long) {
        autoMuteUntil = System.currentTimeMillis() + forMs
    }

    /** Вопрос «Едешь?» показан и на него ещё не ответили. */
    var askOpen: Boolean
        get() = p.getBoolean("auto_ask_open", false)
        set(v) { p.edit().putBoolean("auto_ask_open", v).apply() }

    /** Сказал «НЕТ» (или смахнул вопрос) — молчим до конца этой поездки. */
    var declinedDrive: Boolean
        get() = p.getBoolean("auto_declined", false)
        set(v) { p.edit().putBoolean("auto_declined", v).apply() }

    /** Сколько проверок подряд сказали «не в машине» после «НЕТ». */
    var notInCarStreak: Int
        get() = p.getInt("auto_not_car", 0)
        set(v) { p.edit().putInt("auto_not_car", v).apply() }

    /** Сколько раз подряд Android сказал «в машине» (для случая без геолокации). */
    var inCarStreak: Int
        get() = p.getInt("auto_streak", 0)
        set(v) { p.edit().putInt("auto_streak", v).apply() }

    /** Последняя примерная точка при сигнале «в машине» — чтобы понять, сдвинулись ли. */
    var lastFix: Fix?
        get() = if (p.contains("fix_t")) Fix(
            java.lang.Double.longBitsToDouble(p.getLong("fix_lat", 0)),
            java.lang.Double.longBitsToDouble(p.getLong("fix_lon", 0)),
            p.getFloat("fix_acc", 0f), p.getLong("fix_t", 0),
        ) else null
        set(v) {
            val e = p.edit()
            if (v == null) e.remove("fix_t") else e.putLong("fix_lat", java.lang.Double.doubleToRawLongBits(v.lat))
                .putLong("fix_lon", java.lang.Double.doubleToRawLongBits(v.lon))
                .putFloat("fix_acc", v.accuracyM).putLong("fix_t", v.time)
            e.apply()
        }

    /** Курсы валют, введённые вручную: сколько единиц за 1 € («PLN» → 4.3). */
    var rates: Map<String, Double>
        get() = p.getString("rates", "").orEmpty().split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to (part.substring(i + 1).toDoubleOrNull() ?: return@mapNotNull null)
        }.toMap()
        set(v) { p.edit().putString("rates", v.entries.joinToString(";") { "${it.key}=${it.value}" }).apply() }

    /** Месяц («2026-11»), за который уже подтвердили курсы. */
    var ratesConfirmedMonth: String?
        get() = p.getString("rates_month", null)
        set(v) { p.edit().putString("rates_month", v).apply() }

    /** Валюта последней заправки — подставляем, если на чеке её не видно. */
    var lastCurrency: String
        get() = p.getString("last_currency", "EUR") ?: "EUR"
        set(v) { p.edit().putString("last_currency", v).apply() }

    /** Что открывать в начале поездки перед навигатором (Blitzer.de, музыка…), по порядку. */
    var companions: List<CompanionApp>
        get() {
            val saved = p.getString("companions", null)
            if (saved == null) {
                // Раньше было одно приложение — переносим его в список.
                val pkg = p.getString("companion_pkg", null) ?: return emptyList()
                return listOf(CompanionApp(pkg, p.getString("companion_label", null) ?: pkg))
            }
            return saved.split('\n').mapNotNull { line ->
                val i = line.indexOf('\t')
                if (i <= 0) null else CompanionApp(line.substring(0, i), line.substring(i + 1))
            }
        }
        set(v) { p.edit().putString("companions", v.joinToString("\n") { "${it.pkg}\t${it.label}" }).apply() }

    /** Навигатор для маршрута: «google» или «waze». */
    var navigator: String
        get() = p.getString("navigator", NAV_GOOGLE) ?: NAV_GOOGLE
        set(v) { p.edit().putString("navigator", v).apply() }

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

/** Приложение, которое открывается в начале поездки. */
data class CompanionApp(val pkg: String, val label: String)

const val NAV_GOOGLE = "google"
const val NAV_WAZE = "waze"
