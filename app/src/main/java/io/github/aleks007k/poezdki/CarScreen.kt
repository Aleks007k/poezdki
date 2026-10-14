package io.github.aleks007k.poezdki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Double.input() = if (this > 0) Format.forInput(this) else ""

/** Экран «Машина»: все расходы, как считают автоклубы. Пустое поле = не считается. */
@Composable
fun CarScreen(car: CarCosts, onBack: () -> Unit, onSave: (CarCosts) -> Unit) {
    val context = LocalContext.current
    var l100 by rememberSaveable { mutableStateOf(car.litersPer100.input()) }
    var price by rememberSaveable { mutableStateOf(car.pricePerLiter.input()) }
    var value by rememberSaveable { mutableStateOf(car.carValue.input()) }
    var years by rememberSaveable { mutableStateOf(car.years.input()) }
    var sell by rememberSaveable { mutableStateOf(car.sellPrice.input()) }
    var ins by rememberSaveable { mutableStateOf(car.insurance.input()) }
    var tax by rememberSaveable { mutableStateOf(car.tax.input()) }
    var service by rememberSaveable { mutableStateOf(car.service.input()) }
    var tires by rememberSaveable { mutableStateOf(car.tires.input()) }
    var kmYear by rememberSaveable { mutableStateOf(car.kmPerYear.input()) }
    var fareBase by rememberSaveable { mutableStateOf(car.fareBase.input()) }
    var fareKm by rememberSaveable { mutableStateOf(car.farePerKm?.input().orEmpty()) }
    var fareMin by rememberSaveable { mutableStateOf(car.farePerMinute.input()) }

    val z = Format::parseOrZero
    val edited = CarCosts(
        litersPer100 = z(l100), pricePerLiter = z(price),
        carValue = z(value), sellPrice = z(sell), years = z(years), kmPerYear = z(kmYear),
        insurance = z(ins), tax = z(tax), service = z(service), tires = z(tires),
        fareBase = z(fareBase), farePerKm = Format.parse(fareKm), farePerMinute = z(fareMin),
    )
    val valid = edited.litersPer100 > 0 && edited.pricePerLiter > 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(stringResource(R.string.car_title), onBack)

        // Итог: сколько стоит 1 км.
        Panel(Modifier.fillMaxWidth(), radius = 22.dp, padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.per_km), color = C.Soft, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Big(moneyText(context, edited.totalPerKm), 26)
                }
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val parts = listOf(edited.fuelPerKm to C.Amber, edited.wearPerKm to C.Blue, edited.upkeepPerKm to C.Teal).filter { it.first > 0 }
                    if (parts.isEmpty()) Box(Modifier.weight(1f).height(8.dp).background(C.Track))
                    parts.forEach { (v, c) -> Box(Modifier.weight(v.toFloat()).height(8.dp).background(c)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(context.getString(R.string.legend_fuel, Format.number(edited.fuelPerKm, 2)), color = C.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(context.getString(R.string.legend_wear, Format.number(edited.wearPerKm, 2)), color = C.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(context.getString(R.string.legend_upkeep, Format.number(edited.upkeepPerKm, 2)), color = C.Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Section(stringResource(R.string.sec_fuel), C.Amber)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.liters_per_100), l100, { l100 = it }, Modifier.weight(1f))
            Field(stringResource(R.string.price_per_liter), price, { price = it }, Modifier.weight(1f))
        }

        Section(stringResource(R.string.sec_wear), C.Blue)
        Field(stringResource(R.string.car_value), value, { value = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.sell_years), years, { years = it }, Modifier.weight(1f))
            Field(stringResource(R.string.sell_price), sell, { sell = it }, Modifier.weight(1f))
        }

        Section(stringResource(R.string.sec_upkeep), C.Teal)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.insurance), ins, { ins = it }, Modifier.weight(1f))
            Field(stringResource(R.string.tax), tax, { tax = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.service), service, { service = it }, Modifier.weight(1f))
            Field(stringResource(R.string.tires), tires, { tires = it }, Modifier.weight(1f))
        }

        Section(stringResource(R.string.sec_mileage), C.Muted)
        Field(stringResource(R.string.km_per_year), kmYear, { kmYear = it }, Modifier.fillMaxWidth())

        Section(stringResource(R.string.sec_fare), C.Amber)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.fare_base), fareBase, { fareBase = it }, Modifier.weight(1f), placeholder = "0")
            Field(stringResource(R.string.fare_km), fareKm, { fareKm = it }, Modifier.weight(1f),
                placeholder = Format.number(edited.totalPerKm, 2))
            Field(stringResource(R.string.fare_min), fareMin, { fareMin = it }, Modifier.weight(1f), placeholder = "0")
        }
        Text(stringResource(R.string.fare_hint), color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp)

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.car_hint), color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        AmberButton(stringResource(R.string.save), onClick = { onSave(edited) }, enabled = valid)
    }
}

@Composable
private fun Section(text: String, color: Color) {
    Caption(text, color, Modifier.padding(top = 8.dp))
}

/** Первый запуск: расход и цена литра. */
@Composable
fun FuelSetupScreen(car: CarCosts, onSave: (Double, Double) -> Unit) {
    var l100 by rememberSaveable { mutableStateOf(car.litersPer100.input()) }
    var price by rememberSaveable { mutableStateOf(car.pricePerLiter.input()) }
    val l = Format.parse(l100)
    val p = Format.parse(price)
    Column(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.setup_fuel_title), fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp)
        Spacer(Modifier.height(8.dp))
        Field(stringResource(R.string.liters_per_100), l100, { l100 = it }, Modifier.fillMaxWidth())
        Field(stringResource(R.string.price_per_liter), price, { price = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        AmberButton(stringResource(R.string.next), onClick = { if (l != null && p != null) onSave(l, p) }, enabled = l != null && p != null)
    }
}

/** Первый запуск: разрешения и батарея. */
@Composable
fun SetupStepScreen(step: SetupStep, onAction: () -> Unit, onBatteryDone: () -> Unit) {
    val (text, button) = when (step) {
        SetupStep.LOCATION -> R.string.perm_location_text to R.string.allow
        SetupStep.BACKGROUND -> R.string.perm_background_text to R.string.open_settings
        SetupStep.NOTIFICATIONS -> R.string.perm_notifications_text to R.string.allow
        else -> R.string.battery_text to R.string.open_settings
    }
    Column(
        Modifier.fillMaxSize().padding(vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(text), fontSize = 24.sp, lineHeight = 32.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(40.dp))
        AmberButton(stringResource(button), onClick = onAction)
        if (step == SetupStep.BATTERY) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onBatteryDone,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = C.Muted),
            ) { Text(stringResource(R.string.done), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
