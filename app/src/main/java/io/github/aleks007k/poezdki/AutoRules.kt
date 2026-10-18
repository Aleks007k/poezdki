package io.github.aleks007k.poezdki

/** Правила автозаписи — отдельно от Android, чтобы проверять тестами. */
object AutoRules {
    /** Запасной канал: верим «в машине», только если предыдущая проверка тоже сказала «в машине». */
    fun pairConfirms(previousInCarAt: Long, now: Long) =
        previousInCarAt > 0 && now - previousInCarAt in 1..Config.AUTO_PAIR_WINDOW_MS

    /** Событие «сел в машину» свежее (а не старое, присланное при подписке). */
    fun freshEvent(ageMs: Long) = ageMs in 0..Config.AUTO_EVENT_MAX_AGE_MS
}
