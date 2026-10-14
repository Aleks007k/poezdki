package io.github.aleks007k.poezdki

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class Screen { MAIN, SUMMARY, TRIP, CAR }

@Composable
fun PoezdkiApp(activity: MainActivity, prefs: Prefs, db: Db) {
    PoezdkiTheme {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            val live by AppState.live.collectAsState()
            var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
            var tripId by rememberSaveable { mutableLongStateOf(0L) }
            var carBack by rememberSaveable { mutableStateOf(Screen.SUMMARY) }
            val step = activity.setupStep
            val current = live

            fun openCar(from: Screen) {
                carBack = from
                screen = Screen.CAR
            }

            when {
                current != null -> TripScreen(current, prefs.activeDestination, prefs.car, activity::stopTrip)
                step == SetupStep.FUEL -> FuelSetupScreen(prefs.car, activity::saveFuel)
                step != null -> SetupStepScreen(step, activity)
                screen == Screen.SUMMARY -> {
                    BackHandler { screen = Screen.MAIN }
                    SummaryScreen(
                        db,
                        onTrip = { tripId = it; screen = Screen.TRIP },
                        onCar = { openCar(Screen.SUMMARY) },
                    )
                }
                screen == Screen.TRIP -> {
                    BackHandler { screen = Screen.SUMMARY }
                    TripDetailScreen(
                        db, tripId, activity,
                        onBack = { screen = Screen.SUMMARY },
                        onCar = { openCar(Screen.TRIP) },
                    )
                }
                screen == Screen.CAR -> {
                    BackHandler { screen = carBack }
                    CarScreen(
                        prefs.car,
                        onBack = { screen = carBack },
                        onSave = { activity.saveCar(it); screen = carBack },
                    )
                }
                else -> MainScreen(db, activity, onSummary = { screen = Screen.SUMMARY })
            }
        }
    }
}
