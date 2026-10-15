package io.github.aleks007k.poezdki

/** Правила автозаписи — отдельно от Android, чтобы проверять тестами. */
object AutoRules {
    /** Правда едем: скорость была выше 15 км/ч. Тогда спрашиваем «На своём авто?». */
    fun confirmed(maxSpeedKmh: Double, speedKmh: Double) =
        maxOf(maxSpeedKmh, speedKmh) > Config.AUTO_MIN_SPEED_KMH

    /** Ложная тревога: 3 минуты так и не поехали — выключаем GPS, ничего не сохраняем. */
    fun falseAlarm(startTime: Long, now: Long, maxSpeedKmh: Double, speedKmh: Double) =
        !confirmed(maxSpeedKmh, speedKmh) && now - startTime >= Config.AUTO_CONFIRM_MS
}
