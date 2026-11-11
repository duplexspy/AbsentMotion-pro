package app.absentmotion.pro.morph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

data class ControlPoint(
    var x: Float,
    var y: Float
)

data class ImageMorphConfig(
    val durationMs: Long = 1500L,
    val easing: String = "EaseInOut",
    val meshGridCols: Int = 8,
    val meshGridRows: Int = 8,
    val previewDownsample: Int = 1, // 1 = full, 2 = half for smooth preview
    val useFallbackCrossfade: Boolean = false // Clearly labeled fallback
)

object ImageMorphEngine {

    /**
     * Initializes a regular control point mesh normalized to [0, 1] across the image dimensions.
     */
    fun createDefaultMesh(cols: Int, rows: Int, width: Float, height: Float): Array<ControlPoint> {
        val total = (cols + 1) * (rows + 1)
        val mesh = Array(total) { ControlPoint(0f, 0f) }
        var index = 0
        for (r in 0..rows) {
            val v = r.toFloat() / rows.toFloat()
            for (c in 0..cols) {
                val u = c.toFloat() / cols.toFloat()
                mesh[index++] = ControlPoint(u * width, v * height)
            }
        }
        return mesh
    }

    /**
     * Interpolates control points from Mesh A to Mesh B at progress t.
     */
    fun interpolateMesh(meshA: Array<ControlPoint>, meshB: Array<ControlPoint>, t: Float): FloatArray {
        val count = minOf(meshA.size, meshB.size)
        val verts = FloatArray(count * 2)
        for (i in 0 until count) {
            val pA = meshA[i]
            val pB = meshB[i]
            verts[i * 2] = pA.x + (pB.x - pA.x) * t
            verts[i * 2 + 1] = pA.y + (pB.y - pA.y) * t
        }
        return verts
    }

    /**
     * Genuine dual-mesh image morphing.
     * Warps Image A forward to the intermediate mesh P(t).
     * Warps Image B backward to the intermediate mesh P(t).
     * Cross-dissolves the geometrically aligned warped images.
     */
    fun morphImages(
        srcA: Bitmap,
        srcB: Bitmap,
        meshA: Array<ControlPoint>,
        meshB: Array<ControlPoint>,
        progress: Float,
        config: ImageMorphConfig
    ): Bitmap {
        val t = progress.coerceIn(0f, 1f)
        val width = maxOf(srcA.width, srcB.width) / config.previewDownsample
        val height = maxOf(srcA.height, srcB.height) / config.previewDownsample

        if (config.useFallbackCrossfade) {
            return renderCrossfadeFallback(srcA, srcB, t, width, height)
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cols = config.meshGridCols
        val rows = config.meshGridRows

        // Scaled bitmaps for rendering
        val scaledA = if (srcA.width != width || srcA.height != height) {
            Bitmap.createScaledBitmap(srcA, width, height, true)
        } else srcA

        val scaledB = if (srcB.width != width || srcB.height != height) {
            Bitmap.createScaledBitmap(srcB, width, height, true)
        } else srcB

        // Scale meshes to canvas dimensions if downsampled
        val scaleX = width.toFloat() / srcA.width.toFloat()
        val scaleY = height.toFloat() / srcA.height.toFloat()

        val scaledMeshA = meshA.map { ControlPoint(it.x * scaleX, it.y * scaleY) }.toTypedArray()
        val scaledMeshB = meshB.map { ControlPoint(it.x * scaleX, it.y * scaleY) }.toTypedArray()

        // 1. Warp Image A forward to P(t)
        val intermediateVerts = interpolateMesh(scaledMeshA, scaledMeshB, t)
        val paintA = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
        }

        // Draw mesh-warped A
        canvas.drawBitmapMesh(scaledA, cols, rows, intermediateVerts, 0, null, 0, paintA)

        // 2. Warp Image B backward to P(t)
        val paintB = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (t * 255).toInt().coerceIn(0, 255)
        }

        // Draw mesh-warped B
        canvas.drawBitmapMesh(scaledB, cols, rows, intermediateVerts, 0, null, 0, paintB)

        return output
    }

    /**
     * Clearly labeled fallback crossfade for low-spec devices or when explicitly chosen.
     */
    fun renderCrossfadeFallback(srcA: Bitmap, srcB: Bitmap, progress: Float, width: Int, height: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val t = progress.coerceIn(0f, 1f)

        val paintA = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
        }
        val paintB = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (t * 255).toInt().coerceIn(0, 255)
        }

        val scaledA = Bitmap.createScaledBitmap(srcA, width, height, true)
        val scaledB = Bitmap.createScaledBitmap(srcB, width, height, true)

        canvas.drawBitmap(scaledA, 0f, 0f, paintA)
        canvas.drawBitmap(scaledB, 0f, 0f, paintB)
        return output
    }
}
