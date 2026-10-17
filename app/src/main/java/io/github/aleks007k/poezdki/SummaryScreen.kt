package io.github.aleks007k.poezdki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SummaryScreen(db: Db, car: CarCosts, onTrip: (Long) -> Unit, onCar: () -> Unit, onBack: () -> Unit) {
    var monthMode by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val version by AppState.dataVersion.collectAsState()
    val zone = ZoneId.systemDefault()
    val period = if (monthMode) Period.month(LocalDate.now(), offset) else Period.week(LocalDate.now(), offset)

    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var places by remember { mutableStateOf<Map<Int, Place>>(emptyMap()) }
    LaunchedEffect(period, version, car) {
        val loaded = withContext(Dispatchers.IO) {
            db.trips(period.startMillis(zone), period.endMillis(zone)) to db.places()
        }
        trips = loaded.first.map { it.withCarCosts(car) }
        places = loaded.second
    }
    SummaryContent(
        periodLabel(period, monthMode), monthMode, canGoLater = offset < 0, trips, places,
        onMode = { monthMode = it; offset = 0 }, onEarlier = { offset-- }, onLater = { offset++ },
        onTrip = onTrip, onCar = onCar, onBack = onBack,
    )
}

@Composable
fun SummaryContent(
    periodText: String,
    monthMode: Boolean,
    canGoLater: Boolean,
    trips: List<Trip>,
    places: Map<Int, Place>,
    onMode: (Boolean) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onTrip: (Long) -> Unit,
    onCar: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.summary), onBack)
        // Неделя | Месяц
        Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 4.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Segment(stringResource(R.string.week), !monthMode, Modifier.weight(1f)) { onMode(false) }
                Segment(stringResource(R.string.month), monthMode, Modifier.weight(1f)) { onMode(true) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton(R.drawable.ic_chevron_left, stringResource(R.string.previous), C.Text, onEarlier)
            Text(
                periodText, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            SquareButton(R.drawable.ic_chevron_right, stringResource(R.string.next_period), C.Text, onLater, enabled = canGoLater)
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallStat(Modifier.weight(1f), Format.km(trips.sumOf { it.distanceM }), stringResource(R.string.caption_km))
                    SmallStat(Modifier.weight(1f), trips.size.toString(), stringResource(R.string.caption_trips))
                    SmallStat(Modifier.weight(1f), Format.number(trips.sumOf { it.liters }, 1), stringResource(R.string.caption_liters))
                }
            }
            item { CostPlate(trips) }
            if (trips.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_trips), color = C.Muted, fontSize = 18.sp)
                    }
                }
            }
            itemsIndexed(trips, key = { _, t -> t.id }) { i, t ->
                TripCard(t, places, big = i == 0) { onTrip(t.id) }
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OutlineLink(R.drawable.ic_car, stringResource(R.string.car_link), onCar)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) C.Amber else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) C.OnAmber else C.Muted, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

@Composable
private fun SmallStat(modifier: Modifier, value: String, caption: String) {
    Panel(modifier, radius = 18.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Big(value, 20)
            Text(caption, color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** «Машина обошлась» + полоска топливо / износ / содержание. */
@Composable
private fun CostPlate(trips: List<Trip>) {
    val context = LocalContext.current
    val fuel = trips.sumOf { it.fuel }
    val wear = trips.sumOf { it.wear }
    val upkeep = trips.sumOf { it.upkeep }
    Panel(Modifier.fillMaxWidth(), radius = 20.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.car_cost), color = C.Soft, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Big(moneyText(context, fuel + wear + upkeep), 22)
            }
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (fuel + wear + upkeep <= 0.0) {
                    Box(Modifier.weight(1f).height(8.dp).background(C.Track))
                } else {
                    listOf(fuel to C.Amber, wear to C.Blue, upkeep to C.Teal).filter { it.first > 0 }.forEach { (v, c) ->
                        Box(Modifier.weight(v.toFloat()).height(8.dp).background(c))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Legend(C.Amber, context.getString(R.string.legend_fuel, Format.money(fuel)))
                Legend(C.Blue, context.getString(R.string.legend_wear, Format.money(wear)))
                Legend(C.Teal, context.getString(R.string.legend_upkeep, Format.money(upkeep)))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = C.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TripCard(t: Trip, places: Map<Int, Place>, big: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Panel(Modifier.fillMaxWidth(), radius = if (big) 24.dp else 18.dp, padding = 0.dp, onClick = onClick) {
        Column(
            Modifier.padding(horizontal = if (big) 18.dp else 14.dp, vertical = if (big) 18.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (big) 8.dp else 4.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    context.getString(R.string.time_range, shortDate(t.startTime), timeOf(t.startTime), timeOf(t.endTime)),
                    color = C.Muted, fontSize = if (big) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                Big(moneyText(context, if (t.paid) t.fare else t.total), if (big) 20 else 16)
            }
            Text(
                routeText(context, t, places), fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 17.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(
                        R.string.trip_line, kmText(context, t.distanceM),
                        context.getString(R.string.drive_time, durationText(context, t.movingMs)), litersText(context, t.liters),
                    ),
                    color = C.Soft, fontSize = if (big) 16.sp else 14.sp, modifier = Modifier.weight(1f),
                )
                if (t.paid) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(C.Amber), contentAlignment = Alignment.Center) {
                        Text("€", color = C.OnAmber, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

fun periodLabel(p: Period, monthMode: Boolean): String {
    if (monthMode) {
        return DateTimeFormatter.ofPattern("LLLL yyyy").format(p.from).replaceFirstChar { it.uppercase() }
    }
    val last = p.toExclusive.minusDays(1)
    return if (p.from.month == last.month) {
        "${p.from.dayOfMonth}–${DateTimeFormatter.ofPattern("d MMMM").format(last)}"
    } else {
        "${DateTimeFormatter.ofPattern("d MMM").format(p.from)} – ${DateTimeFormatter.ofPattern("d MMM").format(last)}"
    }
}
