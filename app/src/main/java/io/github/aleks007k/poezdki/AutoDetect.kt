package io.github.aleks007k.poezdki

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

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

    /**
     * Примерная точка по Wi‑Fi и вышкам связи (GPS не включается, батарея почти не тратится).
     * null — геолокация выключена, нет разрешения или не успели за 8 секунд.
     */
    @SuppressLint("MissingPermission") // проверено ниже
    fun roughFix(context: Context, done: (Fix?) -> Unit) {
        fun ok(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val located = ok(Manifest.permission.ACCESS_FINE_LOCATION) || ok(Manifest.permission.ACCESS_COARSE_LOCATION)
        val inBackground = Build.VERSION.SDK_INT < 29 || ok(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (!located || !inBackground) {
            done(null)
            return
        }
        var answered = false
        fun answer(f: Fix?) {
            if (!answered) {
                answered = true
                done(f)
            }
        }
        val cancel = CancellationTokenSource()
        Handler(Looper.getMainLooper()).postDelayed({ cancel.cancel(); answer(null) }, 8_000)
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
                .addOnSuccessListener { loc ->
                    answer(loc?.let { Fix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else 500f, System.currentTimeMillis()) })
                }
                .addOnFailureListener { answer(null) }
        } catch (e: Exception) {
            answer(null)
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

/**
 * Android сообщил «в машине» (событие посадки или очередная проверка раз в 1,5 мин).
 * Сам по себе этот сигнал часто ошибается (ходьба дома), поэтому проверяем реальный
 * сдвиг по примерной точке (Wi‑Fi/вышки, без GPS): спрашиваем, только если правда едем.
 */
class TransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val inCar = when {
            ActivityTransitionResult.hasResult(intent) ->
                ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty().any {
                    it.activityType == DetectedActivity.IN_VEHICLE &&
                        it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                        AutoRules.freshEvent((SystemClock.elapsedRealtimeNanos() - it.elapsedRealTimeNanos) / 1_000_000)
                }
            ActivityRecognitionResult.hasResult(intent) ->
                (ActivityRecognitionResult.extractResult(intent)
                    ?.getActivityConfidence(DetectedActivity.IN_VEHICLE) ?: 0) >= Config.AUTO_MIN_CONFIDENCE
            else -> false
        }
        if (!inCar) {
            // Проверка сказала «не в машине» — серия прерывается.
            if (ActivityRecognitionResult.hasResult(intent)) {
                prefs.inCarStreak = 0
                prefs.lastFix = null
                // После «НЕТ»: ~10 минут не в машине — та поездка кончилась, можно снова спрашивать.
                if (prefs.declinedDrive) {
                    prefs.notInCarStreak = prefs.notInCarStreak + 1
                    if (prefs.notInCarStreak >= Config.DECLINE_RESET_SAMPLES) prefs.declinedDrive = false
                }
            }
            return
        }
        prefs.lastVehicleSignal = now
        prefs.inCarStreak = prefs.inCarStreak + 1
        prefs.notInCarStreak = 0
        // Сказал «НЕТ» на эту поездку (рабочая машина) — молчим, пока не выйдешь из машины.
        if (prefs.declinedDrive) return
        // Запись уже идёт, недавно сказали «НЕТ» / «Стоп» или вопрос уже висит — молчим.
        if (TripService.running && prefs.hasActiveTrip) return
        if (now < prefs.autoMuteUntil || now < prefs.askedUntil) return
        if (!AutoDetect.permitted(context)) return

        val pending = goAsync()
        AutoDetect.roughFix(context) { fix ->
            try {
                val moving = if (fix == null) {
                    // Геолокация выключена: только длинная серия «в машине».
                    prefs.inCarStreak >= Config.AUTO_STREAK_WITHOUT_LOCATION
                } else {
                    val prev = prefs.lastFix
                    // Два сигнала почти подряд — первую точку оставляем, сравниваем позже.
                    if (prev == null || fix.time - prev.time >= Config.MOVE_MIN_GAP_MS) prefs.lastFix = fix
                    AutoRules.movedLikeCar(prev, fix)
                }
                if (moving) {
                    prefs.askedUntil = System.currentTimeMillis() + Config.ASK_TIMEOUT_MS
                    prefs.lastFix = null
                    prefs.inCarStreak = 0
                    Questions.askDrive(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** После перезагрузки телефона и обновления приложения подписка пропадает — оформляем снова. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoDetect.register(context)
    }
}
