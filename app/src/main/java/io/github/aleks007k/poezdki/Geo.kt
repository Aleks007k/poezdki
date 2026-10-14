package io.github.aleks007k.poezdki

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Бесплатный геокодер, встроенный в телефон (адрес ↔ координаты).
 * Вызываем только когда приложение на экране. Не получилось — вернём null.
 */
object Geo {
    private const val TIMEOUT_MS = 15_000L

    suspend fun coordsOf(context: Context, text: String): LatLon? =
        lookup(context) { g, done ->
            if (Build.VERSION.SDK_INT >= 33) {
                g.getFromLocationName(text, 1, listener(done))
            } else {
                @Suppress("DEPRECATION")
                done(g.getFromLocationName(text, 1).orEmpty())
            }
        }?.let { LatLon(it.latitude, it.longitude) }

    suspend fun addressOf(context: Context, at: LatLon): String? =
        lookup(context) { g, done ->
            if (Build.VERSION.SDK_INT >= 33) {
                g.getFromLocation(at.lat, at.lon, 1, listener(done))
            } else {
                @Suppress("DEPRECATION")
                done(g.getFromLocation(at.lat, at.lon, 1).orEmpty())
            }
        }?.let(::shortAddress)

    /** «Улица дом», если есть; иначе город. */
    private fun shortAddress(a: Address): String? {
        val street = a.thoroughfare
        val house = a.subThoroughfare
        return when {
            street != null && house != null -> "$street $house"
            street != null -> street
            a.featureName != null && a.featureName != house -> a.featureName
            else -> a.locality ?: a.subLocality
        }
    }

    @RequiresApi(33)
    private fun listener(done: (List<Address>) -> Unit) = object : Geocoder.GeocodeListener {
        override fun onGeocode(addresses: MutableList<Address>) = done(addresses)
        override fun onError(errorMessage: String?) = done(emptyList())
    }

    private suspend fun lookup(
        context: Context,
        call: (Geocoder, (List<Address>) -> Unit) -> Unit,
    ): Address? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    try {
                        call(Geocoder(context)) { list ->
                            if (cont.isActive) cont.resume(list.firstOrNull())
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
    }
}
