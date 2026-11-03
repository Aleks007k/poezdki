package io.github.aleks007k.poezdki

/** Примерная точка (по Wi‑Fi и вышкам связи) с точностью и временем. */
data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val time: Long)

/** Правила автозаписи — отдельно от Android, чтобы проверять тестами. */
object AutoRules {
    /**
     * Правда едем: между двумя примерными точками сдвинулись больше чем на 400 м
     * (с запасом на их неточность) со скоростью от 15 км/ч. Ходьба по дому — нет.
     */
    fun movedLikeCar(prev: Fix?, cur: Fix): Boolean {
        if (prev == null) return false
        val dt = cur.time - prev.time
        if (dt !in Config.MOVE_MIN_GAP_MS..Config.MOVE_MAX_GAP_MS) return false
        val d = distanceM(prev.lat, prev.lon, cur.lat, cur.lon)
        val sure = d - prev.accuracyM - cur.accuracyM // сдвиг, в котором точно уверены
        if (d < Config.MOVE_MIN_DISTANCE_M || sure <= 0) return false
        return d / (dt / 1000.0) * 3.6 >= Config.MOVE_MIN_SPEED_KMH
    }

    /** Событие «сел в машину» свежее (а не старое, присланное при подписке). */
    fun freshEvent(ageMs: Long) = ageMs in 0..Config.AUTO_EVENT_MAX_AGE_MS
}
