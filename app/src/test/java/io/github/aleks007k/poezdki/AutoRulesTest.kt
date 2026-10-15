package io.github.aleks007k.poezdki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRulesTest {
    private val t0 = 1_000_000L

    @Test
    fun drivingFasterThan15IsConfirmed() {
        assertTrue(AutoRules.confirmed(maxSpeedKmh = 32.0, speedKmh = 0.0))
        assertTrue(AutoRules.confirmed(maxSpeedKmh = 0.0, speedKmh = 16.0))
        assertFalse(AutoRules.confirmed(maxSpeedKmh = 12.0, speedKmh = 5.0))
    }

    @Test
    fun slowForThreeMinutesIsFalseAlarm() {
        assertFalse(AutoRules.falseAlarm(t0, t0 + 2 * 60_000L, 10.0, 0.0)) // ещё ждём
        assertTrue(AutoRules.falseAlarm(t0, t0 + 3 * 60_000L, 10.0, 0.0)) // выключаем GPS
        assertFalse(AutoRules.falseAlarm(t0, t0 + 10 * 60_000L, 40.0, 0.0)) // правда едем
    }
}
