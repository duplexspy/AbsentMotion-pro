package app.absentmotion.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.ui.theme.Am
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object EffectThumbnailManager {
    const val MAX_CACHE_BYTES = 25 * 1024 * 1024
    const val ATLAS_SIZE = 64
    const val VELOCITY_THRESHOLD_DP = 1200f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private var diskCacheDir: File? = null

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val atlas = ConcurrentHashMap<String, Bitmap>()

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val cache = File(context.cacheDir, "effect_thumbs_lru")
        if (!cache.exists()) cache.mkdirs()
        diskCacheDir = cache
        scope.launch {
            preloadBundledAtlas(context)
        }
    }

    fun preloadBundledAtlas(context: Context) {
        if (!ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq)) return
        try {
            val assetManager = context.assets
            val list = assetManager.list("effects/thumb") ?: return
            for (filename in list) {
                if (!filename.endsWith(".webp") && !filename.endsWith(".png")) continue
                val key = "thumb/$filename"
                if (atlas.containsKey(key)) continue
                assetManager.open("effects/$key").use { stream ->
                    decodeRgb565(stream, ATLAS_SIZE, ATLAS_SIZE)?.let { bmp ->
                        atlas[key] = bmp
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun get(path: String?): Bitmap? {
        if (path == null || !ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq)) return null
        memoryCache.get(path)?.let { return it }
        atlas[path]?.let { return it }
        return null
    }

    fun getAtlas(path: String?): Bitmap? {
        if (path == null || !ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq)) return null
        return atlas[path]
    }

    fun getMemoryUsage(): Int = memoryCache.size()

    fun getAtlasCount(): Int = atlas.size

    fun clear() {
        memoryCache.evictAll()
        atlas.clear()
    }

    fun getPlaceholderColor(category: String?): Color {
        return when (category?.lowercase()) {
            "color" -> Color(0xFFE57373)
            "blur" -> Color(0xFF4DD0E1)
            "distort" -> Color(0xFFBA68C8)
            "stylize" -> Color(0xFFFFB74D)
            "noise" -> Color(0xFFAED581)
            "repeat" -> Color(0xFF7986CB)
            "key" -> Color(0xFF81C784)
            "move" -> Color(0xFF64B5F6)
            "3d" -> Color(0xFFFF8A65)
            "text" -> Color(0xFFF06292)
            else -> Color(0xFF333842)
        }
    }

    fun getPlaceholderGlyph(name: String?): String {
        if (name.isNullOrBlank()) return "FX"
        val words = name.trim().split(Regex("\\s+"))
        return if (words.size >= 2) {
            "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        } else {
            name.take(2).uppercase()
        }
    }

    fun loadBitmap(
        catalog: EffectCatalog,
        path: String,
        targetWidth: Int = ATLAS_SIZE,
        targetHeight: Int = ATLAS_SIZE,
    ): Bitmap? {
        if (!ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq)) return null
        memoryCache.get(path)?.let { return it }
        atlas[path]?.let { return it }

        val diskFile = diskCacheDir?.let { File(it, path.replace('/', '_')) }
        if (diskFile != null && diskFile.exists()) {
            runCatching {
                BitmapFactory.decodeFile(diskFile.absolutePath)?.let { bmp ->
                    memoryCache.put(path, bmp)
                    return bmp
                }
            }
        }

        val decoded = runCatching {
            catalog.openStream(path)?.use { stream ->
                decodeRgb565(stream, targetWidth, targetHeight)
            }
        }.getOrNull()

        if (decoded != null) {
            memoryCache.put(path, decoded)
            if (diskFile != null && !diskFile.exists()) {
                scope.launch {
                    runCatching {
                        FileOutputStream(diskFile).use { out ->
                            decoded.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                    }
                }
            }
        }
        return decoded
    }

    fun decodeRgb565(stream: InputStream, reqW: Int, reqH: Int): Bitmap? {
        val bytes = stream.readBytes()
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

        var sampleSize = 1
        if (opts.outHeight > reqH || opts.outWidth > reqW) {
            val halfH = opts.outHeight / 2
            val halfW = opts.outWidth / 2
            while ((halfH / sampleSize) >= reqH && (halfW / sampleSize) >= reqW) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        if (decoded.width == reqW && decoded.height == reqH) return decoded

        val scaled = Bitmap.createScaledBitmap(decoded, reqW, reqH, true)
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun EffectCatalog.openStream(path: String): InputStream? {
        return runCatching { open?.invoke(path) }.getOrNull()
    }
}
