package io.github.aleks007k.poezdki

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
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

/** Окно выбора: список приложений и «Ничего не запускать». */
@Composable
fun CompanionPicker(onPick: (LaunchableApp?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchableApps(context) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Big(stringResource(R.string.companion_pick), 20) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(
                        stringResource(R.string.companion_none), color = C.Muted, fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp),
                    )
                }
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

/** Блок на экране «VIP Борт №1»: что запускать вместе с поездкой. */
@Composable
fun CompanionSection(label: String?, onPick: (LaunchableApp?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth(), radius = 18.dp, padding = 0.dp, onClick = { picking = true }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.companion_label), color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label ?: stringResource(R.string.companion_none), fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    color = if (label != null) C.Text else C.Dim, modifier = Modifier.weight(1f),
                )
                Ico(R.drawable.ic_chevron_right, C.Dim, 20.dp)
            }
        }
    }
    if (label != null) Text(stringResource(R.string.companion_warning), color = C.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    if (picking) CompanionPicker(onPick = { onPick(it); picking = false }, onDismiss = { picking = false })
}
