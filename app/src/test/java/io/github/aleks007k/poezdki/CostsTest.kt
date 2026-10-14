package io.github.aleks007k.poezdki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostsTest {
    // Пример из макета: 18 000 → 8 000 € за 5 лет, 15 000 км в год, содержание 1 500 € в год.
    private val car = CarCosts(
        litersPer100 = 7.0, pricePerLiter = 1.84,
        carValue = 18_000.0, sellPrice = 8_000.0, years = 5.0, kmPerYear = 15_000.0,
        insurance = 600.0, tax = 150.0, service = 500.0, tires = 250.0,
    )

    @Test
    fun perKm() {
        assertEquals(0.1288, car.fuelPerKm, 1e-9)
        assertEquals(0.13333, car.wearPerKm, 1e-5)
        assertEquals(0.1, car.upkeepPerKm, 1e-9)
    }

    @Test
    fun tripOf87km() {
        val m = 87_000.0
        assertEquals("11,21", Format.money(car.fuel(m)))
        assertEquals("11,60", Format.money(car.wear(m)))
        assertEquals("8,70", Format.money(car.upkeep(m)))
        assertEquals("6,09", Format.money(car.liters(m)))
    }

    @Test
    fun emptyFieldsCountAsZero() {
        val fuelOnly = CarCosts(litersPer100 = 7.0, pricePerLiter = 1.84)
        assertEquals(0.0, fuelOnly.wearPerKm, 0.0)
        assertEquals(0.0, fuelOnly.upkeepPerKm, 0.0)
        // Продам дороже, чем стоит, — износа нет, а не отрицательный.
        assertEquals(0.0, fuelOnly.copy(carValue = 5000.0, sellPrice = 9000.0, years = 2.0, kmPerYear = 1.0).wearPerKm, 0.0)
    }

    @Test
    fun fare() {
        // По умолчанию за км — сколько км стоит мне.
        assertEquals(car.total(87_000.0), car.fare(87_000.0, 65 * 60_000L), 1e-9)
        val taxi = car.copy(fareBase = 3.0, farePerKm = 0.5, farePerMinute = 0.1)
        assertEquals(3.0 + 43.5 + 6.5, taxi.fare(87_000.0, 65 * 60_000L), 1e-9)
    }

    @Test
    fun encodeDecode() {
        assertEquals(car, CarCosts.decode(car.encode()))
        val withFare = car.copy(farePerKm = 0.42)
        assertEquals(withFare, CarCosts.decode(withFare.encode()))
        assertNull(CarCosts.decode(null))
        assertNull(CarCosts.decode(""))
    }

    @Test
    fun numbers() {
        assertEquals("18 000", Format.number(18_000.0, 0))
        assertEquals("0,133", Format.number(0.133333, 3))
        assertEquals("7", Format.number(7.0, 2))
        assertEquals("1,84", Format.number(1.84, 2))
        assertEquals(15_000.0, Format.parse("15 000")!!, 0.0)
        assertEquals(0.0, Format.parseOrZero(""), 0.0)
    }
}
