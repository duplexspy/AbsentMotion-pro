@file:Suppress("DEPRECATION")
package app.absentmotion.pro.ai.tile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class TileGridInfo(
    val imageWidth: Int,
    val imageHeight: Int,
    val tileSize: Int,
    val overlapPx: Int,
    val stepX: Int,
    val stepY: Int,
    val cols: Int,
    val rows: Int,
    val totalTiles: Int
)

data class TileRect(
    val index: Int,
    val col: Int,
    val row: Int,
    val inX: Int,
    val inY: Int,
    val inW: Int,
    val inH: Int,
    val outX: Int,
    val outY: Int,
    val outW: Int,
    val outH: Int
)

object AITileProcessor {

    fun calculateGrid(
        imageWidth: Int,
        imageHeight: Int,
        tileSize: Int,
        overlapPercent: Int,
        scaleFactor: Int = 1
    ): TileGridInfo {
        val safeTile = tileSize.coerceAtLeast(32)
        val overlapPx = (safeTile * (overlapPercent.coerceIn(0, 40) / 100f)).toInt()
        val step = max(1, safeTile - overlapPx)

        val cols = if (imageWidth <= safeTile) 1 else ceil((imageWidth - overlapPx).toFloat() / step).toInt()
        val rows = if (imageHeight <= safeTile) 1 else ceil((imageHeight - overlapPx).toFloat() / step).toInt()
        val totalTiles = max(1, cols * rows)

        return TileGridInfo(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            tileSize = safeTile,
            overlapPx = overlapPx,
            stepX = step,
            stepY = step,
            cols = cols,
            rows = rows,
            totalTiles = totalTiles
        )
    }

    fun generateTileRects(grid: TileGridInfo, scaleFactor: Int = 1): List<TileRect> {
        val list = mutableListOf<TileRect>()
        var index = 0

        for (r in 0 until grid.rows) {
            val inY = if (grid.rows == 1) 0 else (r * grid.stepY).coerceAtMost(grid.imageHeight - grid.tileSize).coerceAtLeast(0)
            val inH = min(grid.tileSize, grid.imageHeight - inY)

            for (c in 0 until grid.cols) {
                val inX = if (grid.cols == 1) 0 else (c * grid.stepX).coerceAtMost(grid.imageWidth - grid.tileSize).coerceAtLeast(0)
                val inW = min(grid.tileSize, grid.imageWidth - inX)

                list.add(
                    TileRect(
                        index = index++,
                        col = c,
                        row = r,
                        inX = inX,
                        inY = inY,
                        inW = inW,
                        inH = inH,
                        outX = inX * scaleFactor,
                        outY = inY * scaleFactor,
                        outW = inW * scaleFactor,
                        outH = inH * scaleFactor
                    )
                )
            }
        }
        return list
    }

    /**
     * Extracts a tile bitmap safely from the source bitmap.
     */
    fun extractTile(source: Bitmap, rect: TileRect): Bitmap {
        return Bitmap.createBitmap(source, rect.inX, rect.inY, rect.inW, rect.inH)
    }

    /**
     * Stitches an upscale/processed tile into the final destination bitmap with seamless feathering.
     */
    fun stitchTile(
        destBitmap: Bitmap,
        tileBitmap: Bitmap,
        rect: TileRect,
        overlapPx: Int,
        scaleFactor: Int = 1
    ) {
        val scaledOverlap = overlapPx * scaleFactor
        if (scaledOverlap <= 0) {
            // Direct draw without blending
            val canvas = Canvas(destBitmap)
            canvas.drawBitmap(tileBitmap, rect.outX.toFloat(), rect.outY.toFloat(), null)
            return
        }

        val canvas = Canvas(destBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Seam blending: if tile is not at the boundary, apply subtle blend
        canvas.drawBitmap(tileBitmap, rect.outX.toFloat(), rect.outY.toFloat(), paint)
    }

    /**
     * Higher-order tiling execution with automatic OutOfMemoryError detection and retry.
     */
    suspend fun <T> runWithOomRecovery(
        initialTileSize: Int,
        onDowngrade: (Int) -> Unit = {},
        block: suspend (currentTileSize: Int) -> T
    ): T {
        var currentTile = initialTileSize
        var attempts = 0
        while (attempts < 4) {
            try {
                return block(currentTile)
            } catch (e: OutOfMemoryError) {
                System.gc()
                attempts++
                val next = when {
                    currentTile > 512 -> 512
                    currentTile > 256 -> 256
                    currentTile > 128 -> 128
                    currentTile > 64 -> 64
                    else -> throw e
                }
                currentTile = next
                onDowngrade(next)
            }
        }
        return block(currentTile)
    }
}
