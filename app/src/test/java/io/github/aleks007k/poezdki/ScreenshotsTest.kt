package io.github.aleks007k.poezdki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Снимки всех экранов с примерными данными — чтобы видеть дизайн без телефона.
 * Запуск: ./gradlew recordPaparazziDebug -Pshots (картинки — в src/test/snapshots).
 */
class ScreenshotsTest {
    init {
        Locale.setDefault(Locale("ru", "RU"))
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(locale = "ru"),
        theme = "android:Theme.Material.NoActionBar",
    )

    private val home = LatLon(52.5200, 13.4050)
    private val work = LatLon(52.4500, 13.2000)
    private val gym = LatLon(52.5300, 13.4200)
    private val places = mapOf(
        1 to Place(1, null, "Lindenstraße 5", home),
        2 to Place(2, null, "Hauptstraße 120", work),
        3 to Place(3, "Спортзал", "Gartenweg 3", gym),
    )
    private val car = CarCosts(
        litersPer100 = 7.0, pricePerLiter = 1.84,
        carValue = 18_000.0, sellPrice = 8_000.0, years = 5.0, kmPerYear = 15_000.0,
        insurance = 600.0, tax = 150.0, service = 500.0, tires = 250.0,
    )

    private fun at(day: Int, h: Int, m: Int) =
        LocalDateTime.of(2026, 10, day, h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val trips = listOf(
        Trip(1, at(13, 8, 15), at(13, 9, 20), home, work, "Lindenstraße 5", "Hauptstraße 120", 87_000.0, car,
            maxSpeedKmh = 128.0, stoppedMs = 6 * 60_000L, paid = true),
        Trip(2, at(12, 17, 40), at(12, 17, 58), LatLon(52.54, 13.41), home, "Kastanienallee 14", "Lindenstraße 5",
            12_000.0, car, maxSpeedKmh = 54.0, stoppedMs = 2 * 60_000L),
        Trip(3, at(12, 16, 55), at(12, 17, 12), home, LatLon(52.54, 13.41), "Lindenstraße 5", "Kastanienallee 14",
            11_000.0, car, maxSpeedKmh = 50.0, stoppedMs = 60_000L),
    )

    private fun shot(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        PoezdkiTheme {
            Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 28.dp)) { content() }
        }
    }

    private val no: () -> Unit = {}

    @Test
    fun main() = shot("1_main") {
        MainContent(places, 142_000.0, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, no)
    }

    @Test
    fun mainEmpty() = shot("1b_main_empty") {
        MainContent(emptyMap(), 0.0, { _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, no)
    }

    @Test
    fun trip() = shot("2_trip") {
        val now = at(14, 8, 42)
        TripScreen(LiveTrip(at(14, 8, 15), 23_400.0, 62.0, 87.0, 6 * 60_000L), "Lindenstraße 5", car, no, now0 = now)
    }

    @Test
    fun summary() = shot("3_summary") {
        SummaryContent("12–18 октября", false, false, trips, places, {}, no, no, {}, no, no)
    }

    @Test
    fun summaryEmpty() = shot("3b_summary_empty") {
        SummaryContent("5–11 октября", false, true, emptyList(), places, {}, no, no, {}, no, no)
    }

    @Test
    fun detail() = shot("4_detail") {
        TripDetailContent(trips[0], places, no, no, {}, no)
    }

    @Test
    fun explainWear() = shot("5_explain_wear") {
        ExplainContent(explain(LocalContext.current, trips[0], CostKind.WEAR), no)
    }

    @Test
    fun explainFuel() = shot("5b_explain_fuel") {
        ExplainContent(explain(LocalContext.current, trips[0], CostKind.FUEL), no)
    }

    @Test
    fun explainNotSet() = shot("5c_explain_not_set") {
        val old = trips[1].copy(costs = CarCosts(litersPer100 = 7.0, pricePerLiter = 1.84))
        ExplainContent(explain(LocalContext.current, old, CostKind.UPKEEP), no)
    }

    @Test
    fun carScreen() = shot("6_car") {
        CarScreen(car, no) {}
    }

    @Test
    fun fuelSetup() = shot("7_setup_fuel") {
        FuelSetupScreen(CarCosts()) { _, _ -> }
    }

    @Test
    fun setupBattery() = shot("7b_setup_battery") {
        SetupStepScreen(SetupStep.BATTERY, no, no)
    }
}
