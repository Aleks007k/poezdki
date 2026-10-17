package io.github.aleks007k.poezdki

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Шкала спидометра: до этой скорости дуга заполняется полностью. */
private const val GAUGE_MAX_KMH = 160.0

@Composable
fun TripScreen(
    live: LiveTrip, destination: String, car: CarCosts, onStop: () -> Unit, onSummary: () -> Unit, now0: Long? = null,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(now0 ?: System.currentTimeMillis()) }
    if (now0 == null) LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = (now - live.startTime).coerceAtLeast(0)
    // Средняя — в движении, без стоянок: по ней видно, город это или трасса.
    val moving = (elapsed - live.stoppedMs).coerceAtLeast(0)
    val avg = if (moving > 60_000) live.distanceM / 1000.0 / (moving / 3_600_000.0) else 0.0
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "rec",
    )
    val sweep by animateFloatAsState((live.speedKmh / GAUGE_MAX_KMH).coerceIn(0.0, 1.0).toFloat() * 270f, label = "gauge")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.clip(CircleShape).background(C.Panel).border(1.dp, C.Line, CircleShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(Color(0xFFFF5A4E)))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recording), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        if (destination.isNotBlank()) {
            Text(
                stringResource(R.string.trip_to, destination), color = C.Muted, fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        // Спидометр: дуга — текущая скорость, в центре — километры и время.
        Box(Modifier.fillMaxWidth(0.78f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 14.dp.toPx()
                val arcSize = Size(size.width - w, size.height - w)
                val topLeft = Offset(w / 2, w / 2)
                drawArc(C.Track, 135f, 270f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                if (sweep > 0.5f) drawArc(C.Amber, 135f, sweep, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Big(Format.decimal(live.distanceM / 1000.0, 1), 64)
                Text(stringResource(R.string.km_caption), color = C.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Big(durationText(context, elapsed), 24, bold = false)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), stringResource(R.string.tile_fuel), moneyText(context, car.fuel(live.distanceM, if (avg > 0) avg else Config.HIGHWAY_SPEED_KMH)), C.Amber)
                Tile(Modifier.weight(1f), stringResource(R.string.tile_stood), minutesText(context, live.stoppedMs))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), stringResource(R.string.tile_avg), speedText(context, avg))
                Tile(Modifier.weight(1f), stringResource(R.string.tile_max), speedText(context, live.maxSpeedKmh))
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(28.dp)).background(C.Red).clickable(onClick = onStop),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
            Spacer(Modifier.width(14.dp))
            Big(stringResource(R.string.stop), 28, Color.White)
        }
        // Во время записи можно смотреть итоги и машину — запись идёт дальше.
        OutlineLink(R.drawable.ic_chart, stringResource(R.string.summary), onSummary)
    }
}

@Composable
private fun Tile(modifier: Modifier, label: String, value: String, color: Color = C.Text) {
    Panel(modifier, radius = 20.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Big(value, 20, color)
        }
    }
}
