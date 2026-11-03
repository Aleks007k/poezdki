package io.github.aleks007k.poezdki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun germanAral() {
        val r = ReceiptParser.parse(
            """
            ARAL Tankstelle
            Hauptstraße 12, 50667 Köln
            Datum: 02.11.2026 Uhrzeit: 17:42
            Super E10      Säule 4
            42,30 l x 1,849 EUR/l
            Betrag         78,21 EUR
            MwSt 19%       12,49
            Summe EUR      78,21
            """.trimIndent(), today,
        )
        assertEquals(LocalDate.of(2026, 11, 2), r.date)
        assertEquals(42.30, r.liters!!, 0.001)
        assertEquals(1.849, r.pricePerLiter!!, 0.0001)
        assertEquals(78.21, r.total!!, 0.001)
        assertEquals("EUR", r.currency)
    }

    @Test
    fun polishOrlenWithThousands() {
        val r = ReceiptParser.parse(
            """
            ORLEN S.A.
            2026-10-28 08:15
            ON EFECTA 55,12 L * 6,49 = 357,73
            SUMA PLN 357,73
            """.trimIndent(), today,
        )
        assertEquals(LocalDate.of(2026, 10, 28), r.date)
        assertEquals(55.12, r.liters!!, 0.001)
        assertEquals(6.49, r.pricePerLiter!!, 0.001)
        assertEquals(357.73, r.total!!, 0.001)
        assertEquals("PLN", r.currency)
    }

    @Test
    fun cyrillicReceiptStillReadsNumbers() {
        // Кириллица распознаётся как мусор — но цифры и дата на месте.
        val r = ReceiptParser.parse(
            """
            OKKO A3C
            15.10.26 12:03
            A-95 Evro 35,000 x 56,99 = 1994,65
            CYMA 1 994,65
            """.trimIndent(), today,
        )
        assertEquals(LocalDate.of(2026, 10, 15), r.date)
        assertEquals(35.0, r.liters!!, 0.001)
        assertEquals(56.99, r.pricePerLiter!!, 0.001)
        assertEquals(1994.65, r.total!!, 0.001)
        assertNull(r.currency) // гривну латиницей не написали — возьмём последнюю валюту
    }

    @Test
    fun blurredPhotoGivesAtLeastTotal() {
        val r = ReceiptParser.parse("Shell\nG??amt 64,90\nKarte ****1234", today)
        assertEquals(64.90, r.total!!, 0.001)
        assertNull(r.liters)
    }

    @Test
    fun currencyDetection() {
        assertEquals("CZK", Currencies.detect("CELKEM 1 245,00 Kč"))
        assertEquals("HUF", Currencies.detect("Összesen: 24 500 Ft"))
        assertEquals("CHF", Currencies.detect("Total CHF 92.40"))
        assertNull(Currencies.detect("Total 92.40"))
    }

    @Test
    fun realConsumption() {
        fun r(odo: Double, l: Double, full: Boolean) = Refuel(0, 0, l, l * 1.8, "EUR", 1.0, odo, full, null)
        val list = listOf(r(10_000.0, 40.0, true), r(10_300.0, 10.0, false), r(10_600.0, 26.0, true))
        // (10 + 26) л на 600 км = 6,0 л/100
        assertEquals(6.0, FuelMath.realConsumption(list)!!, 0.001)
        assertNull(FuelMath.realConsumption(list.take(2)))
    }
}
