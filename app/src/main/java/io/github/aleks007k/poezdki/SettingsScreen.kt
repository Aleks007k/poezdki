package io.github.aleks007k.poezdki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Связь с машиной для экрана настроек. */
data class CarLinkState(val available: Boolean, val linked: Boolean, val name: String?)

/**
 * Настройки: расходы машины, что открывать в начале поездки (навигатор и приложения —
 * их названия видны только здесь), автозапись и версия.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCar: () -> Unit,
    navigator: String,
    companions: List<CompanionApp>,
    onNavigator: (String) -> Unit,
    onAddCompanion: (LaunchableApp) -> Unit,
    onRemoveCompanion: (String) -> Unit,
    onTestCompanion: () -> Unit,
    carLink: CarLinkState,
    onLinkCar: () -> Unit,
    onUnlinkCar: () -> Unit,
    autoProblem: Int?,
    lastVehicleSignal: Long,
    onFixAuto: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(stringResource(R.string.settings_title), onBack)

        // Расходы машины — отдельный экран.
        Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 16.dp, onClick = onCar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.ic_car, C.Amber, 24.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.car_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_car_hint), color = C.Muted, fontSize = 14.sp)
                }
                Ico(R.drawable.ic_chevron_right, C.Dim, 20.dp)
            }
        }

        // Что открывается в начале поездки.
        SectionTitle(stringResource(R.string.companion_title))
        CompanionSection(navigator, companions, onNavigator, onAddCompanion, onRemoveCompanion, onTestCompanion)
        if (companions.isNotEmpty() && carLink.available) CarLinkPanel(carLink, onLinkCar, onUnlinkCar)

        // Автозапись: работает ли и когда Android последний раз сказал «в машине».
        SectionTitle(stringResource(R.string.auto_title))
        Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (autoProblem != null) {
                    Text(stringResource(autoProblem), color = C.Amber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    OutlineLink(R.drawable.ic_chevron_right, stringResource(R.string.auto_fix), onFixAuto)
                } else {
                    Text(stringResource(R.string.auto_on), color = C.Teal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (lastVehicleSignal > 0) stringResource(R.string.auto_last, shortDate(lastVehicleSignal) + ", " + timeOf(lastVehicleSignal))
                        else stringResource(R.string.auto_never),
                        color = C.Soft, fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.version, appVersion(context)), color = C.Dim, fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Связь с Bluetooth машины: тогда навигатор сам выходит наверх через несколько секунд. */
@Composable
private fun CarLinkPanel(state: CarLinkState, onLink: () -> Unit, onUnlink: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.linked) {
                Text(
                    stringResource(R.string.car_link_done, state.name ?: stringResource(R.string.car_link_your_car)),
                    color = C.Teal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.car_link_done_hint), color = C.Soft, fontSize = 14.sp, lineHeight = 19.sp)
                TextButton(onClick = onUnlink, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.car_unlink), color = C.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(stringResource(R.string.car_link_title), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.car_link_hint), color = C.Soft, fontSize = 14.sp, lineHeight = 19.sp)
                OutlineLink(R.drawable.ic_chevron_right, stringResource(R.string.car_link_action), onLink)
            }
        }
    }
}

/** Выбор машины из Bluetooth‑устройств, к которым подключался телефон. */
@Composable
fun CarPicker(devices: List<CarLink.BtDevice>, onPick: (CarLink.BtDevice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Big(stringResource(R.string.car_pick), 20) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(devices, key = { it.address }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(R.drawable.ic_car, if (d.isCar) C.Amber else C.Dim, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(d.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = C.Muted, fontSize = 18.sp) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Caption(text, C.Muted, Modifier.padding(top = 8.dp))
}

fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}
