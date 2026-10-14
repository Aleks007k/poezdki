package io.github.aleks007k.poezdki

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Запись поездки. Работает «на переднем плане» (с уведомлением «Идёт запись
 * поездки»), поэтому Android не останавливает её, когда открыт Google Maps
 * или погас экран.
 *
 * Каждая точка GPS сразу пишется в базу. Если Android всё же перезапустит
 * службу, поездка восстанавливается из этих точек.
 */
class TripService : Service(), LocationListener {
    private lateinit var prefs: Prefs
    private lateinit var db: Db
    private var tracker: TripTracker? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tracker?.tick(System.currentTimeMillis())?.let { finish(it); return }
            publish()
            handler.postDelayed(this, Config.TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        db = Db.get(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START || action == null) {
            // Запуск через startForegroundService: уведомление нужно показать сразу.
            if (!goForeground()) {
                // Android не разрешил (нет разрешения на местоположение или
                // запуск из фона). Поездку не теряем: продолжим при открытии приложения.
                tracker = null
                stopSelf()
                return START_NOT_STICKY
            }
        }
        when (action) {
            ACTION_START -> if (tracker == null) {
                val now = System.currentTimeMillis()
                val target = intent!!.target()
                db.clearPoints()
                prefs.startTrip(now, intent.getStringExtra(EXTRA_DEST).orEmpty(), target)
                tracker = TripTracker(now, target)
                begin()
            }
            ACTION_TARGET -> {
                intent!!.target()?.let { t ->
                    if (prefs.hasActiveTrip) prefs.setTarget(t)
                    tracker?.target = t
                }
                if (tracker == null) restoreOrStop()
            }
            ACTION_STOP -> {
                if (tracker == null && prefs.hasActiveTrip) restore()
                tracker?.let { finish(it.stopNow(System.currentTimeMillis())) } ?: stopEverything()
            }
            else -> if (tracker == null) restoreOrStop() // перезапуск после закрытия
        }
        return START_STICKY
    }

    private fun restoreOrStop() {
        if (!prefs.hasActiveTrip) {
            stopEverything()
            return
        }
        restore()
        val end = tracker?.tick(System.currentTimeMillis())
        when {
            end != null -> finish(end)
            goForeground() -> begin()
            else -> {
                tracker = null
                stopSelf()
            }
        }
    }

    private fun restore() {
        val t = TripTracker(prefs.activeStart, prefs.activeTarget)
        db.points().forEach(t::onPoint)
        tracker = t
    }

    /** Показать уведомление «Идёт запись поездки» и стать службой переднего плана. */
    private fun goForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        true
    } catch (e: Exception) {
        false
    }

    @SuppressLint("MissingPermission") // разрешение проверяется в первой строке
    private fun begin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopEverything()
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        lm.removeUpdates(this)
        lm.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, Config.GPS_INTERVAL_MS, 0f, this, Looper.getMainLooper(),
        )
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        publish()
    }

    override fun onLocationChanged(location: Location) {
        val t = tracker ?: return
        val p = GpsPoint(
            time = location.time,
            lat = location.latitude,
            lon = location.longitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            speedKmh = if (location.hasSpeed()) location.speed * 3.6 else null,
        )
        if (p.accuracyM <= Config.MAX_ACCURACY_M) db.insertPoint(p)
        t.onPoint(p)
        t.tick(p.time)?.let { finish(it); return }
        publish()
    }

    // На старых Android эти методы обязательны, иначе приложение может упасть.
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Нужен только старым версиям Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private var lastShownKm = ""

    private fun publish() {
        val t = tracker ?: return
        AppState.live.value = LiveTrip(prefs.activeStart, t.distanceM)
        val km = Format.km(t.distanceM)
        if (km != lastShownKm) {
            lastShownKm = km
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun finish(end: TripEnd) {
        val t = tracker
        if (t != null && end.distanceM >= Config.MIN_TRIP_M) {
            db.insertTrip(
                Trip(
                    id = 0,
                    startTime = prefs.activeStart,
                    endTime = end.endTime,
                    start = t.startPoint?.latLon,
                    end = end.endPoint,
                    startAddress = null, // адреса определим, когда приложение будет на экране
                    endAddress = null,
                    distanceM = end.distanceM,
                    litersPer100 = prefs.litersPer100,
                    pricePerLiter = prefs.pricePerLiter,
                )
            )
            AppState.dataChanged()
        }
        stopEverything()
    }

    private fun stopEverything() {
        tracker = null
        handler.removeCallbacks(ticker)
        getSystemService(LocationManager::class.java).removeUpdates(this)
        db.clearPoints()
        prefs.clearTrip()
        AppState.live.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        getSystemService(LocationManager::class.java).removeUpdates(this)
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val km = tracker?.distanceM?.let { getString(R.string.km_value, Format.km(it)) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_recording))
            .setContentText(km)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun Intent.target(): LatLon? =
        if (hasExtra(EXTRA_LAT)) LatLon(getDoubleExtra(EXTRA_LAT, 0.0), getDoubleExtra(EXTRA_LON, 0.0))
        else null

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_TARGET = "target"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"

        fun start(context: Context, destination: String, target: LatLon?) {
            val i = Intent(context, TripService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_DEST, destination)
            target?.let { i.putExtra(EXTRA_LAT, it.lat).putExtra(EXTRA_LON, it.lon) }
            ContextCompat.startForegroundService(context, i)
        }

        fun setTarget(context: Context, target: LatLon) = send(
            context,
            Intent(context, TripService::class.java).setAction(ACTION_TARGET)
                .putExtra(EXTRA_LAT, target.lat).putExtra(EXTRA_LON, target.lon),
        )

        fun stop(context: Context) =
            send(context, Intent(context, TripService::class.java).setAction(ACTION_STOP))

        /** Если поездка шла, а служба почему-то не работает, — запустить снова. */
        fun resumeIfNeeded(context: Context) {
            if (Prefs(context).hasActiveTrip && AppState.live.value == null) {
                ContextCompat.startForegroundService(context, Intent(context, TripService::class.java))
            }
        }

        private fun send(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                // Приложение в фоне и записи нет — делать нечего.
            }
        }
    }
}
