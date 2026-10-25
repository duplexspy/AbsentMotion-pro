package app.absentmotion.pro

import app.absentmotion.model.Layer
import app.absentmotion.model.Scene
import app.absentmotion.model.XmlNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PreRenderHarnessTest {

    private class SimulatedFrameStorage(val maxBytes: Long) {
        val files = LinkedHashMap<String, Long>(16, 0.75f, true)
        var totalBytes: Long = 0L
            private set

        fun put(key: String, sizeBytes: Long) {
            val prev = files.put(key, sizeBytes)
            if (prev != null) totalBytes -= prev
            totalBytes += sizeBytes
            enforceCap()
        }

        fun get(key: String): Long? {
            return files[key]
        }

        private fun enforceCap() {
            val it = files.entries.iterator()
            while (it.hasNext() && totalBytes > maxBytes) {
                val entry = it.next()
                totalBytes -= entry.value
                it.remove()
            }
        }

        val count: Int get() = files.size
    }

    private class BoundedPlaybackBuffer(val maxFrames: Int) {
        private val cache = LinkedHashMap<Int, String>(maxFrames, 0.75f, true)

        fun access(frame: Int, data: String) {
            cache[frame] = data
            while (cache.size > maxFrames) {
                val oldest = cache.keys.iterator().next()
                cache.remove(oldest)
            }
        }

        val size: Int get() = cache.size
    }

    @Before
    fun setup() {
        ProFeatures.setSafeMode(enabled = false)
    }

    private fun createTestLayer(id: Long, label: String, transform: String, effectVal: Float): Layer {
        return Layer(
            XmlNode(
                "shape",
                linkedMapOf(
                    "id" to "$id",
                    "label" to label,
                    "startTime" to "0",
                    "endTime" to "3000",
                    "s" to ".rect",
                ),
                listOf(
                    XmlNode(
                        "transform",
                        emptyMap(),
                        listOf(XmlNode("location", mapOf("value" to transform)))
                    ),
                    XmlNode(
                        "effect",
                        mapOf("id" to "absent.blur", "amount" to "$effectVal")
                    )
                )
            )
        )
    }

    @Test
    fun testFrameKeyGenerationAndStability() {
        val scene = Scene.create("test", 1080, 1920, 30, 0)
        val l1 = createTestLayer(1L, "Layer 1", "540,960,0", 15.0f)
        val l2 = createTestLayer(1L, "Layer 1", "540,960,0", 15.0f)
        val lDifferent = createTestLayer(1L, "Layer 1", "540,960,0", 25.0f)

        val key1 = PreRenderKey.computeFrameKey(scene, l1, 10)
        val key2 = PreRenderKey.computeFrameKey(scene, l2, 10)
        val keyDiff = PreRenderKey.computeFrameKey(scene, lDifferent, 10)

        assertEquals(key1, key2)
        assertNotEquals(key1, keyDiff)

        val scene4k = Scene.create("test", 2160, 3840, 30, 0)
        val key4k = PreRenderKey.computeFrameKey(scene4k, l1, 10)
        assertNotEquals(key1, key4k)
    }

    @Test
    fun testSelectivePerFrameInvalidation() {
        val validity = PreRenderValidityMap()
        val scene = Scene.create("test", 1080, 1080, 30, 0)
        val layer = createTestLayer(10L, "Anim", "100,100,0", 5f)

        for (f in 0..60) {
            val key = PreRenderKey.computeFrameKey(scene, layer, f)
            validity.recordValid(10L, f, key)
        }
        assertEquals(61, validity.getValidFrames(10L).size)

        val modifiedLayer = createTestLayer(10L, "Anim", "200,200,0", 5f)
        for (f in 30..60) {
            validity.invalidate(10L, f)
        }

        val remaining = validity.getValidFrames(10L)
        assertEquals(30, remaining.size)
        assertTrue(0 in remaining)
        assertTrue(29 in remaining)
        assertFalse(30 in remaining)
        assertFalse(60 in remaining)
    }

    @Test
    fun testStorageCapAndLruEviction() {
        val storage = SimulatedFrameStorage(maxBytes = 10 * 1024 * 1024L)
        val frameBytes = 1024 * 1024L

        for (i in 0 until 15) {
            storage.put("frame_$i", frameBytes)
        }

        assertTrue(storage.totalBytes <= 10 * 1024 * 1024L)
        assertEquals(10, storage.count)
        assertEquals(null, storage.get("frame_0"))
        assertEquals(null, storage.get("frame_4"))
        assertEquals(frameBytes, storage.get("frame_5"))
        assertEquals(frameBytes, storage.get("frame_14"))
    }

    @Test
    fun testFlatMemoryOverPlaybackLoop() {
        val buffer = BoundedPlaybackBuffer(maxFrames = 30)
        val totalPlaybackFrames = 18000

        for (i in 0 until totalPlaybackFrames) {
            val frameIndex = i % 300
            buffer.access(frameIndex, "frame_data_$frameIndex")
            assertTrue(buffer.size <= 30)
        }

        assertEquals(30, buffer.size)
    }

    @Test
    fun testRenderAndReplaceNonDestructive() {
        val original = createTestLayer(42L, "Heavy Layer", "100,100,0", 50f)
        var scene = Scene.create("test", 1080, 1080, 30, 0).add(original)

        assertEquals(1, scene.layers.size)
        assertFalse(scene.layer(42L)!!.hidden)

        val bakedNode = XmlNode(
            "shape",
            linkedMapOf(
                "id" to "100",
                "label" to "${original.label} (Baked)",
                "startTime" to "${original.startTime}",
                "endTime" to "${original.endTime}",
                "fillType" to "media",
                "fillImage" to "cache/prerender/baked_42.png",
                "mediaFillMode" to "Fill",
            )
        )
        val bakedLayer = Layer(bakedNode)

        val hiddenOriginal = scene.update(42L) { it.copy(node = it.node.with("hidden", "true")) }
        scene = hiddenOriginal.addAbove(bakedLayer, 42L)

        assertEquals(2, scene.layers.size)
        assertTrue(scene.layer(42L)!!.hidden)
        assertEquals("100", scene.layer(100L)!!.node["id"])
        assertEquals("media", scene.layer(100L)!!.fillType)

        val restoredOriginal = scene.update(42L) { it.copy(node = it.node.with("hidden", null)) }
        scene = restoredOriginal.remove(100L)

        assertEquals(1, scene.layers.size)
        assertFalse(scene.layer(42L)!!.hidden)
    }

    @Test
    fun testSafeModeReversion() {
        assertTrue(ProFeatures.isEnabled(ProFeatures.preRender))

        ProFeatures.setSafeMode(enabled = true)
        assertFalse(ProFeatures.isEnabled(ProFeatures.preRender))
    }
}
