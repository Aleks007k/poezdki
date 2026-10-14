package io.github.aleks007k.poezdki

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Blue = Color(0xFF1A73E8)
private val Red = Color(0xFFD93025)

private enum class Screen { MAIN, SUMMARY, FUEL }

@Composable
fun PoezdkiApp(activity: MainActivity, prefs: Prefs, db: Db) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF8AB4F8))
    else lightColorScheme(primary = Blue)
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
                val live by AppState.live.collectAsState()
                var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
                val step = activity.setupStep
                val current = live
                when {
                    current != null -> TripScreen(current, prefs.activeDestination, activity::stopTrip)
                    step == SetupStep.FUEL -> FuelForm(
                        title = stringResource(R.string.setup_fuel_title),
                        button = stringResource(R.string.next),
                        prefs = prefs,
                        onSave = activity::saveFuel,
                    )
                    step != null -> SetupStepScreen(step, activity)
                    screen == Screen.SUMMARY -> SummaryScreen(
                        db, activity, onFuel = { screen = Screen.FUEL }, onBack = { screen = Screen.MAIN },
                    )
                    screen == Screen.FUEL -> {
                        BackHandler { screen = Screen.SUMMARY }
                        FuelForm(
                            title = stringResource(R.string.fuel),
                            button = stringResource(R.string.save),
                            prefs = prefs,
                            onSave = { l, p -> activity.saveFuel(l, p); screen = Screen.SUMMARY },
                        )
                    }
                    else -> MainScreen(db, activity, onSummary = { screen = Screen.SUMMARY })
                }
            }
        }
    }
}

// ---------------- Главный экран ----------------

@Composable
private fun MainScreen(db: Db, activity: MainActivity, onSummary: () -> Unit) {
    val version by AppState.dataVersion.collectAsState()
    var places by remember { mutableStateOf<Map<Int, Place>>(emptyMap()) }
    LaunchedEffect(version) { places = withContext(Dispatchers.IO) { db.places() } }

    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableIntStateOf(0) } // какую кнопку места правим (0 — никакую)

    fun submit() {
        val dest = text.trim()
        if (dest.isEmpty()) return
        text = ""
        activity.go(dest, null)
    }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.where), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 24.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = { submit() },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_navigate),
                    contentDescription = stringResource(R.string.go),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (id in 1..3) {
                val place = places[id]
                PlaceButton(
                    label = placeName(LocalContext.current, id, place),
                    onClick = {
                        val address = place?.address
                        if (address.isNullOrBlank()) editing = id else activity.go(address, place?.coords)
                    },
                    onLongClick = { editing = id },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSummary, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.summary), fontSize = 20.sp)
        }
    }

    if (editing != 0) {
        val id = editing
        PlaceDialog(
            title = placeName(LocalContext.current, id, places[id]),
            askName = id == 3,
            initialName = places[id]?.name.orEmpty(),
            initialAddress = places[id]?.address.orEmpty(),
            onSave = { name, address ->
                activity.savePlace(id, name.takeIf { id == 3 }, address)
                editing = 0
            },
            onDismiss = { editing = 0 },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceButton(label: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PlaceDialog(
    title: String,
    askName: Boolean,
    initialName: String,
    initialAddress: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    val ok = address.isNotBlank() && (!askName || name.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 24.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (askName) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.place_name)) },
                        textStyle = TextStyle(fontSize = 22.sp), singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text(stringResource(R.string.place_address)) },
                    textStyle = TextStyle(fontSize = 22.sp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), address.trim()) }, enabled = ok) {
                Text(stringResource(R.string.save), fontSize = 20.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), fontSize = 20.sp) }
        },
    )
}

// ---------------- Поездка ----------------

@Composable
private fun TripScreen(live: LiveTrip, destination: String, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.km_value, Format.km(live.distanceM)),
            fontSize = 76.sp, fontWeight = FontWeight.Bold,
        )
        Text(durationText(now - live.startTime), fontSize = 44.sp)
        if (destination.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.trip_to, destination), fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(56.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
        ) {
            Text(stringResource(R.string.stop), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------- Итоги ----------------

@Composable
private fun SummaryScreen(db: Db, activity: MainActivity, onFuel: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val version by AppState.dataVersion.collectAsState()
    val zone = ZoneId.systemDefault()
    val period = if (monthMode) Period.month(LocalDate.now(), offset) else Period.week(LocalDate.now(), offset)

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var places by remember { mutableStateOf<Map<Int, Place>>(emptyMap()) }
    LaunchedEffect(period, version) {
        val loaded = withContext(Dispatchers.IO) {
            db.trips(period.startMillis(zone), period.endMillis(zone)) to db.places()
        }
        trips = loaded.first
        places = loaded.second
    }
    var toDelete by remember { mutableStateOf<Trip?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleButton(stringResource(R.string.week), !monthMode, Modifier.weight(1f)) {
                monthMode = false; offset = 0
            }
            ToggleButton(stringResource(R.string.month), monthMode, Modifier.weight(1f)) {
                monthMode = true; offset = 0
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { offset-- }) { Text("‹", fontSize = 40.sp) }
            Text(
                periodLabel(period, monthMode), fontSize = 22.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { offset++ }, enabled = offset < 0) { Text("›", fontSize = 40.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            BigNumber(Format.km(trips.sumOf { it.distanceM }), stringResource(R.string.caption_km), Modifier.weight(1f))
            BigNumber(trips.size.toString(), stringResource(R.string.caption_trips), Modifier.weight(1f))
            BigNumber(Format.money(trips.sumOf { it.cost }), stringResource(R.string.caption_euro), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (trips.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_trips), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(trips, key = { it.id }) { t -> TripCard(context, t, places) { toDelete = t } }
            }
        }
        TextButton(onClick = onFuel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.fuel), fontSize = 20.sp)
        }
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_trip_question), fontSize = 24.sp) },
            text = { Text(cardText(context, t, places), fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = { activity.deleteTrip(t.id); toDelete = null }) {
                    Text(stringResource(R.string.delete), fontSize = 20.sp, color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel), fontSize = 20.sp) }
            },
        )
    }
}

