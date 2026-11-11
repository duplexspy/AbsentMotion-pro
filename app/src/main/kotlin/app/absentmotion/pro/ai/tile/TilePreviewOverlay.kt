package app.absentmotion.pro.ai.tile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TilePreviewOverlay(
    grid: TileGridInfo,
    activeTileIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val aspectRatio = if (grid.imageHeight > 0) grid.imageWidth.toFloat() / grid.imageHeight else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF16161A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2A2A32), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Tile Slicing Matrix",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = "${grid.cols} × ${grid.rows} (${grid.totalTiles} tiles)",
                color = Color(0xFF4EE2B7),
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .aspectRatio(aspectRatio.coerceIn(0.5f, 2.5f))
                    .fillMaxSize()
                    .background(Color(0xFF0F0F12), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF3B3B48), RoundedCornerShape(6.dp))
            ) {
                val canvasW = size.width
                val canvasH = size.height

                val scaleX = canvasW / grid.imageWidth
                val scaleY = canvasH / grid.imageHeight

                var idx = 0
                for (r in 0 until grid.rows) {
                    val inY = if (grid.rows == 1) 0 else (r * grid.stepY).coerceAtMost(grid.imageHeight - grid.tileSize).coerceAtLeast(0)
                    val inH = kotlin.math.min(grid.tileSize, grid.imageHeight - inY)

                    for (c in 0 until grid.cols) {
                        val inX = if (grid.cols == 1) 0 else (c * grid.stepX).coerceAtMost(grid.imageWidth - grid.tileSize).coerceAtLeast(0)
                        val inW = kotlin.math.min(grid.tileSize, grid.imageWidth - inX)

                        val rx = inX * scaleX
                        val ry = inY * scaleY
                        val rw = inW * scaleX
                        val rh = inH * scaleY

                        val isActive = idx == activeTileIndex
                        val isFinished = activeTileIndex > idx

                        val fillColor = when {
                            isActive -> Color(0x664EE2B7)
                            isFinished -> Color(0x333B82F6)
                            else -> Color(0x15FFFFFF)
                        }

                        val strokeColor = when {
                            isActive -> Color(0xFF4EE2B7)
                            isFinished -> Color(0xFF3B82F6)
                            else -> Color(0x55888899)
                        }

                        drawRect(
                            color = fillColor,
                            topLeft = Offset(rx, ry),
                            size = Size(rw, rh)
                        )

                        drawRect(
                            color = strokeColor,
                            topLeft = Offset(rx, ry),
                            size = Size(rw, rh),
                            style = Stroke(width = if (isActive) 2.5f else 1f)
                        )

                        idx++
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tile: ${grid.tileSize}px  |  Overlap: ${grid.overlapPx}px",
                color = Color(0xFFA0A0B0),
                fontSize = 11.sp
            )
            Text(
                text = "Res: ${grid.imageWidth}×${grid.imageHeight}",
                color = Color(0xFFA0A0B0),
                fontSize = 11.sp
            )
        }
    }
}
