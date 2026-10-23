package io.github.ocpabdurrahim.skilltrack.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import io.github.ocpabdurrahim.skilltrack.data.CenterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Student photos live in `photos/{studentId}` and are fetched one at a time
 * when a screen or printout needs them (docs/data-model.md, "photos"), then
 * kept in memory.
 */
class Photos(private val store: CenterStore) {
    private val bitmaps = LruCache<String, Bitmap>(80)
    private val urls = LruCache<String, String>(80)
    private val missing = HashSet<String>()

    private val _version = MutableStateFlow(0)
    /** Bumped whenever a photo is saved or removed here, so screens show the new one. */
    val version: StateFlow<Int> = _version

    suspend fun bitmap(studentId: String): Bitmap? {
        val key = key(studentId) ?: return null
        bitmaps.get(key)?.let { return it }
        val url = dataUrl(studentId) ?: return null
        val bitmap = withContext(Dispatchers.Default) { Images.decode(url) } ?: return null
        bitmaps.put(key, bitmap)
        return bitmap
    }

    /** The photo as a data: URL (for printouts), or null when the student has none. */
    suspend fun dataUrl(studentId: String): String? {
        val key = key(studentId) ?: return null
        urls.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }
        val ref = store.photoRef(studentId) ?: return null
        val snap = runCatching { ref.get(Source.CACHE).await() }.getOrNull()?.takeIf { it.exists() }
            ?: runCatching { ref.get().await() }.getOrNull()
            ?: return null // offline and never fetched: try again next time
        val data = snap.takeIf { it.exists() && it.getBoolean("deleted") != true }?.getString("data")
        if (data.isNullOrEmpty()) {
            synchronized(missing) { missing += key }
            return null
        }
        urls.put(key, data)
        return data
    }

    fun save(studentId: String, dataUrl: String) {
        val key = key(studentId) ?: return
        val ref = store.photoRef(studentId) ?: return
        urls.put(key, dataUrl)
        bitmaps.remove(key)
        synchronized(missing) { missing -= key }
        store.trackWrite(ref.set(mapOf("data" to dataUrl) + CenterStore.live()))
        _version.value++
    }

    fun remove(studentId: String) {
        val key = key(studentId) ?: return
        val ref = store.photoRef(studentId) ?: return
        urls.remove(key)
        bitmaps.remove(key)
        synchronized(missing) { missing += key }
        store.trackWrite(ref.set(mapOf("data" to "", "deleted" to true, "updatedAt" to FieldValue.serverTimestamp())))
        _version.value++
    }

    private fun key(studentId: String): String? = store.currentCenterId?.let { "$it/$studentId" }
}

/** Turning picked pictures into small data: URLs, and back. */
object Images {
    /** A student photo: cropped to 4:5, 240 × 300, JPEG, at most about 60 KB. */
    fun studentPhoto(context: Context, uri: Uri): String {
        val source = load(context, uri, 900)
        val cropped = cropTo(source, 4, 5)
        val scaled = Bitmap.createScaledBitmap(cropped, 240, 300, true)
        return jpeg(scaled, 60_000)
    }

    /** A center logo: fits in 360 × 360, PNG when small enough (keeps transparency), else JPEG; at most 100 KB. */
    fun logo(context: Context, uri: Uri): String {
        var bitmap = fit(load(context, uri, 1200), 360)
        repeat(4) {
            val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            if (png.size <= 100_000) return "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
            bitmap = fit(bitmap, (max(bitmap.width, bitmap.height) * 0.75f).roundToInt())
        }
        return jpeg(onWhite(bitmap), 100_000)
    }

    fun decode(dataUrl: String): Bitmap? {
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:") || comma < 0) return null
        return runCatching {
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun load(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("That file is not a picture this phone can open.")
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropTo(b: Bitmap, w: Int, h: Int): Bitmap {
        val target = w.toFloat() / h
        val current = b.width.toFloat() / b.height
        return if (current > target) {
            val newWidth = (b.height * target).roundToInt()
            Bitmap.createBitmap(b, (b.width - newWidth) / 2, 0, newWidth, b.height)
        } else {
            val newHeight = (b.width / target).roundToInt()
            // Keep the top part: faces are usually in the upper half of a portrait.
            Bitmap.createBitmap(b, 0, ((b.height - newHeight) / 4).coerceAtLeast(0), b.width, newHeight)
        }
    }

    private fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val side = max(b.width, b.height)
        if (side <= maxSide) return b
        val scale = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(b, (b.width * scale).roundToInt().coerceAtLeast(1), (b.height * scale).roundToInt().coerceAtLeast(1), true)
    }

    private fun onWhite(b: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(b, 0f, 0f, null)
        }
        return out
    }

    private fun jpeg(b: Bitmap, maxBytes: Int): String {
        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 35)
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
