package app.absentmotion.pro.morph

import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ShapePreset(val displayName: String) {
    Triangle("Triangle"),
    Circle("Circle"),
    Square("Square"),
    Heart("Heart"),
    Star("5-Point Star"),
    Polygon("Hexagon"),
    Diamond("Diamond")
}

data class ShapePoint(val x: Float, val y: Float)

data class ShapeMorphConfig(
    val startShape: ShapePreset = ShapePreset.Triangle,
    val endShape: ShapePreset = ShapePreset.Circle,
    val durationMs: Long = 1000L,
    val easing: String = "EaseInOut",
    val loop: Boolean = true,
    val reverseOnLoop: Boolean = true,
    val startFillColor: Color = Color(0xFF00FF9D),
    val endFillColor: Color = Color(0xFF00E5FF),
    val strokeColor: Color = Color(0xFFFFFFFF),
    val startStrokeWidth: Float = 3f,
    val endStrokeWidth: Float = 3f,
    val size: Float = 200f
)

data class MorphResult(
    val path: Path,
    val progress: Float,
    val fillColor: Color,
    val strokeWidth: Float
)

object ShapeMorphEngine {

    const val SAMPLE_COUNT = 128

    fun generatePoints(preset: ShapePreset, size: Float, count: Int = SAMPLE_COUNT): List<ShapePoint> {
        val radius = size / 2f
        val points = ArrayList<ShapePoint>(count)

        when (preset) {
            ShapePreset.Circle -> {
                for (i in 0 until count) {
                    val theta = (2 * PI * i / count).toFloat()
                    points.add(ShapePoint(radius * cos(theta), radius * sin(theta)))
                }
            }
            ShapePreset.Triangle -> {
                val vertices = listOf(
                    ShapePoint(0f, -radius),
                    ShapePoint(radius * cos(PI.toFloat() / 6f), radius * sin(PI.toFloat() / 6f)),
                    ShapePoint(-radius * cos(PI.toFloat() / 6f), radius * sin(PI.toFloat() / 6f))
                )
                samplePolygon(vertices, count, points)
            }
            ShapePreset.Square -> {
                val half = radius * 0.85f
                val vertices = listOf(
                    ShapePoint(-half, -half),
                    ShapePoint(half, -half),
                    ShapePoint(half, half),
                    ShapePoint(-half, half)
                )
                samplePolygon(vertices, count, points)
            }
            ShapePreset.Heart -> {
                for (i in 0 until count) {
                    val t = (2 * PI * i / count).toFloat()
                    // Classic parametric heart curve
                    val x = 16f * sin(t) * sin(t) * sin(t)
                    val y = -(13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t))
                    val scale = radius / 18f
                    points.add(ShapePoint(x * scale, y * scale))
                }
            }
            ShapePreset.Star -> {
                val vertices = ArrayList<ShapePoint>()
                val innerR = radius * 0.45f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) radius else innerR
                    val angle = (i * PI / 5.0 - PI / 2.0).toFloat()
                    vertices.add(ShapePoint(r * cos(angle), r * sin(angle)))
                }
                samplePolygon(vertices, count, points)
            }
            ShapePreset.Polygon -> {
                // Hexagon
                val vertices = ArrayList<ShapePoint>()
                for (i in 0 until 6) {
                    val angle = (i * PI / 3.0).toFloat()
                    vertices.add(ShapePoint(radius * cos(angle), radius * sin(angle)))
                }
                samplePolygon(vertices, count, points)
            }
            ShapePreset.Diamond -> {
                val vertices = listOf(
                    ShapePoint(0f, -radius),
                    ShapePoint(radius * 0.7f, 0f),
                    ShapePoint(0f, radius),
                    ShapePoint(-radius * 0.7f, 0f)
                )
                samplePolygon(vertices, count, points)
            }
        }
        return points
    }

    private fun samplePolygon(vertices: List<ShapePoint>, totalSamples: Int, out: ArrayList<ShapePoint>) {
        val numEdges = vertices.size
        val samplesPerEdge = totalSamples / numEdges
        val remainder = totalSamples % numEdges

        for (e in 0 until numEdges) {
            val v0 = vertices[e]
            val v1 = vertices[(e + 1) % numEdges]
            val edgeCount = samplesPerEdge + if (e < remainder) 1 else 0
            for (s in 0 until edgeCount) {
                val f = s.toFloat() / edgeCount.toFloat()
                out.add(ShapePoint(v0.x + (v1.x - v0.x) * f, v0.y + (v1.y - v0.y) * f))
            }
        }
    }

    /**
     * Finds rotational shift k that minimizes Euclidean distance sum between A[i] and B[(i+k)%N]
     * to eliminate twisting during vector morphing.
     */
    fun findBestAlignment(a: List<ShapePoint>, b: List<ShapePoint>): Int {
        val n = minOf(a.size, b.size)
        var bestShift = 0
        var minSumDistSq = Float.MAX_VALUE

        for (shift in 0 until n step 2) {
            var sumDistSq = 0f
            for (i in 0 until n step 4) {
                val pA = a[i]
                val pB = b[(i + shift) % n]
                val dx = pA.x - pB.x
                val dy = pA.y - pB.y
                sumDistSq += dx * dx + dy * dy
            }
            if (sumDistSq < minSumDistSq) {
                minSumDistSq = sumDistSq
                bestShift = shift
            }
        }
        return bestShift
    }

    fun applyEasing(t: Float, easing: String): Float {
        val c = t.coerceIn(0f, 1f)
        return when (easing) {
            "Linear" -> c
            "EaseIn" -> c * c * c
            "EaseOut" -> 1f - (1f - c) * (1f - c) * (1f - c)
            "Overshoot" -> {
                val s = 1.70158f
                val p = c - 1f
                p * p * ((s + 1f) * p + s) + 1f
            }
            else -> {
                // EaseInOut
                if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).let { it * it * it } / 2f
            }
        }
    }

    fun interpolate(config: ShapeMorphConfig, elapsedMs: Long, centerX: Float = 0f, centerY: Float = 0f): MorphResult {
        val duration = maxOf(100L, config.durationMs)
        val cycle = elapsedMs % (if (config.reverseOnLoop && config.loop) duration * 2 else duration)
        val rawT = if (config.reverseOnLoop && config.loop && cycle > duration) {
            1f - (cycle - duration).toFloat() / duration.toFloat()
        } else {
            (cycle % duration).toFloat() / duration.toFloat()
        }

        val easedProgress = applyEasing(rawT, config.easing)
        val pointsA = generatePoints(config.startShape, config.size)
        val pointsB = generatePoints(config.endShape, config.size)
        val shift = findBestAlignment(pointsA, pointsB)

        val path = Path()
        val n = minOf(pointsA.size, pointsB.size)

        for (i in 0 until n) {
            val pA = pointsA[i]
            val pB = pointsB[(i + shift) % n]
            val mx = centerX + pA.x + (pB.x - pA.x) * easedProgress
            val my = centerY + pA.y + (pB.y - pA.y) * easedProgress
            if (i == 0) path.moveTo(mx, my) else path.lineTo(mx, my)
        }
        path.close()

        val r = config.startFillColor.red + (config.endFillColor.red - config.startFillColor.red) * easedProgress
        val g = config.startFillColor.green + (config.endFillColor.green - config.startFillColor.green) * easedProgress
        val b = config.startFillColor.blue + (config.endFillColor.blue - config.startFillColor.blue) * easedProgress
        val a = config.startFillColor.alpha + (config.endFillColor.alpha - config.startFillColor.alpha) * easedProgress
        val currentFill = Color(r, g, b, a)

        val currentStroke = config.startStrokeWidth + (config.endStrokeWidth - config.startStrokeWidth) * easedProgress

        return MorphResult(path, easedProgress, currentFill, currentStroke)
    }
}
