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
            ActivityRecognition.getClient(context).requestActivityTransitionUpdates(request, pendingIntent(context))
        } catch (e: Exception) {
            // Нет сервисов Google на телефоне — автозаписи не будет, остальное работает.
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 20, Intent(context, TransitionReceiver::class.java),
        // Сервисы Google дописывают в него событие — поэтому изменяемый.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )
}

/** Android сообщил: «в машине». */
class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val inCar = result.transitionEvents.any {
            it.activityType == DetectedActivity.IN_VEHICLE &&
                it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }
        if (!inCar || Prefs(context).hasActiveTrip || !AutoDetect.permitted(context)) return
        try {
            TripService.startAuto(context)
        } catch (e: Exception) {
            // Android не разрешил запуск из фона — пропускаем эту поездку.
        }
    }
}

/** После перезагрузки телефона и обновления приложения подписка пропадает — оформляем снова. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoDetect.register(context)
    }
}
