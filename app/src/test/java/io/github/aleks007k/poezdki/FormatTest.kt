package io.github.aleks007k.poezdki

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test
    fun numbers() {
        assertEquals("87", Format.km(87_400.0))
        assertEquals("8,4", Format.km(8_420.0))
        assertEquals("11,20", Format.money(11.2))
        assertEquals(1 to 5, Format.hoursMinutes(65 * 60_000L))
        assertEquals(6.5, Format.parse("6,5")!!, 0.0)
        assertEquals(6.5, Format.parse(" 6.5 ")!!, 0.0)
        assertEquals(null, Format.parse("abc"))
        assertEquals("6,5", Format.forInput(6.5))
        assertEquals("7", Format.forInput(7.0))
    }

    @Test
    fun cost() {
        // 87 км × 7 л/100 × 1,84 € = 11,21 €
        assertEquals("11,21", Format.money(CarCosts(litersPer100 = 7.0, pricePerLiter = 1.84).fuel(87_000.0)))
    }

    @Test
    fun periods() {
        val wed = LocalDate.of(2026, 10, 14)
        val w = Period.week(wed, 0)
        assertEquals(LocalDate.of(2026, 10, 12), w.from)
        assertEquals(LocalDate.of(2026, 10, 19), w.toExclusive)
        assertEquals(LocalDate.of(2026, 10, 5), Period.week(wed, -1).from)
        val sun = LocalDate.of(2026, 10, 18)
        assertEquals(LocalDate.of(2026, 10, 12), Period.week(sun, 0).from)
        val m = Period.month(wed, -10)
        assertEquals(LocalDate.of(2025, 12, 1), m.from)
        assertEquals(LocalDate.of(2026, 1, 1), m.toExclusive)
    }
}
