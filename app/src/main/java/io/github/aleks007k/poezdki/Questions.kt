package io.github.aleks007k.poezdki

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Уведомление «Едешь на своём авто?» с кнопками ДА / НЕТ. Окна поверх приложений нет. */
object Questions {
    private const val CHANNEL_ID = "question"
    private const val NOTIFICATION_ID = 2
    const val EXTRA_YES = "yes"

    /** Во время автопоездки: запись уже идёт. */
    fun askDuringTrip(context: Context) =
        show(context, context.getString(R.string.q_title), context.getString(R.string.q_text))

    /** Поездка закончилась, а ответа не было: она сохранена, «Нет» её удалит. */
    fun askAfterTrip(context: Context, km: String) =
        show(context, context.getString(R.string.q_title_done), context.getString(R.string.q_text_done, km))

    /** Android заметил «в машине», а геолокация выключена. Нажал — открылись настройки. */
    fun askLocationOff(context: Context) {
        val settings = PendingIntent.getActivity(
            context, 13,
            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            context,
            NotificationCompat.Builder(context, channel(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.gps_off_title))
                .setContentText(context.getString(R.string.gps_off_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.gps_off_text)))
                .setContentIntent(settings)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun channel(context: Context): String {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.q_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        return CHANNEL_ID
    }

    private fun notify(context: Context, n: android.app.Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Уведомления запрещены — сказать не получится.
        }
    }

    private fun show(context: Context, title: String, text: String) {
        fun answer(yes: Boolean) = PendingIntent.getBroadcast(
            context, if (yes) 10 else 11,
            Intent(context, AnswerReceiver::class.java).putExtra(EXTRA_YES, yes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context, 12, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.yes), answer(true))
            .addAction(0, context.getString(R.string.no), answer(false))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        notify(context, n)
    }
}

/** Нажали ДА или НЕТ в уведомлении. */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val yes = intent.getBooleanExtra(Questions.EXTRA_YES, true)
        Questions.cancel(context)
        val autoRunning = prefs.hasActiveTrip && prefs.activeAuto
        when {
            yes && autoRunning -> prefs.autoConfirmed = true
            yes -> prefs.pendingAutoTripId = 0
            autoRunning -> TripService.discard(context)
            else -> {
                val id = prefs.pendingAutoTripId
                if (id > 0) {
                    Db.get(context).deleteTrip(id)
                    prefs.pendingAutoTripId = 0
                    AppState.dataChanged()
                }
            }
        }
    }
}
