package app.absentmotion.pro

import app.absentmotion.model.Keyframe
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyframeToolsTest {

    @Test
    fun reverseKeyframesReversesValues() {
        val original = listOf(
            Keyframe(0.0f, "10.0"),
            Keyframe(0.5f, "20.0"),
            Keyframe(1.0f, "30.0")
        )
        val reversed = KeyframeTools.reverse(original)

        assertEquals(3, reversed.size)
        assertEquals(0.0f, reversed[0].t, 0.001f)
        assertEquals("30.0", reversed[0].v)
        assertEquals(0.5f, reversed[1].t, 0.001f)
        assertEquals("20.0", reversed[1].v)
        assertEquals(1.0f, reversed[2].t, 0.001f)
        assertEquals("10.0", reversed[2].v)
    }

    @Test
    fun mirrorKeyframesInvertsTime() {
        val original = listOf(
            Keyframe(0.0f, "A"),
            Keyframe(0.2f, "B"),
            Keyframe(1.0f, "C")
        )
        val mirrored = KeyframeTools.mirror(original)

        assertEquals(3, mirrored.size)
        assertEquals(0.0f, mirrored[0].t, 0.001f)
        assertEquals("C", mirrored[0].v)
        assertEquals(0.8f, mirrored[1].t, 0.001f)
        assertEquals("B", mirrored[1].v)
        assertEquals(1.0f, mirrored[2].t, 0.001f)
        assertEquals("A", mirrored[2].v)
    }

    @Test
    fun scaleTimingExpandsOrCompresses() {
        val original = listOf(
            Keyframe(0.0f, "0"),
            Keyframe(0.25f, "25"),
            Keyframe(0.5f, "50")
        )
        val scaled = KeyframeTools.scaleTiming(original, 2.0f)

        assertEquals(3, scaled.size)
        assertEquals(0.0f, scaled[0].t, 0.001f)
        assertEquals(0.5f, scaled[1].t, 0.001f)
        assertEquals(1.0f, scaled[2].t, 0.001f)
    }

    @Test
    fun easeAllAppliesCurveToAll() {
        val original = listOf(
            Keyframe(0.0f, "0", null),
            Keyframe(1.0f, "100", null)
        )
        val eased = KeyframeTools.easeAll(original, "cubic(0.4, 0.0, 0.2, 1.0)")

        assertEquals("cubic(0.4, 0.0, 0.2, 1.0)", eased[0].e)
        assertEquals("cubic(0.4, 0.0, 0.2, 1.0)", eased[1].e)
    }
}
