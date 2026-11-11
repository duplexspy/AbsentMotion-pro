package app.absentmotion.pro.effects

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object GroupSharpnessEnhancer {

    data class SharpnessParams(
        val intensity: Float = 1.0f,
        val radius: Float = 1.5f,
        val edgePreservation: Float = 0.8f,
        val detailPreservation: Float = 0.9f
    )

    /**
     * Non-destructive unsharp mask with edge-aware guided bilateral weighting.
     * Enhances fine details without amplifying noise or introducing harsh halos along sharp high-contrast edges.
     */
    fun enhance(src: Bitmap, params: SharpnessParams): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0 || params.intensity <= 0f) return src

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val result = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val r = max(1, params.radius.toInt())
        val edgeThreshold = max(0.01f, 1f - params.edgePreservation) * 255f
        val intensity = params.intensity
        val detailFactor = params.detailPreservation

        for (y in 0 until height) {
            val yMin = max(0, y - r)
            val yMax = min(height - 1, y + r)
            for (x in 0 until width) {
                val idx = y * width + x
                val centerPixel = pixels[idx]
                val a = (centerPixel shr 24) and 0xFF
                if (a == 0) {
                    result[idx] = centerPixel
                    continue
                }

                val cr = (centerPixel shr 16) and 0xFF
                val cg = (centerPixel shr 8) and 0xFF
                val cb = centerPixel and 0xFF
                val centerLuma = 0.299f * cr + 0.587f * cg + 0.114f * cb

                // Fast local average
                val xMin = max(0, x - r)
                val xMax = min(width - 1, x + r)
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0

                for (ny in yMin..yMax step max(1, r / 2)) {
                    val rowOffset = ny * width
                    for (nx in xMin..xMax step max(1, r / 2)) {
                        val p = pixels[rowOffset + nx]
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                }

                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count
                val avgLuma = 0.299f * avgR + 0.587f * avgG + 0.114f * avgB

                val diffLuma = abs(centerLuma - avgLuma)
                // Edge attenuation: reduce sharpening on extreme edges to prevent ringing/haloing
                val edgeWeight = exp(-diffLuma / edgeThreshold).coerceIn(0f, 1f)
                val effectiveGain = intensity * (detailFactor + (1f - detailFactor) * edgeWeight)

                val diffR = cr - avgR
                val diffG = cg - avgG
                val diffB = cb - avgB

                val nr = (cr + diffR * effectiveGain).toInt().coerceIn(0, 255)
                val ng = (cg + diffG * effectiveGain).toInt().coerceIn(0, 255)
                val nb = (cb + diffB * effectiveGain).toInt().coerceIn(0, 255)

                result[idx] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        out.setPixels(result, 0, width, 0, 0, width, height)
        return out
    }
}
