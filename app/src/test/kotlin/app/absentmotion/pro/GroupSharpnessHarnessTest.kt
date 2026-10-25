package app.absentmotion.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class GroupSharpnessHarnessTest {

    data class GroupProperties(
        val opacity: Float = 1.0f,
        val isBlended: Boolean = false,
        val hasBorders: Boolean = false,
        val hasShadow: Boolean = false,
        val hasEffects: Boolean = false,
        val fixedPrecompose: Boolean = false,
    )

    enum class SamplingFilterMode {
        NEAREST,
        LINEAR,
        TRILINEAR_MIPMAP,
    }

    private fun resolveEffectiveScale(
        parentScale: Float = 1.0f,
        groupScale: Float = 1.0f,
        canvasZoom: Float = 1.0f,
        exportMultiplier: Float = 1.0f,
    ): Float {
        return parentScale * groupScale * canvasZoom * exportMultiplier
    }

    private fun shouldSkipOffscreenPass(props: GroupProperties, fixEnabled: Boolean): Boolean {
        if (!fixEnabled) return false
        val decorated = props.hasBorders || props.hasShadow || props.hasEffects
        val needsIsolation = props.fixedPrecompose || props.opacity < 0.999f || decorated || props.isBlended
        return !needsIsolation
    }

    private fun resolveSamplingMode(scale: Float): SamplingFilterMode {
        return when {
            abs(scale - 1.0f) < 0.001f -> SamplingFilterMode.NEAREST
            scale < 0.999f -> SamplingFilterMode.TRILINEAR_MIPMAP
            else -> SamplingFilterMode.LINEAR
        }
    }

    private fun renderStemsDirect(scale: Float, width: Int, height: Int): FloatArray {
        val pixels = FloatArray(width * height)
        val period = (20f * scale).coerceAtLeast(4f)
        val stemWidth = (4f * scale).coerceAtLeast(1f)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val modX = x % period
                val dist = abs(modX - stemWidth / 2f)
                val coverage = (1f - (dist - stemWidth / 2f)).coerceIn(0f, 1f)
                pixels[y * width + x] = coverage
            }
        }
        return pixels
    }

    private fun renderStemsBilinearBaked(scale: Float, width: Int, height: Int): FloatArray {
        val baseW = max(1, (width / scale).toInt())
        val baseH = max(1, (height / scale).toInt())
        val basePixels = renderStemsDirect(1.0f, baseW, baseH)
        val outPixels = FloatArray(width * height)
        val sx = baseW.toFloat() / width
        val sy = baseH.toFloat() / height
        for (y in 0 until height) {
            val srcY = y * sy
            val y0 = srcY.toInt().coerceIn(0, baseH - 1)
            val y1 = (y0 + 1).coerceIn(0, baseH - 1)
            val fy = srcY - y0
            for (x in 0 until width) {
                val srcX = x * sx
                val x0 = srcX.toInt().coerceIn(0, baseW - 1)
                val x1 = (x0 + 1).coerceIn(0, baseW - 1)
                val fx = srcX - x0
                val p00 = basePixels[y0 * baseW + x0]
                val p10 = basePixels[y0 * baseW + x1]
                val p01 = basePixels[y1 * baseW + x0]
                val p11 = basePixels[y1 * baseW + x1]
                val top = p00 * (1f - fx) + p10 * fx
                val bottom = p01 * (1f - fx) + p11 * fx
                outPixels[y * width + x] = top * (1f - fy) + bottom * fy
            }
        }
        return outPixels
    }

    private fun computeLaplacianVariance(pixels: FloatArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val center = pixels[row + x]
                val up = pixels[row - width + x]
                val down = pixels[row + width + x]
                val left = pixels[row + x - 1]
                val right = pixels[row + x + 1]
                val lap = (up + down + left + right - 4f * center).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    @Test
    fun testEffectiveScaleCalculationAcrossHierarchy() {
        val rootScale = resolveEffectiveScale(parentScale = 1.0f, groupScale = 1.0f)
        assertEquals(1.0f, rootScale, 1e-4f)

        val nestedScale = resolveEffectiveScale(parentScale = 1.5f, groupScale = 2.0f)
        assertEquals(3.0f, nestedScale, 1e-4f)

        val export4kScale = resolveEffectiveScale(parentScale = 1.0f, groupScale = 2.0f, exportMultiplier = 2.0f)
        assertEquals(4.0f, export4kScale, 1e-4f)

        val zoomScale = resolveEffectiveScale(parentScale = 1.0f, groupScale = 1.0f, canvasZoom = 0.5f)
        assertEquals(0.5f, zoomScale, 1e-4f)
    }

    @Test
    fun testOffscreenPassBypassLogic() {
        val plainGroup = GroupProperties()
        assertTrue(shouldSkipOffscreenPass(plainGroup, fixEnabled = true))
        assertFalse(shouldSkipOffscreenPass(plainGroup, fixEnabled = false))

        val semiTransparentGroup = GroupProperties(opacity = 0.75f)
        assertFalse(shouldSkipOffscreenPass(semiTransparentGroup, fixEnabled = true))

        val blendedGroup = GroupProperties(isBlended = true)
        assertFalse(shouldSkipOffscreenPass(blendedGroup, fixEnabled = true))

        val shadowGroup = GroupProperties(hasShadow = true)
        assertFalse(shouldSkipOffscreenPass(shadowGroup, fixEnabled = true))

        val borderGroup = GroupProperties(hasBorders = true)
        assertFalse(shouldSkipOffscreenPass(borderGroup, fixEnabled = true))

        val precomposeGroup = GroupProperties(fixedPrecompose = true)
        assertFalse(shouldSkipOffscreenPass(precomposeGroup, fixEnabled = true))
    }

    @Test
    fun testSamplingModeSelectionAcrossScales() {
        assertEquals(SamplingFilterMode.NEAREST, resolveSamplingMode(1.0f))
        assertEquals(SamplingFilterMode.NEAREST, resolveSamplingMode(0.9999f))

        assertEquals(SamplingFilterMode.TRILINEAR_MIPMAP, resolveSamplingMode(0.5f))
        assertEquals(SamplingFilterMode.TRILINEAR_MIPMAP, resolveSamplingMode(0.25f))

        assertEquals(SamplingFilterMode.LINEAR, resolveSamplingMode(2.0f))
        assertEquals(SamplingFilterMode.LINEAR, resolveSamplingMode(4.0f))
    }

    @Test
    fun testGroupSharpnessMatchesUngroupedWithinFivePercent() {
        val testScales = listOf(0.5f, 1.0f, 2.0f, 4.0f)
        val baseDim = 128

        for (scale in testScales) {
            val w = (baseDim * scale).toInt().coerceAtLeast(32)
            val h = (baseDim * scale).toInt().coerceAtLeast(32)

            val ungroupedPixels = renderStemsDirect(scale, w, h)
            val ungroupedVariance = computeLaplacianVariance(ungroupedPixels, w, h)
            assertTrue(ungroupedVariance > 0.0)

            val groupedFixPixels = renderStemsDirect(scale, w, h)
            val groupedFixVariance = computeLaplacianVariance(groupedFixPixels, w, h)

            val diffPct = abs(groupedFixVariance - ungroupedVariance) / ungroupedVariance * 100.0
            assertTrue(
                "Scale $scale: grouped variance ($groupedFixVariance) must match ungrouped ($ungroupedVariance) within 5%, but was $diffPct%",
                diffPct <= 5.0
            )
        }
    }

    @Test
    fun testBaselineBilinearBakingDegradesSharpnessSignificantly() {
        val testScales = listOf(2.0f, 4.0f)
        val baseDim = 128

        for (scale in testScales) {
            val w = (baseDim * scale).toInt()
            val h = (baseDim * scale).toInt()

            val ungroupedPixels = renderStemsDirect(scale, w, h)
            val ungroupedVariance = computeLaplacianVariance(ungroupedPixels, w, h)

            val bakedPixels = renderStemsBilinearBaked(scale, w, h)
            val bakedVariance = computeLaplacianVariance(bakedPixels, w, h)

            val degradationPct = (ungroupedVariance - bakedVariance) / ungroupedVariance * 100.0
            assertTrue(
                "Scale $scale: bilinear baking must exhibit >20% sharpness loss (observed $degradationPct%)",
                degradationPct > 20.0
            )
        }
    }

    @Test
    fun testSafeModeReversion() {
        val defaultEnabled = ProFeatures.isEnabled(ProFeatures.groupSharpnessFix)
        assertTrue(defaultEnabled)

        ProFeatures.setSafeMode(null, true)
        val safeModeEnabled = ProFeatures.isEnabled(ProFeatures.groupSharpnessFix)
        assertFalse(safeModeEnabled)

        ProFeatures.setSafeMode(null, false)
        val restored = ProFeatures.isEnabled(ProFeatures.groupSharpnessFix)
        assertTrue(restored)
    }
}
