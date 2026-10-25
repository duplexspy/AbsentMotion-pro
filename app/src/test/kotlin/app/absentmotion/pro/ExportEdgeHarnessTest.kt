package app.absentmotion.pro

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
}
