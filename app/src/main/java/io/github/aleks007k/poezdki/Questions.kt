package io.github.aleks007k.poezdki

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Единственный вопрос от приложения: «Едешь на своём авто?» с кнопками ДА / НЕТ.
 * ДА — открывается приложение и начинает запись (если геолокация выключена,
 * Android сам предложит её включить). НЕТ — вопрос исчезает, полчаса тишины.
 */
object Questions {
    private const val CHANNEL_ID = "question"
    private const val NOTIFICATION_ID = 2

    /** Открыть приложение с этим действием = начать запись. */
    const val ACTION_DRIVE_YES = "io.github.aleks007k.poezdki.DRIVE_YES"
    const val EXTRA_TOKEN = "token"

    fun askDrive(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.q_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        val token = java.util.UUID.randomUUID().toString()
        Prefs(context).driveToken = token
        val yes = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).setAction(ACTION_DRIVE_YES).putExtra(EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val no = PendingIntent.getBroadcast(
            context, 11, Intent(context, AnswerReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.q_title))
            .setContentText(context.getString(R.string.q_text))
            .setContentIntent(yes) // нажал на само уведомление — тоже «ДА»
            .setDeleteIntent(no) // смахнул — значит «НЕТ»
            .addAction(0, context.getString(R.string.yes), yes)
            .addAction(0, context.getString(R.string.no), no)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(Config.ASK_TIMEOUT_MS) // не ответил — само исчезнет
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Уведомления запрещены — спросить не получится.
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

/** «НЕТ» или смахнул вопрос: не спрашивать до конца этой поездки (рабочая машина, автобус, такси). */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Questions.cancel(context)
        val prefs = Prefs(context)
        prefs.driveToken = ""
        prefs.askOpen = false
        prefs.muteAuto(Config.AUTO_MUTE_AFTER_NO_MS)
        prefs.decline(System.currentTimeMillis())
    }
}
