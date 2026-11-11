package app.absentmotion.pro.cache

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class CacheCategory(val displayName: String, val dirName: String) {
    PREVIEW_FRAMES("Preview Frames", "preview_frames"),
    THUMBNAILS("Thumbnails & Waveforms", "thumbnails"),
    DECODED_MEDIA("Decoded Media Frames", "decoded_media"),
    TEXT_FONTS("Text & Font Glyph Cache", "text_cache"),
    EFFECTS_PRECOMPS("Effects & Precompositions", "effects_cache"),
    ONLINE_ASSETS("Online Asset Downloads", "online_assets")
}

enum class CachePreset(val displayName: String, val limitMb: Int) {
    LOW("Low (Old Phones)", 250),
    BALANCED("Balanced", 750),
    HIGH("High Performance", 2048),
    CUSTOM("Custom Limit", 1000)
}

object CacheManager {

    private const val PREFS_NAME = "am_advanced_cache_settings"
    private const val KEY_PRESET = "cache_preset"
    private const val KEY_LIMIT_MB = "cache_limit_mb"
    private const val KEY_AUTO_CLEANUP = "cache_auto_cleanup"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPreset(context: Context): CachePreset {
        val name = getPrefs(context).getString(KEY_PRESET, CachePreset.BALANCED.name) ?: CachePreset.BALANCED.name
        return runCatching { CachePreset.valueOf(name) }.getOrDefault(CachePreset.BALANCED)
    }

    fun setPreset(context: Context, preset: CachePreset) {
        val editor = getPrefs(context).edit().putString(KEY_PRESET, preset.name)
        if (preset != CachePreset.CUSTOM) {
            editor.putInt(KEY_LIMIT_MB, preset.limitMb)
        }
        editor.apply()
    }

    fun getLimitMb(context: Context): Int {
        val preset = getPreset(context)
        return if (preset != CachePreset.CUSTOM) preset.limitMb else getPrefs(context).getInt(KEY_LIMIT_MB, 1000)
    }

    fun setCustomLimitMb(context: Context, mb: Int) {
        getPrefs(context).edit()
            .putString(KEY_PRESET, CachePreset.CUSTOM.name)
            .putInt(KEY_LIMIT_MB, max(100, mb))
            .apply()
    }

    fun isAutoCleanupEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CLEANUP, true)
    }

    fun setAutoCleanupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CLEANUP, enabled).apply()
    }

    private fun getCategoryDir(context: Context, category: CacheCategory): File {
        return File(context.cacheDir, category.dirName).apply { mkdirs() }
    }

    fun getCategoryBytes(context: Context, category: CacheCategory): Long {
        val dir = getCategoryDir(context, category)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getTotalCacheBytes(context: Context): Long {
        return CacheCategory.entries.sumOf { getCategoryBytes(context, it) }
    }

    suspend fun clearCategory(context: Context, category: CacheCategory): Int = withContext(Dispatchers.IO) {
        val dir = getCategoryDir(context, category)
        if (!dir.exists()) return@withContext 0

        // Strict safety check: must be inside context.cacheDir
        val cacheCanonical = context.cacheDir.canonicalPath
        val dirCanonical = dir.canonicalPath
        if (!dirCanonical.startsWith(cacheCanonical)) {
            return@withContext 0
        }

        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.deleteRecursively()) deletedCount++
            } else {
                if (file.delete()) deletedCount++
            }
        }
        deletedCount
    }

    suspend fun clearAllCategories(context: Context): Int = withContext(Dispatchers.IO) {
        CacheCategory.entries.sumOf { clearCategory(context, it) }
    }

    suspend fun performAutoCleanupIfOverLimit(context: Context): Long = withContext(Dispatchers.IO) {
        if (!isAutoCleanupEnabled(context)) return@withContext 0L
        val limitBytes = getLimitMb(context).toLong() * 1024L * 1024L
        val currentBytes = getTotalCacheBytes(context)
        if (currentBytes <= limitBytes) return@withContext 0L

        var bytesFreed = 0L
        // Collect all cache files across categories, sorted by last modified (LRU)
        val allCacheFiles = CacheCategory.entries.flatMap { cat ->
            getCategoryDir(context, cat).walkTopDown().filter { it.isFile }.toList()
        }.sortedBy { it.lastModified() }

        val targetBytes = (limitBytes * 0.85).toLong()
        var remainingBytes = currentBytes

        for (file in allCacheFiles) {
            if (remainingBytes <= targetBytes) break
            val len = file.length()
            if (file.delete()) {
                bytesFreed += len
                remainingBytes -= len
            }
        }
        bytesFreed
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(java.util.Locale.US, "%.2f GB", gb)
    }
}
