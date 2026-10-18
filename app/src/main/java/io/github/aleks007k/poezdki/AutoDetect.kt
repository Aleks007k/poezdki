package io.github.aleks007k.poezdki

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Автозапись. Android сам (по датчикам движения, почти без батареи) сообщает
 * «я в машине» — тогда спрашиваем «Едешь на своём авто?». GPS включается только после «ДА».
 */
object AutoDetect {
    /** Чтобы спросить «Едешь?», нужно только «Физическая активность». */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

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
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val inCar = when {
            // «Сел в машину» — верим, только если событие свежее.
            ActivityTransitionResult.hasResult(intent) ->
                ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty().any {
                    it.activityType == DetectedActivity.IN_VEHICLE &&
                        it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                        AutoRules.freshEvent((SystemClock.elapsedRealtimeNanos() - it.elapsedRealTimeNanos) / 1_000_000)
                }
            // Запасной канал: верим, только если две проверки подряд сказали «в машине».
            ActivityRecognitionResult.hasResult(intent) -> {
                val sure = (ActivityRecognitionResult.extractResult(intent)
                    ?.getActivityConfidence(DetectedActivity.IN_VEHICLE) ?: 0) >= Config.AUTO_MIN_CONFIDENCE
                val confirmed = sure && AutoRules.pairConfirms(prefs.lastSampleInCar, now)
                prefs.lastSampleInCar = if (sure) now else 0L
                confirmed
            }
            else -> false
        }
        if (!inCar) return
        prefs.lastVehicleSignal = now
        // Запись уже идёт, недавно сказали «НЕТ» / «Стоп» или вопрос уже висит — молчим.
        if (TripService.running && prefs.hasActiveTrip) return
        if (now < prefs.autoMuteUntil) return
        if (now < prefs.askedUntil) return
        if (!AutoDetect.permitted(context)) return
        prefs.askedUntil = now + Config.ASK_TIMEOUT_MS
        Questions.askDrive(context)
    }
}

/** После перезагрузки телефона и обновления приложения подписка пропадает — оформляем снова. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoDetect.register(context)
    }
}
