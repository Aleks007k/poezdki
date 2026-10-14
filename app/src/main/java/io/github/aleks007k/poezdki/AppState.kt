package io.github.aleks007k.poezdki

import kotlinx.coroutines.flow.MutableStateFlow

/** Что сейчас показывать на экране поездки. */
data class LiveTrip(
    val startTime: Long,
    val distanceM: Double,
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val stoppedMs: Long = 0,
)

/** Общее состояние внутри приложения: служба записи пишет, экраны читают. */
object AppState {
    /** Идёт ли запись прямо сейчас. null — нет. */
    val live = MutableStateFlow<LiveTrip?>(null)

    /** Меняется, когда поменялись поездки или места, — экраны перечитывают базу. */
    val dataVersion = MutableStateFlow(0)

    fun dataChanged() {
        dataVersion.value++
    }
}
