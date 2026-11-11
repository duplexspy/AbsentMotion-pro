package app.absentmotion.pro.ai.classifier

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

enum class ContentType(val displayName: String, val description: String) {
    REAL_LIFE_PHOTO("Real-Life Photo", "Natural camera photograph with complex textures"),
    REAL_LIFE_VIDEO("Real-Life Video", "Live-action camera footage"),
    ANIME_ILLUSTRATION("Anime / Manga / 2D Art", "Flat color shading with dark line art"),
    ANIME_VIDEO("Anime Animation Video", "Animated 2D series with keyframe drawings"),
    GENERAL_MIXED("General Mixed Content", "Hybrid graphics or uncertain classification")
}

data class ClassificationResult(
    val detectedType: ContentType,
    val animeConfidence: Float,
    val edgeDensity: Float,
    val flatColorAreaRatio: Float,
    val averageSaturation: Float
)

object AIContentClassifier {

    /**
     * Analyzes a bitmap using edge detection, color saturation, and flat-area detection.
     * Samples are downscaled to 128x128 for ultra-fast processing (< 5ms).
     */
    fun classifyBitmap(bitmap: Bitmap, isVideo: Boolean = false): ClassificationResult {
        val sampleSize = 128
        val sample = if (bitmap.width != sampleSize || bitmap.height != sampleSize) {
            Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
        } else {
            bitmap
        }

        val pixels = IntArray(sampleSize * sampleSize)
        sample.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        if (sample !== bitmap) {
            sample.recycle()
        }

        var totalSaturation = 0f
        var flatPixels = 0
        var strongEdges = 0
        var totalEdges = 0f

        val hsv = FloatArray(3)

        // Pass 1: Saturation & color stats
        for (i in pixels.indices) {
            val c = pixels[i]
            Color.colorToHSV(c, hsv)
            totalSaturation += hsv[1]
        }
        val avgSat = totalSaturation / pixels.size

        // Pass 2: Sobel / Laplacian Edge analysis and flat color run detection
        for (y in 1 until sampleSize - 1) {
            for (x in 1 until sampleSize - 1) {
                val idx = y * sampleSize + x
                val c = pixels[idx]

                // Grayscale intensity
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

                val cLeft = pixels[idx - 1]
                val lumLeft = (Color.red(cLeft) * 299 + Color.green(cLeft) * 587 + Color.blue(cLeft) * 114) / 1000

                val cRight = pixels[idx + 1]
                val lumRight = (Color.red(cRight) * 299 + Color.green(cRight) * 587 + Color.blue(cRight) * 114) / 1000

                val cTop = pixels[idx - sampleSize]
                val lumTop = (Color.red(cTop) * 299 + Color.green(cTop) * 587 + Color.blue(cTop) * 114) / 1000

                val cBottom = pixels[idx + sampleSize]
                val lumBottom = (Color.red(cBottom) * 299 + Color.green(cBottom) * 587 + Color.blue(cBottom) * 114) / 1000

                val dx = abs(lumRight - lumLeft)
                val dy = abs(lumBottom - lumTop)
                val gradient = sqrt((dx * dx + dy * dy).toFloat())

                totalEdges += gradient

                // High gradient with sharp line
                if (gradient > 65f) {
                    strongEdges++
                } else if (gradient < 8f) {
                    // Very flat color region (characteristic of anime cel shading)
                    flatPixels++
                }
            }
        }

        val innerPixels = (sampleSize - 2) * (sampleSize - 2)
        val edgeDensity = strongEdges.toFloat() / innerPixels
        val flatRatio = flatPixels.toFloat() / innerPixels

        // Anime / Cartoon typically has:
        // - High flat color area ratio (> 0.40)
        // - Higher color saturation (> 0.35)
        // - Sharp distinct edges along cell outlines
        var animeScore = 0f
        if (flatRatio > 0.35f) animeScore += 0.4f
        if (flatRatio > 0.50f) animeScore += 0.2f
        if (avgSat > 0.32f) animeScore += 0.25f
        if (edgeDensity in 0.04f..0.25f) animeScore += 0.15f

        val detectedType = when {
            animeScore >= 0.60f -> if (isVideo) ContentType.ANIME_VIDEO else ContentType.ANIME_ILLUSTRATION
            animeScore <= 0.35f -> if (isVideo) ContentType.REAL_LIFE_VIDEO else ContentType.REAL_LIFE_PHOTO
            else -> ContentType.GENERAL_MIXED
        }

        return ClassificationResult(
            detectedType = detectedType,
            animeConfidence = animeScore.coerceIn(0f, 1f),
            edgeDensity = edgeDensity,
            flatColorAreaRatio = flatRatio,
            averageSaturation = avgSat
        )
    }
}
