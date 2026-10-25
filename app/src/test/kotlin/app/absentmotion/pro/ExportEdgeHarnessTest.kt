package app.absentmotion.pro

import app.absentmotion.export.EncoderCaps
import app.absentmotion.export.SizePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportEdgeHarnessTest {

    data class AlignmentResult(
        val originalWidth: Int,
        val originalHeight: Int,
        val alignedWidth: Int,
        val alignedHeight: Int,
        val padX: Int,
        val padY: Int,
    )

    private fun alignDimensions(width: Int, height: Int, alignment: Int = 16): AlignmentResult {
        val alignedW = ((width + alignment - 1) / alignment) * alignment
        val alignedH = ((height + alignment - 1) / alignment) * alignment
        return AlignmentResult(
            originalWidth = width,
            originalHeight = height,
            alignedWidth = alignedW,
            alignedHeight = alignedH,
            padX = alignedW - width,
            padY = alignedH - height,
        )
    }

    private fun resolveClearColor(bgColor: Int, flagEnabled: Boolean): Int {
        return if (flagEnabled) {
            if ((bgColor ushr 24) != 0) bgColor else 0
        } else {
            0xFF000000.toInt()
        }
    }

    private fun clampUv(uv: FloatArray, bounds: FloatArray): FloatArray {
        return floatArrayOf(
            uv[0].coerceIn(bounds[0], bounds[2]),
            uv[1].coerceIn(bounds[1], bounds[3])
        )
    }

    @Test
    fun testStandard1080pRequiresPadding() {
        val result = alignDimensions(1080, 1920)
        assertEquals(1088, result.alignedWidth)
        assertEquals(1920, result.alignedHeight)
        assertEquals(8, result.padX)
        assertEquals(0, result.padY)
    }

    @Test
    fun test4KAlreadyAligned() {
        val result = alignDimensions(3840, 2160)
        assertEquals(3840, result.alignedWidth)
        assertEquals(2160, result.alignedHeight)
        assertEquals(0, result.padX)
        assertEquals(0, result.padY)
    }

    @Test
    fun testOddDimensionAlignment() {
        val result = alignDimensions(719, 1279, alignment = 2)
        assertEquals(720, result.alignedWidth)
        assertEquals(1280, result.alignedHeight)
        assertEquals(1, result.padX)
        assertEquals(1, result.padY)
    }

    @Test
    fun testViewportFillScaleCalculation() {
        val originalW = 1000f
        val originalH = 563f
        val aligned = alignDimensions(1000, 563, 16)
        val scaleX = aligned.alignedWidth.toFloat() / originalW
        val scaleY = aligned.alignedHeight.toFloat() / originalH
        assertTrue(scaleX >= 1.0f)
        assertTrue(scaleY >= 1.0f)
    }

    @Test
    fun testClearColorSelection() {
        val white = 0xFFFFFFFF.toInt()
        val transparent = 0x00000000
        val customBg = 0xFF1B1D27.toInt()

        assertEquals(white, resolveClearColor(white, true))
        assertEquals(0, resolveClearColor(transparent, true))
        assertEquals(customBg, resolveClearColor(customBg, true))

        assertEquals(0xFF000000.toInt(), resolveClearColor(white, false))
        assertEquals(0xFF000000.toInt(), resolveClearColor(transparent, false))
    }

    @Test
    fun testCropMetadataCalculation() {
        val originalW = 1000
        val originalH = 563
        val aligned = alignDimensions(originalW, originalH, 16)

        val cropLeft = 0
        val cropTop = 0
        val cropRight = originalW - 1
        val cropBottom = originalH - 1

        assertEquals(0, cropLeft)
        assertEquals(0, cropTop)
        assertEquals(999, cropRight)
        assertEquals(562, cropBottom)
        assertEquals(1008, aligned.alignedWidth)
        assertEquals(576, aligned.alignedHeight)
    }

    @Test
    fun testEncoderCapsFitsSizeWithAlignment() {
        val caps = EncoderCaps(
            name = "test.hw.avc",
            mime = "video/avc",
            hardware = true,
            widths = 128..4096,
            heights = 128..4096,
            widthAlign = 16,
            heightAlign = 16,
            sizes = listOf(SizePoint(4096, 4096, 60.0)),
        )

        try {
            ProFeatures.exportEdgeLineFix = false
            assertFalse(caps.fitsSize(1000, 564))

            ProFeatures.exportEdgeLineFix = true
            assertTrue(caps.fitsSize(1000, 564))
            assertTrue(caps.fitsSize(1080, 1920))
        } finally {
            ProFeatures.exportEdgeLineFix = true
        }
    }

    @Test
    fun testProxyUvClamping() {
        val bounds = floatArrayOf(0.0f, 0.0f, 0.9926f, 1.0f)
        val inside = clampUv(floatArrayOf(0.5f, 0.5f), bounds)
        assertEquals(0.5f, inside[0], 0.0001f)
        assertEquals(0.5f, inside[1], 0.0001f)

        val outsideRight = clampUv(floatArrayOf(1.002f, 0.5f), bounds)
        assertEquals(0.9926f, outsideRight[0], 0.0001f)
        assertEquals(0.5f, outsideRight[1], 0.0001f)

        val outsideTopLeft = clampUv(floatArrayOf(-0.01f, -0.01f), bounds)
        assertEquals(0.0f, outsideTopLeft[0], 0.0001f)
        assertEquals(0.0f, outsideTopLeft[1], 0.0001f)
    }
}
