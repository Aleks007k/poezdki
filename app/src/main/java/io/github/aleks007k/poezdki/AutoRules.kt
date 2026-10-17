package io.github.aleks007k.poezdki

/** Правила автозаписи — отдельно от Android, чтобы проверять тестами. */
object AutoRules {
    /** Правда едем: скорость была выше 15 км/ч. Тогда спрашиваем «На своём авто?». */
    fun confirmed(maxSpeedKmh: Double, speedKmh: Double) =
        maxOf(maxSpeedKmh, speedKmh) > Config.AUTO_MIN_SPEED_KMH

    /** Ложная тревога: 3 минуты так и не поехали — выключаем GPS, ничего не сохраняем. */
    fun falseAlarm(startTime: Long, now: Long, maxSpeedKmh: Double, speedKmh: Double) =
        !confirmed(maxSpeedKmh, speedKmh) && now - startTime >= Config.AUTO_CONFIRM_MS

    /** Запасной канал: верим «в машине», только если предыдущая проверка тоже сказала «в машине». */
    fun pairConfirms(previousInCarAt: Long, now: Long) =
        previousInCarAt > 0 && now - previousInCarAt in 1..Config.AUTO_PAIR_WINDOW_MS

    /** Событие «сел в машину» свежее (а не старое, присланное при подписке). */
    fun freshEvent(ageMs: Long) = ageMs in 0..Config.AUTO_EVENT_MAX_AGE_MS
}
