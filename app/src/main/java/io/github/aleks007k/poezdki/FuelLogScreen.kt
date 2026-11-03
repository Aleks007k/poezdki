package io.github.aleks007k.poezdki

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------------- Журнал заправок ----------------

@Composable
fun FuelLogScreen(
    db: Db, car: CarCosts,
    onOpen: (Refuel) -> Unit, onCamera: () -> Unit, onManual: () -> Unit,
    onUseConsumption: (Double) -> Unit, onBack: () -> Unit,
) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val version by AppState.dataVersion.collectAsState()
    val zone = ZoneId.systemDefault()
    val month = YearMonth.now().plusMonths(offset.toLong())
    var list by remember { mutableStateOf<List<Refuel>>(emptyList()) }
    var real by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(month, version) {
        val (m, all) = withContext(Dispatchers.IO) {
            db.refuels(month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()) to db.refuels()
        }
        list = m
        real = FuelMath.realConsumption(all)
    }
    FuelLogContent(
        DateTimeFormatter.ofPattern("LLLL yyyy").format(month).replaceFirstChar { it.uppercase() },
        canGoLater = offset < 0, list, real, car.litersPer100,
        onEarlier = { offset-- }, onLater = { offset++ },
        onOpen = onOpen, onCamera = onCamera, onManual = onManual, onUseConsumption = onUseConsumption, onBack = onBack,
    )
}

@Composable
fun FuelLogContent(
    monthText: String,
    canGoLater: Boolean,
    refuels: List<Refuel>,
    realConsumption: Double?,
    currentL100: Double,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onOpen: (Refuel) -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
    onUseConsumption: (Double) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val totalEur = refuels.sumOf { it.totalEur }
    val liters = refuels.sumOf { it.liters }
    Column(Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.refuels_title), onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton(R.drawable.ic_chevron_left, stringResource(R.string.previous), C.Text, onEarlier)
            Text(monthText, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            SquareButton(R.drawable.ic_chevron_right, stringResource(R.string.next_period), C.Text, onLater, enabled = canGoLater)
        }

        // Главное — сколько потратил на топливо за месяц.
        Panel(Modifier.fillMaxWidth(), radius = 22.dp, padding = 18.dp) {
            Caption(stringResource(R.string.refuels_month_total))
            Big(moneyText(context, totalEur), 40, C.Amber)
            Text(
                context.getString(R.string.refuels_summary, refuels.size, Format.number(liters, 1),
                    if (liters > 0) Format.money(totalEur / liters) else "–"),
                color = C.Soft, fontSize = 15.sp,
            )
        }

        realConsumption?.let { real ->
            Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 16.dp) {
                Text(context.getString(R.string.real_consumption, Format.number(real, 1)), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(stringResource(R.string.real_consumption_hint), color = C.Muted, fontSize = 13.sp)
                if (kotlin.math.abs(real - currentL100) >= 0.05) {
                    Spacer(Modifier.height(8.dp))
                    OutlineLink(R.drawable.ic_chevron_right, stringResource(R.string.use_in_calc), { onUseConsumption(real) })
                }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (refuels.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.refuels_empty), color = C.Muted, fontSize = 17.sp, textAlign = TextAlign.Center)
                }
            }
            items(refuels, key = { it.id }) { r -> RefuelRow(r) { onOpen(r) } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(20.dp)).background(C.Amber).clickable(onClick = onCamera),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(R.drawable.ic_camera, C.OnAmber, 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.refuel_photo), color = C.OnAmber, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Box(
                Modifier.height(64.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, C.Line, RoundedCornerShape(20.dp))
                    .clickable(onClick = onManual).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.refuel_manual), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        }
    }
}

@Composable
private fun RefuelRow(r: Refuel, onClick: () -> Unit) {
    val context = LocalContext.current
    val cur = Currencies.byCode(r.currency)
    Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 0.dp, onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateTimeFormatter.ofPattern("EE, d MMM").format(Instant.ofEpochMilli(r.time).atZone(ZoneId.systemDefault()))
                        .replaceFirstChar { it.uppercase() },
                    color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    context.getString(R.string.refuel_line, Format.number(r.liters, 2), Format.number(r.pricePerLiter, 3), cur.sign) +
                        if (r.full) " · " + context.getString(R.string.full_tank_short) else "",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Big(moneyText(context, r.totalEur), 17)
                if (r.currency != "EUR") Text("${Format.number(r.total, 2)} ${cur.sign}", color = C.Muted, fontSize = 13.sp)
            }
            if (r.photo != null) {
                Spacer(Modifier.width(8.dp))
                Ico(R.drawable.ic_camera, C.Dim, 18.dp)
            }
        }
    }
}

