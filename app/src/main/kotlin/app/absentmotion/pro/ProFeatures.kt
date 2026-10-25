package app.absentmotion.pro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ProFeatures {
    const val PREFS_NAME = "pro_features"
    const val KEY_STARTUP_CRASHES = "startup_crashes"
    const val KEY_SAFE_MODE = "safe_mode"
    const val STABLE_TIME_MS = 10_000L
    const val CRASH_THRESHOLD = 2

    var exportEdgeLineFix: Boolean = true
    var textRenderFix: Boolean = true
    var groupSharpnessFix: Boolean = true
    var effectThumbnailsLq: Boolean = true
    var preRender: Boolean = true
    var newVectorTool: Boolean = true
    var dafontBrowser: Boolean = true
    var premiumUi: Boolean = true

    var isSafeMode by mutableStateOf(false)
        private set

    var startupCrashes by mutableStateOf(0)
        private set

    @Volatile
    private var launchElapsedRealtime: Long = 0L

    internal var prefsOverride: android.content.SharedPreferences? = null

    private fun getPrefs(context: Context?): android.content.SharedPreferences? {
        prefsOverride?.let { return it }
        return context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context? = null, launchRealtime: Long = android.os.SystemClock.elapsedRealtime()) {
        launchElapsedRealtime = launchRealtime
        val prefs = getPrefs(context) ?: return
        val crashes = prefs.getInt(KEY_STARTUP_CRASHES, 0)
        val safe = prefs.getBoolean(KEY_SAFE_MODE, false) || crashes >= CRASH_THRESHOLD
        startupCrashes = crashes
        isSafeMode = safe
    }

    fun onStableRunning(context: Context? = null) {
        val prefs = getPrefs(context) ?: return
        prefs.edit().putInt(KEY_STARTUP_CRASHES, 0).apply()
        startupCrashes = 0
    }

    fun onCrash(context: Context? = null, crashRealtime: Long = android.os.SystemClock.elapsedRealtime()) {
        val duration = crashRealtime - launchElapsedRealtime
        if (launchElapsedRealtime > 0L && duration in 0..STABLE_TIME_MS) {
            val prefs = getPrefs(context) ?: return
            val current = prefs.getInt(KEY_STARTUP_CRASHES, 0) + 1
            val enterSafe = current >= CRASH_THRESHOLD
            prefs.edit()
                .putInt(KEY_STARTUP_CRASHES, current)
                .putBoolean(KEY_SAFE_MODE, enterSafe)
                .apply()
            startupCrashes = current
            if (enterSafe) isSafeMode = true
        }
    }

    fun setSafeMode(context: Context? = null, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs?.edit()
            ?.putBoolean(KEY_SAFE_MODE, enabled)
            ?.putInt(KEY_STARTUP_CRASHES, if (enabled) CRASH_THRESHOLD else 0)
            ?.apply()
        isSafeMode = enabled
        if (!enabled) startupCrashes = 0
    }

    fun isEnabled(flag: Boolean): Boolean = !isSafeMode && flag
}
