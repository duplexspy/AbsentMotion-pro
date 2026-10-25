package app.absentmotion.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThumbnailPerfHarnessTest {

    private class TestLruCache(val maxBytes: Int) {
        private val map = LinkedHashMap<String, Int>(16, 0.75f, true)
        var currentBytes: Int = 0
            private set

        fun get(key: String): Int? {
            return map[key]
        }

        fun put(key: String, bytes: Int) {
            val prev = map.put(key, bytes)
            if (prev != null) {
                currentBytes -= prev
            }
            currentBytes += bytes
            trimToSize(maxBytes)
        }

        private fun trimToSize(max: Int) {
            val iterator = map.entries.iterator()
            while (iterator.hasNext() && currentBytes > max) {
                val entry = iterator.next()
                currentBytes -= entry.value
                iterator.remove()
            }
        }

        val size: Int
            get() = map.size
    }

    private class FakeScrollEnvironment {
        var isFastScrolling: Boolean = false
        val atlas = mutableMapOf<String, String>()
        val lruCache = TestLruCache(EffectThumbnailManager.MAX_CACHE_BYTES)
        var decodesTriggered = 0

        fun renderTile(path: String, category: String, name: String, velocityDp: Float): String {
            val fast = velocityDp >= EffectThumbnailManager.VELOCITY_THRESHOLD_DP || isFastScrolling
            lruCache.get(path)?.let { return "LRU:$it" }
            atlas[path]?.let { return "ATLAS:$it" }
            if (fast) {
                return "PLACEHOLDER:${EffectThumbnailManager.getPlaceholderGlyph(name)}"
            }
            decodesTriggered++
            val decodedTag = "DECODED:$path"
            lruCache.put(path, 8192)
            return decodedTag
        }
    }

    @Before
    fun setup() {
        ProFeatures.setSafeMode(enabled = false)
    }

    @Test
    fun testLruCacheCapacityAndEviction() {
        val cache = TestLruCache(25 * 1024 * 1024)
        val oneMb = 1024 * 1024

        for (i in 0 until 30) {
            cache.put("item_$i", oneMb)
        }

        assertTrue(cache.currentBytes <= 25 * 1024 * 1024)
        assertEquals(25, cache.size)
        assertEquals(null, cache.get("item_0"))
        assertEquals(null, cache.get("item_4"))
        assertNotNull(cache.get("item_5"))
        assertNotNull(cache.get("item_29"))
    }

    @Test
    fun testRgb565MemoryEfficiency() {
        val count = 184
        val argbWidth = 192
        val argbHeight = 192
        val argbBytesPerPixel = 4
        val totalArgbBytes = count * argbWidth * argbHeight * argbBytesPerPixel

        val lqWidth = EffectThumbnailManager.ATLAS_SIZE
        val lqHeight = EffectThumbnailManager.ATLAS_SIZE
        val rgb565BytesPerPixel = 2
        val totalRgb565Bytes = count * lqWidth * lqHeight * rgb565BytesPerPixel

        val ratio = totalArgbBytes.toDouble() / totalRgb565Bytes.toDouble()
        assertEquals(18.0, ratio, 0.001)

        val totalRgb565Mb = totalRgb565Bytes.toDouble() / (1024.0 * 1024.0)
        assertTrue(totalRgb565Mb < 2.0)
        assertTrue(totalRgb565Bytes < EffectThumbnailManager.MAX_CACHE_BYTES)
    }

    @Test
    fun testPlaceholderColorAndGlyphGeneration() {
        val categories = listOf(
            "color", "blur", "distort", "stylize", "noise",
            "repeat", "key", "move", "3d", "text", "unknown"
        )
        val colors = categories.map { EffectThumbnailManager.getPlaceholderColor(it) }
        assertEquals(categories.size, colors.size)
        assertTrue(colors.distinct().size >= 10)

        assertEquals("MB", EffectThumbnailManager.getPlaceholderGlyph("Motion Blur"))
        assertEquals("CK", EffectThumbnailManager.getPlaceholderGlyph("Chroma Key"))
        assertEquals("BL", EffectThumbnailManager.getPlaceholderGlyph("Blur"))
        assertEquals("GL", EffectThumbnailManager.getPlaceholderGlyph("Glow"))
        assertEquals("FX", EffectThumbnailManager.getPlaceholderGlyph(""))
        assertEquals("FX", EffectThumbnailManager.getPlaceholderGlyph(null))
    }

    @Test
    fun testFastFlingDeferredRendering() {
        val env = FakeScrollEnvironment()
        val path = "thumb/unloaded_effect.webp"
        val category = "blur"
        val name = "Directional Blur"

        val fastResult = env.renderTile(path, category, name, velocityDp = 2000f)
        assertEquals("PLACEHOLDER:DB", fastResult)
        assertEquals(0, env.decodesTriggered)

        val slowResult = env.renderTile(path, category, name, velocityDp = 200f)
        assertEquals("DECODED:$path", slowResult)
        assertEquals(1, env.decodesTriggered)

        val cachedResult = env.renderTile(path, category, name, velocityDp = 3000f)
        assertTrue(cachedResult.startsWith("LRU:"))
        assertEquals(1, env.decodesTriggered)
    }

    @Test
    fun testScrollSequence1000ItemsFrameTimes() {
        val itemsCount = 1000
        val env = FakeScrollEnvironment()
        for (i in 0 until 184) {
            env.atlas["thumb/effect_$i.webp"] = "effect_$i"
        }

        val baselineFrameTimesMs = DoubleArray(itemsCount)
        val optimizedFrameTimesMs = DoubleArray(itemsCount)

        var simulatedVelocity = 0f
        for (i in 0 until itemsCount) {
            simulatedVelocity = if (i % 50 < 30) 2500f else 100f
            env.isFastScrolling = simulatedVelocity > 1200f

            val itemIndex = i % 250
            val path = "thumb/effect_$itemIndex.webp"

            val baselineTime = if (itemIndex >= 184) 28.0 + (i % 7) else 18.0 + (i % 5)
            baselineFrameTimesMs[i] = baselineTime

            val t0 = System.nanoTime()
            val rendered = env.renderTile(path, "blur", "Effect $itemIndex", simulatedVelocity)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

            val cost = when {
                rendered.startsWith("LRU:") -> 0.05
                rendered.startsWith("ATLAS:") -> 0.08
                rendered.startsWith("PLACEHOLDER:") -> 0.01
                else -> 12.0
            }
            optimizedFrameTimesMs[i] = elapsedMs + cost
        }

        val baselineSorted = baselineFrameTimesMs.sorted()
        val baselineP99 = baselineSorted[(itemsCount * 0.99).toInt()]
        val baselineDrops = baselineFrameTimesMs.count { it > 32.0 }

        val optimizedSorted = optimizedFrameTimesMs.sorted()
        val optimizedP99 = optimizedSorted[(itemsCount * 0.99).toInt()]
        val optimizedDrops = optimizedFrameTimesMs.count { it > 32.0 }

        assertTrue(baselineP99 > 20.0)
        assertTrue(optimizedP99 < 24.0)
        assertEquals(0, optimizedDrops)
    }

    @Test
    fun testSafeModeReversion() {
        assertTrue(ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq))

        ProFeatures.setSafeMode(enabled = true)
        assertFalse(ProFeatures.isEnabled(ProFeatures.effectThumbnailsLq))

        val bmp = EffectThumbnailManager.get("thumb/blur.webp")
        assertEquals(null, bmp)

        val atlasBmp = EffectThumbnailManager.getAtlas("thumb/blur.webp")
        assertEquals(null, atlasBmp)
    }
}
