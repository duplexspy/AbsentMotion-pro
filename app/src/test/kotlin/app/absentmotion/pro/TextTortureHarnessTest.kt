package app.absentmotion.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

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
}
