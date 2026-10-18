package io.github.aleks007k.poezdki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRulesTest {
    private val t0 = 1_000_000L

    @Test
    fun oneSampleIsNotEnough() {
        assertFalse(AutoRules.pairConfirms(previousInCarAt = 0L, now = t0)) // первая проверка
        assertTrue(AutoRules.pairConfirms(previousInCarAt = t0, now = t0 + 90_000L)) // вторая подряд
        assertFalse(AutoRules.pairConfirms(previousInCarAt = t0, now = t0 + 10 * 60_000L)) // слишком давно
    }

    @Test
    fun oldInCarEventIsIgnored() {
        assertTrue(AutoRules.freshEvent(30_000L))
        assertFalse(AutoRules.freshEvent(60 * 60_000L))
    }
}
