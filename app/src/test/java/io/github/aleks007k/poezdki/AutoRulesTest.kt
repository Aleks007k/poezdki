package io.github.aleks007k.poezdki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRulesTest {
    private val t0 = 1_000_000L

    /** Точка в `meters` к северу от старта. */
    private fun fix(meters: Double, atMs: Long, acc: Float = 50f) =
        Fix(52.0 + meters / 111_195.0, 13.0, acc, atMs)

    @Test
    fun walkingAtHomeIsNotDriving() {
        // 1,5 минуты ходьбы по дому: точка «гуляет» на 60 м.
        assertFalse(AutoRules.movedLikeCar(fix(0.0, t0), fix(60.0, t0 + 90_000)))
        // Плохая точность по Wi‑Fi: 450 м сдвига при точности ±300 м — не верим.
        assertFalse(AutoRules.movedLikeCar(fix(0.0, t0, 300f), fix(450.0, t0 + 90_000, 300f)))
    }

    @Test
    fun drivingIsDriving() {
        // 1,5 минуты по городу на 40 км/ч ≈ 1 км.
        assertTrue(AutoRules.movedLikeCar(fix(0.0, t0), fix(1_000.0, t0 + 90_000)))
    }

    @Test
    fun needsTwoPointsAtSensibleGap() {
        assertFalse(AutoRules.movedLikeCar(null, fix(1_000.0, t0)))
        assertFalse(AutoRules.movedLikeCar(fix(0.0, t0), fix(1_000.0, t0 + 10_000))) // слишком быстро после первой
        assertFalse(AutoRules.movedLikeCar(fix(0.0, t0), fix(5_000.0, t0 + 20 * 60_000))) // слишком давно
    }

    @Test
    fun oldInCarEventIsIgnored() {
        assertTrue(AutoRules.freshEvent(30_000L))
        assertFalse(AutoRules.freshEvent(60 * 60_000L))
    }

    @Test
    fun declineLastsWholeDriveEvenIfSensorsAreUnsure() {
        val no = t0
        // Час по трассе без остановок: не шёл пешком, телефон не лежал 20 минут — молчим.
        assertFalse(AutoRules.declineOver(no + 60 * 60_000, no, walked = false, stillSince = 0))
        // Пробка 10 минут — ещё та же поездка.
        assertFalse(AutoRules.declineOver(no + 40 * 60_000, no, walked = false, stillSince = no + 30 * 60_000))
    }

    @Test
    fun declineEndsWhenDriveIsOver() {
        val no = t0
        // Вышел из машины и пошёл.
        assertTrue(AutoRules.declineOver(no + 50 * 60_000, no, walked = true, stillSince = 0))
        // Машина стоит 20 минут (телефон неподвижен).
        assertTrue(AutoRules.declineOver(no + 60 * 60_000, no, walked = false, stillSince = no + 40 * 60_000))
        // На всякий случай — через 6 часов.
        assertTrue(AutoRules.declineOver(no + 6 * 60 * 60_000, no, walked = false, stillSince = 0))
    }
}
