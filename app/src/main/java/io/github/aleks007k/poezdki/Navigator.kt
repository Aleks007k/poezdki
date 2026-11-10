package io.github.aleks007k.poezdki

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Открыть выбранный навигатор (Google Maps или Waze) с маршрутом до цели. */
object Navigator {
    /** quiet — не показывать подсказку «Waze не установлен» (повторное открытие). */
    fun open(context: Context, nav: String, destination: String, target: LatLon?, quiet: Boolean = false) {
        if (nav == NAV_WAZE && openWaze(context, destination, target)) return
        if (nav == NAV_WAZE && !quiet) Toast.makeText(context, R.string.waze_missing, Toast.LENGTH_LONG).show()
        openMaps(context, destination)
    }

    /** false — Waze не установлен. */
    private fun openWaze(context: Context, destination: String, target: LatLon?): Boolean {
        val link = target?.let { "https://waze.com/ul?ll=${it.lat},${it.lon}&navigate=yes" }
            ?: "https://waze.com/ul?q=${Uri.encode(destination)}&navigate=yes"
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("com.waze").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun openMaps(context: Context, destination: String) {
        val q = Uri.encode(destination)
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q&mode=d"))
                    .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$q&travelmode=driving"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
            }
        }
    }
}