@Composable
private fun ToggleButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(56.dp), shape = shape) { Text(text, fontSize = 20.sp) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(56.dp), shape = shape) { Text(text, fontSize = 20.sp) }
    }
}

@Composable
private fun BigNumber(value: String, caption: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(caption, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TripCard(context: Context, t: Trip, places: Map<Int, Place>, onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                DateTimeFormatter.ofPattern("EE, d MMM, HH:mm")
                    .format(Instant.ofEpochMilli(t.startTime).atZone(ZoneId.systemDefault())),
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(cardText(context, t, places), fontSize = 20.sp)
        }
    }
}

// ---------------- Расход ----------------

@Composable
private fun FuelForm(title: String, button: String, prefs: Prefs, onSave: (Double, Double) -> Unit) {
    var liters by rememberSaveable {
        mutableStateOf(prefs.litersPer100.takeIf { it > 0 }?.let(Format::forInput).orEmpty())
    }
    var price by rememberSaveable {
        mutableStateOf(prefs.pricePerLiter.takeIf { it > 0 }?.let(Format::forInput).orEmpty())
    }
    val l = Format.parse(liters)
    val p = Format.parse(price)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        NumberField(liters, { liters = it }, stringResource(R.string.liters_per_100))
        NumberField(price, { price = it }, stringResource(R.string.price_per_liter))
        Button(
            onClick = { if (l != null && p != null) onSave(l, p) },
            enabled = l != null && p != null,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(20.dp),
        ) { Text(button, fontSize = 28.sp) }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label, fontSize = 18.sp) },
        textStyle = TextStyle(fontSize = 28.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------- Первый запуск ----------------

@Composable
private fun SetupStepScreen(step: SetupStep, activity: MainActivity) {
    val (text, button) = when (step) {
        SetupStep.LOCATION -> R.string.perm_location_text to R.string.allow
        SetupStep.BACKGROUND -> R.string.perm_background_text to R.string.open_settings
        SetupStep.NOTIFICATIONS -> R.string.perm_notifications_text to R.string.allow
        else -> R.string.battery_text to R.string.open_settings
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(text), fontSize = 26.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { activity.setupAction(step) },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(20.dp),
        ) { Text(stringResource(button), fontSize = 26.sp) }
        if (step == SetupStep.BATTERY) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = activity::finishBatteryStep) {
                Text(stringResource(R.string.done), fontSize = 22.sp)
            }
        }
    }
}

// ---------------- Тексты ----------------

/** Название кнопки места. */
private fun placeName(context: Context, id: Int, place: Place?): String = when (id) {
    1 -> context.getString(R.string.home)
    2 -> context.getString(R.string.work)
    else -> place?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.custom_place)
}

/** Точка ближе 200 м к адресу кнопки — название кнопки, иначе улица и дом. */
private fun pointLabel(context: Context, at: LatLon?, address: String?, places: Map<Int, Place>): String {
    if (at != null) {
        places.values
            .filter { it.coords != null && !it.address.isNullOrBlank() }
            .minByOrNull { distanceM(at, it.coords!!) }
            ?.takeIf { distanceM(at, it.coords!!) <= Config.PLACE_RADIUS_M }
            ?.let { return placeName(context, it.id, it) }
    }
    return address ?: context.getString(R.string.unknown_place)
}

private fun cardText(context: Context, t: Trip, places: Map<Int, Place>): String =
    context.getString(
        R.string.trip_card,
        pointLabel(context, t.start, t.startAddress, places),
        pointLabel(context, t.end, t.endAddress, places),
        context.getString(R.string.km_value, Format.km(t.distanceM)),
        durationText(context, t.endTime - t.startTime),
        context.getString(R.string.money_value, Format.money(t.cost)),
    )

@Composable
private fun durationText(millis: Long): String = durationText(LocalContext.current, millis)

private fun durationText(context: Context, millis: Long): String {
    val (h, m) = Format.hoursMinutes(millis)
    return if (h > 0) context.getString(R.string.duration_hm, h, m) else context.getString(R.string.duration_m, m)
}

private fun periodLabel(p: Period, monthMode: Boolean): String {
    if (monthMode) {
        return DateTimeFormatter.ofPattern("LLLL yyyy").format(p.from).replaceFirstChar { it.uppercase() }
    }
    val last = p.toExclusive.minusDays(1)
    return if (p.from.month == last.month) {
        "${p.from.dayOfMonth}–${DateTimeFormatter.ofPattern("d MMM").format(last)}"
    } else {
        "${DateTimeFormatter.ofPattern("d MMM").format(p.from)} – ${DateTimeFormatter.ofPattern("d MMM").format(last)}"
    }
}
