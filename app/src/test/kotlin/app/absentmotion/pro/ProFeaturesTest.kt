package app.absentmotion.pro

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProFeaturesTest {
    private val prefs = FakeSharedPreferences()

    @Before
    fun setup() {
        ProFeatures.prefsOverride = prefs
        ProFeatures.setSafeMode(enabled = false)
    }

    @Test
    fun defaultFlagsAreEnabled() {
        assertTrue(ProFeatures.isEnabled(ProFeatures.exportEdgeLineFix))
        assertTrue(ProFeatures.isEnabled(ProFeatures.textRenderFix))
        assertTrue(ProFeatures.isEnabled(ProFeatures.groupSharpnessFix))
        assertTrue(ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq))
        assertTrue(ProFeatures.isEnabled(ProFeatures.preRender))
        assertTrue(ProFeatures.isEnabled(ProFeatures.newVectorTool))
        assertTrue(ProFeatures.isEnabled(ProFeatures.dafontBrowser))
        assertTrue(ProFeatures.isEnabled(ProFeatures.premiumUi))
    }

    @Test
    fun crashLoopTriggersSafeMode() {
        ProFeatures.init(launchRealtime = 1000L)
        assertFalse(ProFeatures.isSafeMode)

        ProFeatures.onCrash(crashRealtime = 2000L)
        assertEquals(1, ProFeatures.startupCrashes)
        assertFalse(ProFeatures.isSafeMode)

        ProFeatures.onCrash(crashRealtime = 3000L)
        assertEquals(2, ProFeatures.startupCrashes)
        assertTrue(ProFeatures.isSafeMode)

        assertFalse(ProFeatures.isEnabled(ProFeatures.exportEdgeLineFix))
        assertFalse(ProFeatures.isEnabled(ProFeatures.textRenderFix))
    }

    @Test
    fun lateCrashDoesNotTriggerSafeMode() {
        ProFeatures.init(launchRealtime = 1000L)
        ProFeatures.onCrash(crashRealtime = 15000L)
        assertEquals(0, ProFeatures.startupCrashes)
        assertFalse(ProFeatures.isSafeMode)
    }

    @Test
    fun stableRunningResetsCrashCount() {
        ProFeatures.init(launchRealtime = 1000L)
        ProFeatures.onCrash(crashRealtime = 2000L)
        assertEquals(1, ProFeatures.startupCrashes)

        ProFeatures.onStableRunning()
        assertEquals(0, ProFeatures.startupCrashes)
    }

    @Test
    fun manualSafeModeToggle() {
        ProFeatures.setSafeMode(enabled = true)
        assertTrue(ProFeatures.isSafeMode)
        assertFalse(ProFeatures.isEnabled(ProFeatures.newVectorTool))

        ProFeatures.setSafeMode(enabled = false)
        assertFalse(ProFeatures.isSafeMode)
        assertTrue(ProFeatures.isEnabled(ProFeatures.newVectorTool))
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = HashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val data: HashMap<String, Any>) : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { pending[key!!] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { pending[key!!] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { pending[key!!] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { pending[key!!] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { pending[key!!] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { pending[key!!] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { pending[key!!] = null; return this }
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
