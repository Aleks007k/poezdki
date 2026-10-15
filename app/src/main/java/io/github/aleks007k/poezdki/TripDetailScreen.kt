package io.github.aleks007k.poezdki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TripDetailScreen(db: Db, tripId: Long, car: CarCosts, activity: MainActivity, onBack: () -> Unit, onCar: () -> Unit) {
    val context = LocalContext.current
    val version by AppState.dataVersion.collectAsState()
    var trip by remember { mutableStateOf<Trip?>(null) }
    var places by remember { mutableStateOf<Map<Int, Place>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(tripId, version, car) {
        val r = withContext(Dispatchers.IO) { db.trip(tripId) to db.places() }
        trip = r.first?.withCarCosts(car)
        places = r.second
        loaded = true
    }
    val t = trip
    if (t == null) {
        // Поездку удалили — назад к итогам.
        if (loaded) LaunchedEffect(Unit) { onBack() }
        return
    }
    TripDetailContent(
        t, places, onBack = onBack, onCar = onCar,
        onPaid = { activity.setPaid(t.id, it) },
        onDelete = { activity.deleteTrip(t.id); onBack() },
    )
}

@Composable
fun TripDetailContent(
    t: Trip,
    places: Map<Int, Place>,
    onBack: () -> Unit,
    onCar: () -> Unit,
    onPaid: (Boolean) -> Unit,
    onDelete: () -> Unit,
    initialExplain: CostKind? = null,
) {
    val context = LocalContext.current
    var explainKind by remember { mutableStateOf(initialExplain) }
    var askDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(longDate(t.startTime), onBack)

        // Откуда → куда, с линией как у маршрута.
        Panel(Modifier.fillMaxWidth(), radius = 26.dp, padding = 20.dp) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.width(22.dp).fillMaxHeight().padding(top = 6.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(14.dp).border(3.dp, C.Amber, CircleShape))
                    Box(Modifier.width(2.dp).weight(1f).padding(vertical = 6.dp).background(Color(0xFF3A404B)))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(C.Amber))
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    PointBlock(context.getString(R.string.from_label, timeOf(t.startTime)), t.start, t.startAddress, places)
                    PointBlock(context.getString(R.string.to_label, timeOf(t.endTime)), t.end, t.endAddress, places)
                }
            }
        }

        // Показатели поездки.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), kmText(context, t.distanceM), stringResource(R.string.stat_distance))
                val (h, m) = Format.hoursMinutes(t.durationMs)
                Stat(Modifier.weight(1f), context.getString(R.string.duration_short, h, m), stringResource(R.string.stat_duration))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), speedText(context, t.avgSpeedKmh), stringResource(R.string.stat_avg))
                Stat(Modifier.weight(1f), speedText(context, t.maxSpeedKmh), stringResource(R.string.stat_max))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Modifier.weight(1f), minutesText(context, t.stoppedMs), stringResource(R.string.stat_stood))
                Stat(Modifier.weight(1f), litersText(context, t.liters), stringResource(R.string.stat_liters))
            }
        }

        // Деньги: каждая строка открывает «как посчитано».
        Panel(Modifier.fillMaxWidth(), radius = 26.dp, padding = 0.dp) {
            Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
                CostRow(stringResource(R.string.cost_fuel), moneyText(context, t.fuel), C.Amber) { explainKind = CostKind.FUEL }
                CostRow(stringResource(R.string.cost_wear), moneyText(context, t.wear), C.Blue) { explainKind = CostKind.WEAR }
                CostRow(stringResource(R.string.cost_upkeep), moneyText(context, t.upkeep), C.Teal) { explainKind = CostKind.UPKEEP }
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.cost_total), fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Text(moneyText(context, t.total), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(end = 24.dp))
                }
            }
        }

        // Поездка за деньги.
        Panel(Modifier.fillMaxWidth(), radius = 22.dp, padding = 0.dp) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.paid_trip), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = t.paid,
                        onCheckedChange = onPaid,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = C.Amber, checkedThumbColor = C.OnAmber,
                            uncheckedTrackColor = C.Track, uncheckedThumbColor = C.Muted, uncheckedBorderColor = C.Line,
                        ),
                    )
                }
                if (t.paid) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.AmberDark)
                            .clickable { explainKind = CostKind.FARE }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.to_pay), color = C.AmberSoft, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Big(moneyText(context, t.fare), 22, C.Amber)
                        Spacer(Modifier.width(6.dp))
                        Ico(R.drawable.ic_chevron_right, C.Dim, 18.dp)
                    }
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp))
                .border(1.dp, C.RedLine, RoundedCornerShape(20.dp)).clickable { askDelete = true },
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.delete_trip), color = C.RedSoft, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }

    explainKind?.let { kind ->
        ExplainSheet(explain(context, t, kind), onDismiss = { explainKind = null }, onChange = { explainKind = null; onCar() })
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = C.Panel,
            title = { Big(stringResource(R.string.delete_trip_question), 20) },
            text = { Text(routeText(context, t, places), color = C.Soft, fontSize = 17.sp) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = C.RedSoft, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.cancel), color = C.Muted, fontSize = 18.sp) }
            },
        )
    }
}

@Composable
private fun PointBlock(caption: String, at: LatLon?, address: String?, places: Map<Int, Place>) {
    val context = LocalContext.current
    val place = nearPlace(at, places)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(caption, color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            place?.let { placeName(context, it.id, it) } ?: address ?: stringResource(R.string.unknown_place),
            fontWeight = FontWeight.Bold, fontSize = 21.sp,
        )
        val sub = if (place != null) address ?: place.address else null
        if (!sub.isNullOrBlank()) Text(sub, color = C.Soft, fontSize = 15.sp)
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, caption: String) {
    Panel(modifier, radius = 20.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Big(value, 22)
            Text(caption, color = C.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Soft, fontSize = 19.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Spacer(Modifier.width(6.dp))
        Ico(R.drawable.ic_chevron_right, C.Dim, 18.dp)
    }
}
