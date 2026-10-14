package io.github.aleks007k.poezdki

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Какое окно места открыто: никакое, правка места, новое место. */
private sealed interface PlaceEdit {
    data object None : PlaceEdit
    data class Existing(val id: Int) : PlaceEdit
    data object New : PlaceEdit
}

@Composable
fun MainScreen(db: Db, activity: MainActivity, onSummary: () -> Unit) {
    val context = LocalContext.current
    val version by AppState.dataVersion.collectAsState()
    var places by remember { mutableStateOf<Map<Int, Place>>(emptyMap()) }
    var weekMeters by remember { mutableStateOf(0.0) }
    LaunchedEffect(version) {
        val zone = ZoneId.systemDefault()
        val week = Period.week(LocalDate.now(), 0)
        val (p, m) = withContext(Dispatchers.IO) {
            db.places() to db.trips(week.startMillis(zone), week.endMillis(zone)).sumOf { it.distanceM }
        }
        places = p
        weekMeters = m
    }

    var text by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<PlaceEdit>(PlaceEdit.None) }

    fun submit() {
        val dest = text.trim()
        if (dest.isEmpty()) return
        text = ""
        activity.go(dest, null)
    }

    fun tap(id: Int) {
        val p = places[id]
        val address = p?.address
        if (address.isNullOrBlank()) edit = PlaceEdit.Existing(id) else activity.go(address, p?.coords)
    }

    Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 16.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Big(stringResource(R.string.where), 34)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Panel(Modifier.weight(1f).height(72.dp), radius = 20.dp, padding = 0.dp) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = TextStyle(color = C.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                            cursorBrush = SolidColor(C.Amber),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (text.isEmpty()) Text(stringResource(R.string.dest_hint), color = C.Dim, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                inner()
                            },
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (text.isNotBlank()) C.Amber else C.Track)
                        .clickable(enabled = text.isNotBlank()) { submit() },
                    contentAlignment = Alignment.Center,
                ) { Ico(R.drawable.ic_navigate, if (text.isNotBlank()) C.OnAmber else C.Dim, 36.dp, stringResource(R.string.go)) }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigPlaceTile(Modifier.weight(1f), R.drawable.ic_home, placeName(context, 1, places[1]), places[1]?.address,
                    onClick = { tap(1) }, onLongClick = { edit = PlaceEdit.Existing(1) })
                BigPlaceTile(Modifier.weight(1f), R.drawable.ic_work, placeName(context, 2, places[2]), places[2]?.address,
                    onClick = { tap(2) }, onLongClick = { edit = PlaceEdit.Existing(2) })
            }
            places.keys.filter { it >= 3 }.sorted().forEach { id ->
                Panel(
                    Modifier.fillMaxWidth().height(68.dp), radius = 22.dp, padding = 0.dp,
                    onClick = { tap(id) }, onLongClick = { edit = PlaceEdit.Existing(id) },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(R.drawable.ic_pin, C.Amber, 26.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(placeName(context, id, places[id]), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(12.dp))
                        Text(places[id]?.address.orEmpty(), color = C.Muted, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
            // Пустая плитка «+»: новое место.
            Box(
                Modifier.fillMaxWidth().height(60.dp).dashedBorder(C.Line, 22.dp)
                    .clip(RoundedCornerShape(22.dp)).clickable { edit = PlaceEdit.New },
                contentAlignment = Alignment.Center,
            ) { Ico(R.drawable.ic_plus, C.Faint, 28.dp, stringResource(R.string.add_place)) }
        }

        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth(), radius = 26.dp, padding = 0.dp, onClick = onSummary) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Caption(stringResource(R.string.this_week))
                    Big(kmText(context, weekMeters), 22)
                }
                Text(stringResource(R.string.summary), color = C.Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Ico(R.drawable.ic_chevron_right, C.Muted, 20.dp)
            }
        }
    }

    when (val e = edit) {
        PlaceEdit.None -> Unit
        PlaceEdit.New -> PlaceDialog(
            title = stringResource(R.string.new_place), askName = true, initialName = "", initialAddress = "",
            onSave = { name, address -> activity.addPlace(name, address); edit = PlaceEdit.None },
            onDelete = null, onDismiss = { edit = PlaceEdit.None },
        )
        is PlaceEdit.Existing -> {
            val p = places[e.id]
            PlaceDialog(
                title = placeName(context, e.id, p), askName = e.id >= 3,
                initialName = p?.name.orEmpty(), initialAddress = p?.address.orEmpty(),
                onSave = { name, address ->
                    activity.savePlace(e.id, name.takeIf { e.id >= 3 }, address); edit = PlaceEdit.None
                },
                onDelete = if (e.id >= 3 && p != null) ({ activity.deletePlace(e.id); edit = PlaceEdit.None }) else null,
                onDismiss = { edit = PlaceEdit.None },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigPlaceTile(
    modifier: Modifier, icon: Int, name: String, address: String?,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Panel(modifier.height(150.dp), radius = 26.dp, padding = 20.dp, onClick = onClick, onLongClick = onLongClick) {
        Ico(icon, C.Amber, 34.dp)
        Spacer(Modifier.weight(1f))
        Text(name, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            address ?: stringResource(R.string.set_address), color = C.Muted, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaceDialog(
    title: String,
    askName: Boolean,
    initialName: String,
    initialAddress: String,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    val ok = address.isNotBlank() && (!askName || name.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Big(title, 20) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (askName) Field(stringResource(R.string.place_name), name, { name = it }, numeric = false)
                Field(stringResource(R.string.place_address), address, { address = it }, numeric = false)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), address.trim()) }, enabled = ok) {
                Text(stringResource(R.string.save), color = if (ok) C.Amber else C.Dim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = C.RedSoft, fontSize = 18.sp) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = C.Muted, fontSize = 18.sp) }
            }
        },
    )
}

