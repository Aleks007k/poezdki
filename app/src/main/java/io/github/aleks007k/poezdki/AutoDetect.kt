package io.github.aleks007k.poezdki

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Автозапись. Android сам (по датчикам движения, почти без батареи) сообщает
 * «я в машине» — только тогда включаем GPS. В остальное время GPS выключен.
 */
object AutoDetect {
    fun permitted(context: Context): Boolean {
        fun ok(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        if (!ok(Manifest.permission.ACCESS_FINE_LOCATION)) return false
        if (Build.VERSION.SDK_INT >= 29) {
            // Запись из фона возможна только с «Разрешить в любом режиме».
            if (!ok(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return false
            if (!ok(Manifest.permission.ACTIVITY_RECOGNITION)) return false
        }
        return true
    }

    /** Подписаться на «сел в машину». Повторный вызов безопасен. */
    @SuppressLint("MissingPermission") // проверено в permitted()
    fun register(context: Context) {
        if (!permitted(context)) return
        val request = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
        )
        try {
            val client = ActivityRecognition.getClient(context)
            // 1) «Сел в машину» — одно событие в момент посадки.
            client.requestActivityTransitionUpdates(request, pendingIntent(context, 20))
            // 2) Запасной канал: раз в 1,5 минуты «чем ты сейчас занят» — если событие (1) потерялось.
            client.requestActivityUpdates(Config.AUTO_SAMPLE_MS, pendingIntent(context, 21))
        } catch (e: Exception) {
            // Нет сервисов Google на телефоне — автозаписи не будет, остальное работает.
        }
    }

    /** Что мешает автозаписи (для строки состояния). null — всё в порядке. */
    fun problem(context: Context): Int? {
        fun ok(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            !ok(Manifest.permission.ACCESS_FINE_LOCATION) -> R.string.auto_no_location
            Build.VERSION.SDK_INT >= 29 && !ok(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> R.string.auto_no_background
            Build.VERSION.SDK_INT >= 29 && !ok(Manifest.permission.ACTIVITY_RECOGNITION) -> R.string.auto_no_activity
            else -> null
        }
    }

    private fun pendingIntent(context: Context, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, TransitionReceiver::class.java),
        // Сервисы Google дописывают в него событие — поэтому изменяемый.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )
}

/** Android сообщил: «в машине» (событие посадки или очередная проверка). */
class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val inCar = when {
            ActivityTransitionResult.hasResult(intent) ->
                ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty().any {
                    it.activityType == DetectedActivity.IN_VEHICLE &&
                        it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
                }
            ActivityRecognitionResult.hasResult(intent) ->
                (ActivityRecognitionResult.extractResult(intent)?.getActivityConfidence(DetectedActivity.IN_VEHICLE) ?: 0) >=
                    Config.AUTO_MIN_CONFIDENCE
            else -> false
        }
        if (!inCar) return
        val prefs = Prefs(context)
        prefs.lastVehicleSignal = System.currentTimeMillis()
        // Запись уже идёт (служба жива) или недавно сказали «НЕТ» / «Стоп» — ничего не делаем.
        if (TripService.running && prefs.hasActiveTrip) return
        if (System.currentTimeMillis() < prefs.autoMuteUntil) return
        if (!AutoDetect.permitted(context)) return
        if (!locationOn(context)) {
            // Геолокация выключена — скорость не узнать и поездку не записать. Скажем об этом.
            Questions.askLocationOff(context)
            return
        }
        try {
            TripService.startAuto(context)
        } catch (e: Exception) {
            // Android не разрешил запуск из фона — пропускаем эту поездку.
        }
    }
}

private fun locationOn(context: Context): Boolean {
    val lm = context.getSystemService(android.location.LocationManager::class.java)
    return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
    else lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
}

/** После перезагрузки телефона и обновления приложения подписка пропадает — оформляем снова. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoDetect.register(context)
    }
}
