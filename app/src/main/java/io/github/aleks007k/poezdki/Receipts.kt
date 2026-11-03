package io.github.aleks007k.poezdki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Фото чеков: хранение в папке приложения и чтение текста с них. */
object Receipts {
    /** Самая длинная сторона сохранённого фото — чтобы чек занимал ~300 КБ, а не 5 МБ. */
    private const val MAX_SIDE = 1800

    private fun dir(context: Context) = File(context.filesDir, "receipts").apply { mkdirs() }

    /** Куда камера положит снимок (временный файл) и его адрес для камеры. */
    fun cameraTarget(context: Context): Pair<File, Uri> {
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture.jpg")
        f.delete()
        return f to FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /**
     * Повернуть как надо, уменьшить и сохранить в папку чеков.
     * Возвращает файл и картинку (для распознавания). null — не получилось прочитать фото.
     */
    suspend fun store(context: Context, source: Uri): Pair<File, Bitmap>? = withContext(Dispatchers.IO) {
        try {
            val rotation = context.contentResolver.openInputStream(source)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val raw = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(raw.width, raw.height))
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            val file = File(dir(context), "receipt_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            file to bmp
        } catch (e: Exception) {
            null
        }
    }

    /** Текст с фото (распознавание прямо в телефоне). null — не получилось. */
    suspend fun readText(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Уменьшенная картинка для показа. */
    suspend fun load(path: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }
}
