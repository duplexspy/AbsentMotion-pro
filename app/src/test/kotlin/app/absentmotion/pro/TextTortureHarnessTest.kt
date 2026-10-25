package app.absentmotion.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

class TextTortureHarnessTest {

    private fun generateScaleSteps(minScale: Float = 0.25f, maxScale: Float = 8.0f, steps: Int = 40): List<Float> {
        val logMin = ln(minScale)
        val logMax = ln(maxScale)
        return (0 until steps).map { i ->
            val t = i.toFloat() / (steps - 1)
            exp(logMin + t * (logMax - logMin))
        }
    }

    private fun evaluateInkCoverageStability(
        scaleSteps: List<Float>,
        simulatedJumpIndex: Int? = null,
        jumpFactor: Float = 1.15f,
    ): List<Pair<Int, Float>> {
        val failures = ArrayList<Pair<Int, Float>>()
        var previousRatio: Float? = null

        scaleSteps.forEachIndexed { index, scale ->
            val jump = if (simulatedJumpIndex != null && index >= simulatedJumpIndex) jumpFactor else 1.0f
            val baseInkArea = 500f
            val measuredInk = baseInkArea * (scale * scale) * jump
            val normalizedRatio = measuredInk / (scale * scale)

            previousRatio?.let { prev ->
                val deltaPct = abs(normalizedRatio - prev) / prev * 100.0f
                if (deltaPct > 5.0f) {
                    failures.add(index to deltaPct)
                }
            }
            previousRatio = normalizedRatio
        }
        return failures
    }

    private fun evaluateZPerspectiveStability(
        zSteps: List<Float>,
        focalLength: Float = 1000f,
        simulatedJumpIndex: Int? = null,
        jumpFactor: Float = 1.15f,
    ): List<Pair<Int, Float>> {
        val failures = ArrayList<Pair<Int, Float>>()
        var previousRatio: Float? = null

        zSteps.forEachIndexed { index, z ->
            val effectiveScale = focalLength / max(1.0f, focalLength + z)
            val jump = if (simulatedJumpIndex != null && index >= simulatedJumpIndex) jumpFactor else 1.0f
            val baseInkArea = 500f
            val measuredInk = baseInkArea * (effectiveScale * effectiveScale) * jump
            val normalizedRatio = measuredInk / (effectiveScale * effectiveScale)

            previousRatio?.let { prev ->
                val deltaPct = abs(normalizedRatio - prev) / prev * 100.0f
                if (deltaPct > 5.0f) {
                    failures.add(index to deltaPct)
                }
            }
            previousRatio = normalizedRatio
        }
        return failures
    }

    @Test
    fun testScaleStepsCoverage() {
        val steps = generateScaleSteps()
        assertEquals(40, steps.size)
        assertTrue(steps.first() <= 0.26f)
        assertTrue(steps.last() >= 7.9f)
    }

    @Test
    fun testLinearTextPassesStabilityCheck() {
        val steps = generateScaleSteps()
        val failures = evaluateInkCoverageStability(steps)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun testNonLinearHintingJumpFailsStabilityCheck() {
        val steps = generateScaleSteps()
        val failures = evaluateInkCoverageStability(steps, simulatedJumpIndex = 15, jumpFactor = 1.12f)
        assertFalse(failures.isEmpty())
        assertEquals(15, failures.first().first)
        assertTrue(failures.first().second > 5.0f)
    }

    @Test
    fun testZPerspectivePassesStabilityCheck() {
        val zSteps = (0 until 30).map { -300f + it * 50f }
        val failures = evaluateZPerspectiveStability(zSteps)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun testZPerspectiveJumpFailsStabilityCheck() {
        val zSteps = (0 until 30).map { -300f + it * 50f }
        val failures = evaluateZPerspectiveStability(zSteps, simulatedJumpIndex = 10, jumpFactor = 1.14f)
        assertFalse(failures.isEmpty())
        assertEquals(10, failures.first().first)
        assertTrue(failures.first().second > 5.0f)
    }

    @Test
    fun testOverhangBoundsExpansion() {
        val advanceWidth = 60.0f
        val negativeBearing = -4.5f
        val ascent = -28.0f
        val descent = 10.0f

        val totalWidth = advanceWidth + abs(negativeBearing)
        val totalHeight = abs(ascent) + abs(descent)

        assertTrue(totalWidth > advanceWidth)
        assertEquals(64.5f, totalWidth, 0.001f)
        assertEquals(38.0f, totalHeight, 0.001f)
    }

    @Test
    fun testProFeaturesTextRenderFlag() {
        assertTrue(ProFeatures.isEnabled(ProFeatures.textRenderFix))
        val originalSafe = ProFeatures.isSafeMode
        try {
            ProFeatures.setSafeMode(null, true)
            assertFalse(ProFeatures.isEnabled(ProFeatures.textRenderFix))
        } finally {
            ProFeatures.setSafeMode(null, originalSafe)
        }
    }

    @Test
    fun testPadOverhangCalculation() {
        val lineBaseline = 32.0f
        val layoutHeight = 40.0f
        val fontMetricsTop = -36.0f
        val fontMetricsBottom = 12.0f

        val padTop = max(0.0f, -fontMetricsTop - lineBaseline)
        val padBottom = max(0.0f, (lineBaseline + fontMetricsBottom) - layoutHeight)
        val totalHeight = layoutHeight + padTop + padBottom

        assertEquals(4.0f, padTop, 0.001f)
        assertEquals(4.0f, padBottom, 0.001f)
        assertEquals(48.0f, totalHeight, 0.001f)
    }
}
