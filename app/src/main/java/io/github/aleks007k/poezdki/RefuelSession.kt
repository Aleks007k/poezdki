package io.github.aleks007k.poezdki

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Открытая форма заправки и снимок с камеры.
 *
 * Живут не в экране, а в приложении: при повороте экрана Android пересоздаёт экран,
 * и раньше недописанная форма пропадала. Если Android закрыл всё приложение, пока
 * открыта камера (бывает при нехватке памяти), форма возвращается из [save]/[restore].
 */
object RefuelSession {
    var draft by mutableStateOf<RefuelDraft?>(null)

    /** Куда камера сохраняет снимок. */
    var cameraPath: String? = null

    /** Камеру открыли кнопкой «Фото чека»: передумал снимать — форму не оставляем. */
    var cameraOpenedForm = false

    /** Для работы, которая не должна обрываться при повороте экрана (чтение чека, запись в базу). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun save(out: Bundle) {
        val d = draft ?: return
        out.putBundle(
            KEY,
            Bundle().apply {
                putLong("id", d.id)
                putString("date", d.date.toString())
                putString("liters", d.liters)
                putString("price", d.price)
                putString("total", d.total)
                putString("currency", d.currency)
                putString("rate", d.rate)
                putString("odometer", d.odometer)
                putBoolean("full", d.full)
                putString("photo", d.photo)
                putBoolean("scanning", d.scanning)
                putBoolean("scanEmpty", d.scanEmpty)
                putString("cameraPath", cameraPath)
                putBoolean("cameraOpenedForm", cameraOpenedForm)
            },
        )
    }

    /** Вернуть форму после перезапуска приложения. true — форма была в процессе чтения чека. */
    fun restore(saved: Bundle?): Boolean {
        if (draft != null) return false // приложение не закрывалось — всё и так на месте
        val b = saved?.getBundle(KEY) ?: return false
        draft = RefuelDraft(
            id = b.getLong("id"),
            date = java.time.LocalDate.parse(b.getString("date")),
            liters = b.getString("liters").orEmpty(),
            price = b.getString("price").orEmpty(),
            total = b.getString("total").orEmpty(),
            currency = b.getString("currency") ?: "EUR",
            rate = b.getString("rate").orEmpty(),
            odometer = b.getString("odometer").orEmpty(),
            full = b.getBoolean("full"),
            photo = b.getString("photo"),
            scanEmpty = b.getBoolean("scanEmpty"),
        )
        cameraPath = b.getString("cameraPath")
        cameraOpenedForm = b.getBoolean("cameraOpenedForm")
        return b.getBoolean("scanning")
    }

    private const val KEY = "refuel_draft"
}
