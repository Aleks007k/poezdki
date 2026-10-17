package io.github.aleks007k.poezdki

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class Screen { MAIN, SUMMARY, TRIP, CAR }

@Composable
fun PoezdkiApp(activity: MainActivity, prefs: Prefs, db: Db) {
    PoezdkiTheme {
        val live by AppState.live.collectAsState()
        // Чтобы после сохранения «VIP Борт №1» экраны пересчитались.
        val version by AppState.dataVersion.collectAsState()
        val car = remember(version) { prefs.car }
        var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
        var tripId by rememberSaveable { mutableLongStateOf(0L) }
        var carBack by rememberSaveable { mutableStateOf(Screen.SUMMARY) }
        val step = activity.setupStep
        val current = live

        fun openCar(from: Screen) {
            carBack = from
            screen = Screen.CAR
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            // Во время записи на других экранах — полоска; нажал — назад к поездке.
            if (current != null && screen != Screen.MAIN) RecordingBar(current.distanceM) { screen = Screen.MAIN }
            Box(Modifier.weight(1f)) {
                when {
                    current != null && screen == Screen.MAIN -> TripScreen(
                        current, prefs.activeDestination, car, activity::stopTrip,
                        onSummary = { screen = Screen.SUMMARY },
                    )
                    step == SetupStep.FUEL -> FuelSetupScreen(prefs.car, activity::saveFuel)
                    step != null -> SetupStepScreen(step, { activity.setupAction(step) }, activity::finishBatteryStep)
                    screen == Screen.SUMMARY -> {
                        BackHandler { screen = Screen.MAIN }
                        SummaryScreen(
                            db, car,
                            onTrip = { tripId = it; screen = Screen.TRIP },
                            onCar = { openCar(Screen.SUMMARY) },
                            onBack = { screen = Screen.MAIN },
                        )
                    }
                    screen == Screen.TRIP -> {
                        BackHandler { screen = Screen.SUMMARY }
                        TripDetailScreen(
                            db, tripId, car, activity,
                            onBack = { screen = Screen.SUMMARY },
                            onCar = { openCar(Screen.TRIP) },
                        )
                    }
                    screen == Screen.CAR -> CarScreen(
                        car,
                        onBack = { screen = carBack },
                        onSave = { activity.saveCar(it); screen = carBack },
                        autoProblem = AutoDetect.problem(activity),
                        lastVehicleSignal = prefs.lastVehicleSignal,
                        onFixAuto = activity::openAppSettings,
                    )
                    else -> MainScreen(db, activity, onSummary = { screen = Screen.SUMMARY })
                }
            }
        }
    }
}