// ---------------- Форма заправки ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelForm(
    draft: RefuelDraft,
    onChange: (RefuelDraft) -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var pickDate by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val cur = Currencies.byCode(draft.currency)
    val ready = draft.toRefuel()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(stringResource(if (draft.id > 0) R.string.refuel_edit else R.string.refuel_new), onCancel)

        // Фото чека: превью или кнопки «Камера / Галерея».
        if (draft.photo != null) {
            var thumb by remember(draft.photo) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(draft.photo) { thumb = Receipts.load(draft.photo, 600)?.asImageBitmap() }
            Box(
                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(C.Panel)
                    .clickable { showPhoto = true },
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (draft.scanning) ScanningBadge()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoButton(Modifier.weight(1f), R.drawable.ic_camera, stringResource(R.string.photo_camera), onCamera)
                PhotoButton(Modifier.weight(1f), R.drawable.ic_gallery, stringResource(R.string.photo_gallery), onGallery)
            }
        }
        if (draft.scanEmpty) Text(stringResource(R.string.scan_empty), color = C.Amber, fontSize = 14.sp)

        // Дата.
        Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 0.dp, onClick = { pickDate = true }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.refuel_date), color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Big(DateTimeFormatter.ofPattern("d MMMM yyyy").format(draft.date), 20)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.refuel_liters), draft.liters, { onChange(draft.copy(liters = it)) }, Modifier.weight(1f),
                placeholder = ready?.liters?.takeIf { draft.liters.isBlank() }?.let { Format.number(it, 2) }.orEmpty())
            Field(context.getString(R.string.refuel_price, cur.sign), draft.price, { onChange(draft.copy(price = it)) }, Modifier.weight(1f),
                placeholder = ready?.pricePerLiter?.takeIf { draft.price.isBlank() }?.let { Format.number(it, 3) }.orEmpty())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(context.getString(R.string.refuel_total, cur.sign), draft.total, { onChange(draft.copy(total = it)) }, Modifier.weight(1f),
                placeholder = ready?.total?.takeIf { draft.total.isBlank() }?.let { Format.number(it, 2) }.orEmpty())
            // Валюта.
            Panel(Modifier.weight(1f), radius = 18.dp, padding = 0.dp, onClick = { pickCurrency = true }) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.refuel_currency), color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Big(cur.sign, 20, modifier = Modifier.weight(1f))
                        Ico(R.drawable.ic_chevron_down, C.Dim, 18.dp)
                    }
                }
            }
        }
        if (draft.currency != "EUR") {
            Field(context.getString(R.string.refuel_rate, cur.sign), draft.rate, { onChange(draft.copy(rate = it)) }, Modifier.fillMaxWidth())
            ready?.let { Text(context.getString(R.string.refuel_in_eur, Format.money(it.totalEur)), color = C.Soft, fontSize = 15.sp) }
        }

        Field(stringResource(R.string.refuel_odometer), draft.odometer, { onChange(draft.copy(odometer = it)) }, Modifier.fillMaxWidth())
        Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 0.dp) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.full_tank), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(stringResource(R.string.full_tank_hint), color = C.Muted, fontSize = 13.sp)
                }
                Switch(
                    checked = draft.full, onCheckedChange = { onChange(draft.copy(full = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = C.Amber, checkedThumbColor = C.OnAmber,
                        uncheckedTrackColor = C.Track, uncheckedThumbColor = C.Muted, uncheckedBorderColor = C.Line,
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        AmberButton(stringResource(R.string.save), onClick = onSave, enabled = ready != null && !draft.scanning)
        if (draft.id > 0) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .border(1.dp, C.RedLine, RoundedCornerShape(18.dp)).clickable { askDelete = true },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.delete), color = C.RedSoft, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.done), color = C.Amber, fontWeight = FontWeight.Bold) }
            },
        ) { DatePicker(state = state) }
    }
    if (pickCurrency) CurrencyPicker(onPick = { c -> onChange(draft.copy(currency = c)); pickCurrency = false }) { pickCurrency = false }
    if (showPhoto && draft.photo != null) PhotoViewer(draft.photo) { showPhoto = false }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = C.Panel,
            title = { Big(stringResource(R.string.refuel_delete_question), 20) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = C.RedSoft, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.cancel), color = C.Muted, fontSize = 18.sp) } },
        )
    }
}

@Composable
private fun ScanningBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(C.Bg.copy(alpha = 0.85f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = C.Amber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.scanning), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun PhotoButton(modifier: Modifier, icon: Int, text: String, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp).dashedBorder(C.Line, 20.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Ico(icon, C.Amber, 30.dp)
        Spacer(Modifier.height(6.dp))
        Text(text, color = C.Soft, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun CurrencyPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Big(stringResource(R.string.refuel_currency), 20) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(Currencies.all, key = { it.code }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c.code) }.padding(vertical = 12.dp)) {
                        Text(c.sign, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(90.dp))
                        Text(c.code, color = C.Muted, fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = C.Muted, fontSize = 18.sp) } },
    )
}

/** Фото чека во весь экран. */
@Composable
fun PhotoViewer(path: String, onClose: () -> Unit) {
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { bmp = Receipts.load(path, 2000)?.asImageBitmap() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Bg).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp)) }
        }
    }
}

// ---------------- Курсы валют ----------------

/** Полоска «Курс на ноябрь: 1 € = 4,30 zł — не изменился?» (раз в месяц, если были другие валюты). */
@Composable
fun RatesBanner(rates: Map<String, Double>, monthName: String, onOk: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.AmberDark).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(context.getString(R.string.rates_title, monthName), color = C.AmberSoft, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        rates.forEach { (code, r) ->
            Text("1 € = ${Format.number(r, 4)} ${Currencies.byCode(code).sign}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOk) { Text(stringResource(R.string.rates_same), color = C.Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.rates_change), color = C.Soft, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
fun RatesDialog(rates: Map<String, Double>, onSave: (Map<String, Double>) -> Unit, onDismiss: () -> Unit) {
    var values by remember { mutableStateOf(rates.mapValues { Format.number(it.value, 4) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Big(stringResource(R.string.rates_edit_title), 20) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                values.forEach { (code, v) ->
                    Field("1 € = … ${Currencies.byCode(code).sign}", v, { values = values + (code to it) }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values.mapNotNull { (k, v) -> Format.parse(v)?.let { k to it } }.toMap()) }) {
                Text(stringResource(R.string.save), color = C.Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = C.Muted, fontSize = 18.sp) } },
    )
}

