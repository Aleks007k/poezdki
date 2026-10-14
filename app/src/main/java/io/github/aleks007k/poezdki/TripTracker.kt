package io.github.aleks007k.poezdki

/** Одна точка от GPS. speedKmh = null, если телефон скорость не сообщил. */
data class GpsPoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedKmh: Double?,
) {
    val latLon get() = LatLon(lat, lon)
}

/** Чем закончилась поездка: когда машина остановилась, где, сколько проехала. */
data class TripEnd(
    val endTime: Long,
    val endPoint: LatLon?,
    val distanceM: Double,
    val maxSpeedKmh: Double = 0.0,
    /** Сколько простояли (пробки, светофоры) — без последней остановки. */
    val stoppedMs: Long = 0,
)

/**
 * Считает километры и решает, когда поездка закончилась.
 * Ничего не знает об Android — поэтому его можно проверить тестами.
 *
 * Правила:
 * - стою = скорость ниже [Config.STOP_SPEED_KMH];
 * - стою рядом с целью [Config.NEAR_TARGET_STOP_MS] — конец;
 * - стою где угодно [Config.STOP_END_MS] — «пауза»: ещё [Config.RESUME_WINDOW_MS]
 *   ждём, вдруг поеду быстрее [Config.RESUME_SPEED_KMH] — тогда поездка продолжается;
 * - конец поездки — момент, когда машина остановилась (ожидание и ходьба не входят).
 */
class TripTracker(private val startTime: Long, var target: LatLon?) {
    var distanceM = 0.0
        private set
    var startPoint: GpsPoint? = null
        private set

    /** Поездка на паузе: стоим дольше 10 минут, ждём, не поедем ли дальше. */
    var paused = false
        private set

    /** Текущая, максимальная скорость и время стоянок — для экрана поездки. */
    var speedKmh = 0.0
        private set
    var maxSpeedKmh = 0.0
        private set
    var stoppedMs = 0L
        private set
    private var lastMoving = true
    private var stoppedAtStop = 0L

    private var lastPoint: GpsPoint? = null
    private var anchor: GpsPoint? = null
    private var hasMoved = false
    private var stopStart: Long? = null
    private var stopPoint: GpsPoint? = null
    private var distanceAtStop = 0.0
    private var end: TripEnd? = null

    fun onPoint(p: GpsPoint) {
        if (end != null || p.accuracyM > Config.MAX_ACCURACY_M) return
        val prev = lastPoint
        if (prev != null && p.time <= prev.time) return

        // «Прыжок»: от последней засчитанной точки сюда пришлось бы лететь.
        anchor?.let { a ->
            val d = distanceM(a.lat, a.lon, p.lat, p.lon)
            val dt = (p.time - a.time) / 1000.0
            if (d > Config.CRAWL_STEP_M && d / dt * 3.6 > Config.MAX_JUMP_SPEED_KMH) return
        }

        val speed = p.speedKmh ?: prev?.let {
            distanceM(it.lat, it.lon, p.lat, p.lon) / ((p.time - it.time) / 1000.0) * 3.6
        } ?: 0.0
        val moving = speed >= Config.STOP_SPEED_KMH

        speedKmh = speed
        // Максимум — только по скорости от самого GPS (она точнее, чем по точкам).
        p.speedKmh?.let { if (it <= Config.MAX_JUMP_SPEED_KMH) maxSpeedKmh = maxOf(maxSpeedKmh, it) }
        if (prev != null && !moving && !lastMoving) {
            val dt = p.time - prev.time
            if (dt <= Config.MAX_STOP_GAP_MS) stoppedMs += dt
        }
        lastMoving = moving

        lastPoint = p
        if (startPoint == null) {
            startPoint = p
            anchor = p
        }

        // Километры: дрожание на месте не считаем, медленную пробку — считаем.
        anchor?.let { a ->
            val d = distanceM(a.lat, a.lon, p.lat, p.lon)
            if (d >= Config.MIN_STEP_M && (moving || d >= Config.CRAWL_STEP_M)) {
                distanceM += d
                anchor = p
            }
        }

        if (paused) {
            if (speed > Config.RESUME_SPEED_KMH) {
                paused = false
                stopStart = null
                hasMoved = true
            }
        } else if (moving) {
            stopStart = null
            hasMoved = true
        } else if (stopStart == null) {
            stopStart = p.time
            stopPoint = p
            distanceAtStop = distanceM
            stoppedAtStop = stoppedMs
        }
    }

    /** Проверить таймеры. Возвращает конец поездки, если он наступил. */
    fun tick(now: Long): TripEnd? {
        end?.let { return it }
        val last = lastPoint
        val listenAll = Config.STOP_END_MS + Config.RESUME_WINDOW_MS
        if (last == null) {
            // GPS так и не поймал ни одной точки.
            if (now - startTime >= listenAll) end = TripEnd(now, null, 0.0)
            return end
        }
        val since = stopStart
        if (since == null) {
            // Едем, но точки давно не приходят (GPS пропал надолго).
            if (now - last.time >= listenAll) end = TripEnd(last.time, last.latLon, distanceM, maxSpeedKmh, stoppedMs)
            return end
        }
        val stoppedFor = now - since
        val nearTarget = hasMoved && target?.let { t ->
            stopPoint?.let { distanceM(it.latLon, t) <= Config.NEAR_TARGET_M }
        } == true
        if (nearTarget && stoppedFor >= Config.NEAR_TARGET_STOP_MS) {
            end = endAtStop()
        } else if (stoppedFor >= listenAll) {
            end = endAtStop()
        } else if (stoppedFor >= Config.STOP_END_MS) {
            paused = true
        }
        return end
    }

    /** Остановить вручную (кнопка «Стоп»). */
    fun stopNow(now: Long): TripEnd {
        end?.let { return it }
        val e = if (stopStart != null) endAtStop()
        else TripEnd(now, lastPoint?.latLon, distanceM, maxSpeedKmh, stoppedMs)
        end = e
        return e
    }

    private fun endAtStop() =
        TripEnd(stopStart!!, stopPoint?.latLon, distanceAtStop, maxSpeedKmh, stoppedAtStop)
}
