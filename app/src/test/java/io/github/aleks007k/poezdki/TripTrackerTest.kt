package io.github.aleks007k.poezdki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrackerTest {
    private val t0 = 1_000_000_000L
    private val lat0 = 48.0
    private val lon0 = 11.0

    /** Метров на градус широты (примерно). */
    private val mPerDeg = 111_195.0

    /** Едем на север: каждую секунду точка, скорость speedKmh. Возвращает время конца. */
    private fun drive(
        tr: TripTracker, fromTime: Long, fromMeters: Double, seconds: Int, speedKmh: Double,
    ): Pair<Long, Double> {
        var t = fromTime
        var m = fromMeters
        repeat(seconds) {
            t += 1000
            m += speedKmh / 3.6
            tr.onPoint(GpsPoint(t, lat0 + m / mPerDeg, lon0, 5f, speedKmh))
        }
        return t to m
    }

    /** Стоим на месте (скорость 0, лёгкое дрожание). */
    private fun stand(tr: TripTracker, fromTime: Long, atMeters: Double, seconds: Int): Long {
        var t = fromTime
        repeat(seconds) { i ->
            t += 1000
            val jitter = if (i % 2 == 0) 3.0 else -3.0
            tr.onPoint(GpsPoint(t, lat0 + (atMeters + jitter) / mPerDeg, lon0, 8f, 0.0))
            tr.tick(t)?.let { return t }
        }
        return t
    }

    @Test
    fun drivingCountsDistance() {
        val tr = TripTracker(t0, null)
        tr.onPoint(GpsPoint(t0, lat0, lon0, 5f, 0.0))
        drive(tr, t0, 0.0, 600, 60.0) // 10 минут по 60 км/ч = 10 км
        assertEquals(10_000.0, tr.distanceM, 150.0)
    }

    @Test
    fun jitterOnTheSpotAddsNothing() {
        val tr = TripTracker(t0, null)
        stand(tr, t0, 0.0, 300)
        assertEquals(0.0, tr.distanceM, 0.001)
    }

    @Test
    fun badAccuracyAndJumpsAreIgnored() {
        val tr = TripTracker(t0, null)
        val (t, m) = drive(tr, t0, 0.0, 60, 50.0)
        val before = tr.distanceM
        // Плохая точность.
        tr.onPoint(GpsPoint(t + 1000, lat0 + (m + 500) / mPerDeg, lon0, 80f, 50.0))
        // Прыжок на 2 км за секунду.
        tr.onPoint(GpsPoint(t + 2000, lat0 + (m + 2000) / mPerDeg, lon0, 5f, 50.0))
        assertEquals(before, tr.distanceM, 0.001)
    }

    @Test
    fun nearTargetEndsAfterTwoMinutesAtStopMoment() {
        val target = LatLon(lat0 + 5000 / mPerDeg, lon0)
        val tr = TripTracker(t0, target)
        val (t, m) = drive(tr, t0, 0.0, 360, 50.0) // ~5 км
        val endT = stand(tr, t, m, 600)
        val end = tr.tick(endT)
        assertNotNull(end)
        assertEquals(t + 1000, end!!.endTime) // первая секунда стоянки
        assertTrue(endT - t <= Config.NEAR_TARGET_STOP_MS + 2000)
        assertEquals(5000.0, end.distanceM, 100.0)
    }

    @Test
    fun trafficLightDoesNotEndTrip() {
        val tr = TripTracker(t0, LatLon(0.0, 0.0))
        var (t, m) = drive(tr, t0, 0.0, 120, 50.0)
        t = stand(tr, t, m, 90) // светофор 1,5 минуты
        assertNull(tr.tick(t))
        val r = drive(tr, t, m, 120, 50.0)
        assertNull(tr.tick(r.first))
        assertFalse(tr.paused)
    }

    @Test
    fun elsewhereEndsAfterTenMinutesPlusListening() {
        val tr = TripTracker(t0, null)
        val (t, m) = drive(tr, t0, 0.0, 300, 50.0)
        stand(tr, t, m, (Config.STOP_END_MS / 1000).toInt() + 5)
        assertTrue(tr.paused)
        assertNull(tr.tick(t + Config.STOP_END_MS + 10_000))
        val end = tr.tick(t + Config.STOP_END_MS + Config.RESUME_WINDOW_MS + 1000)
        assertNotNull(end)
        assertEquals(t + 1000, end!!.endTime)
    }

    @Test
    fun drivingAgainWithinWindowContinuesSameTrip() {
        val tr = TripTracker(t0, null)
        var (t, m) = drive(tr, t0, 0.0, 300, 50.0)
        t = stand(tr, t, m, 15 * 60) // 15 минут стоим — пауза
        assertTrue(tr.paused)
        // Пешком (5 км/ч) — не продолжение.
        val walk = drive(tr, t, m, 30, 5.0)
        assertTrue(tr.paused)
        // Поехали (40 км/ч) — продолжение.
        val r = drive(tr, walk.first, walk.second, 120, 40.0)
        assertFalse(tr.paused)
        assertNull(tr.tick(r.first))
    }

    @Test
    fun manualStopWhileStandingUsesStopMoment() {
        val tr = TripTracker(t0, null)
        val (t, m) = drive(tr, t0, 0.0, 300, 50.0)
        val d = tr.distanceM
        stand(tr, t, m, 60)
        val end = tr.stopNow(t + 120_000)
        assertEquals(t + 1000, end.endTime)
        assertEquals(d, end.distanceM, 0.001)
    }

    @Test
    fun noGpsAtAllEndsEventually() {
        val tr = TripTracker(t0, null)
        assertNull(tr.tick(t0 + 60_000))
        val end = tr.tick(t0 + Config.STOP_END_MS + Config.RESUME_WINDOW_MS)
        assertNotNull(end)
        assertEquals(0.0, end!!.distanceM, 0.0)
    }

    @Test
    fun nearTargetBeforeDepartureDoesNotEnd() {
        // Нажал «Дом», стоя у дома, и ещё не тронулся — 2-минутное правило не действует.
        val tr = TripTracker(t0, LatLon(lat0, lon0))
        val t = stand(tr, t0, 0.0, 180)
        assertNull(tr.tick(t))
    }

    @Test
    fun maxSpeedAndStoppedTime() {
        val tr = TripTracker(t0, null)
        var (t, m) = drive(tr, t0, 0.0, 120, 50.0)
        val r1 = drive(tr, t, m, 60, 90.0)
        t = stand(tr, r1.first, r1.second, 120) // пробка 2 минуты
        val r2 = drive(tr, t, r1.second, 60, 40.0)
        assertEquals(90.0, tr.maxSpeedKmh, 0.001)
        assertEquals(119_000.0, tr.stoppedMs.toDouble(), 2_000.0)
        // Последняя остановка (конец поездки) в «стоял» не входит.
        stand(tr, r2.first, r2.second, 60)
        val end = tr.stopNow(r2.first + 61_000)
        assertEquals(119_000.0, end.stoppedMs.toDouble(), 2_000.0)
        assertEquals(90.0, end.maxSpeedKmh, 0.001)
    }
}
