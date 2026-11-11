package app.absentmotion.pro.labs

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthAiEstimatorTest {

    @Test
    fun testDepthAiTensorPreprocessingAndInference() {
        val width = 192
        val height = 192
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Draw synthetic pattern: bright textured circle in center, dark background
        val pixels = IntArray(width * height)
        val cx = width / 2
        val cy = height / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                pixels[y * width + x] = if (dist < 40) {
                    Color.rgb(220, 180, 140) // Foreground warm subject
                } else if (dist < 70) {
                    Color.rgb(100, 140, 200) // Midground
                } else {
                    Color.rgb(20, 30, 40)    // Distant background
                }
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        val disparity = DepthAiEstimator.inferDepthField(bmp)

        // 1. Verify correct tensor output dimensions
        assertEquals(192 * 192, disparity.size)

        // 2. Verify all numbers are finite (no NaN or Infinity)
        for (v in disparity) {
            assertTrue("Disparity value must be finite", v.isFinite())
        }

        // 3. Verify non-trivial spatial variance
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        for (v in disparity) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        assertTrue("Disparity map must have non-zero variance", maxVal > minVal)
        val variance = maxVal - minVal
        assertTrue("Disparity spread should be significant: $variance", variance > 0.01f)

        // 4. Verify center (foreground) has distinct disparity from corners (background)
        val centerIdx = cy * width + cx
        val cornerIdx = 0
        assertFalse("Center and corner disparity should differ", disparity[centerIdx] == disparity[cornerIdx])

        bmp.recycle()
    }

    @Test
    fun testDepthAiColormaps() {
        val width = 64
        val height = 64
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { Color.rgb(it % 256, (it * 3) % 256, (it * 7) % 256) }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        val palettes = listOf("Greyscale", "Inferno", "Turbo", "Viridis", "Plasma", "Spectral")
        for (palette in palettes) {
            val depthMap = DepthAiEstimator.inferDepthMap(bmp, palette = palette)
            assertNotNull("Depth map output must not be null for $palette", depthMap)
            assertEquals("Output width must match input", width, depthMap.width)
            assertEquals("Output height must match input", height, depthMap.height)

            val outPixels = IntArray(width * height)
            depthMap.getPixels(outPixels, 0, width, 0, 0, width, height)
            var hasNonZeroAlpha = false
            var hasColorVariation = false
            val firstPixel = outPixels[0]
            for (p in outPixels) {
                if ((p ushr 24) > 0) hasNonZeroAlpha = true
                if (p != firstPixel) hasColorVariation = true
            }
            assertTrue("Colormap $palette should output visible pixels", hasNonZeroAlpha)
            assertTrue("Colormap $palette should have spatial variations", hasColorVariation)
            depthMap.recycle()
        }
        bmp.recycle()
    }

    @Test
    fun testDepthModelDefinitions() {
        assertEquals(3, DepthAiEstimator.DepthModelType.entries.size)
        for (type in DepthAiEstimator.DepthModelType.entries) {
            assertTrue("Display name must not be blank", type.displayName.isNotBlank())
            assertTrue("Filename must end with .onnx", type.filename.endsWith(".onnx"))
            assertTrue("Download URL must be https", type.downloadUrl.startsWith("https://"))
        }
    }
}
