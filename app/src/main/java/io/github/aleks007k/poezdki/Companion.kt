package io.github.aleks007k.poezdki

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Установленное приложение, которое можно открыть. */
data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/** Все приложения с иконкой на рабочем столе; Blitzer — первыми. */
fun launchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(main, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { r ->
            LaunchableApp(
                r.activityInfo.packageName,
                r.loadLabel(pm).toString(),
                try { r.loadIcon(pm).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null },
            )
        }
        .sortedWith(compareBy({ !it.label.contains("blitzer", ignoreCase = true) }, { it.label.lowercase() }))
}

/** Окно выбора приложения. */
@Composable
fun CompanionPicker(onPick: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchableApps(context) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Big(stringResource(R.string.companion_pick), 20) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps.orEmpty(), key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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

/**
 * Блок «Вместе с поездкой»: какой навигатор открывать с маршрутом и какие приложения
 * (до 3) открыть перед ним — Blitzer.de, музыку и т. п.
 */
@Composable
fun CompanionSection(
    navigator: String,
    companions: List<CompanionApp>,
    onNavigator: (String) -> Unit,
    onAdd: (LaunchableApp) -> Unit,
    onRemove: (String) -> Unit,
    onTest: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.navigator_title), color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavChip(stringResource(R.string.nav_google), navigator == NAV_GOOGLE, Modifier.weight(1f)) { onNavigator(NAV_GOOGLE) }
                NavChip(stringResource(R.string.nav_waze), navigator == NAV_WAZE, Modifier.weight(1f)) { onNavigator(NAV_WAZE) }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.companions_more), color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            companions.forEachIndexed { i, app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${app.label}", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemove(app.pkg) }) {
                        Text("✕", color = C.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (companions.size < Config.MAX_COMPANIONS) {
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                        .dashedBorder(C.Line, 14.dp).clickable { picking = true },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.companion_add), color = C.Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
    if (companions.isNotEmpty()) {
        TextButton(onClick = onTest) {
            Text(stringResource(R.string.companion_test), color = C.Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
    Text(stringResource(R.string.companion_warning), color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    if (picking) CompanionPicker(onPick = { onAdd(it); picking = false }, onDismiss = { picking = false })
}

@Composable
private fun NavChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.height(46.dp).clip(shape)
            .background(if (selected) C.Amber else Color.Transparent)
            .border(1.dp, if (selected) C.Amber else C.Line, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) C.OnAmber else C.Soft, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}
