@file:Suppress("DEPRECATION")
package app.absentmotion.pro.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.pro.ai.classifier.ModelRoutingMode
import app.absentmotion.pro.ai.tile.AITileProcessor
import app.absentmotion.pro.ai.tile.AITileSettings
import app.absentmotion.pro.ai.tile.TileBatchSize
import app.absentmotion.pro.ai.tile.TileOverlapMode
import app.absentmotion.pro.ai.tile.TilePreviewOverlay
import app.absentmotion.pro.ai.tile.TileProcessingMode
import app.absentmotion.pro.ai.tile.TileSizeMode
import app.absentmotion.pro.labs.AmzLabTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAdvancedSettingsSheet(
    tool: AmzLabTool,
    inputWidth: Int = 1280,
    inputHeight: Int = 720,
    currentTileSettings: AITileSettings,
    onTileSettingsChanged: (AITileSettings) -> Unit,
    routingMode: ModelRoutingMode = ModelRoutingMode.AUTO,
    onRoutingModeChanged: (ModelRoutingMode) -> Unit = {},
    onDismiss: () -> Unit
) {
    var tileSettings by remember { mutableStateOf(currentTileSettings) }
    var selectedRouting by remember { mutableStateOf(routingMode) }

    // Dynamic tile grid estimation
    val resolvedSize = tileSettings.resolveTileSize(256)
    val resolvedOverlap = tileSettings.resolveOverlapPercent(10)
    val grid = remember(inputWidth, inputHeight, resolvedSize, resolvedOverlap) {
        AITileProcessor.calculateGrid(inputWidth, inputHeight, resolvedSize, resolvedOverlap)
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF131722))
                .border(1.dp, Color(0xFF262E44), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Advanced AI Engine Settings",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tool.title,
                            color = Color(0xFF00FF9D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222B3E))
                            .clickable { onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Model Routing (only for Super Resolution / Upscale)
                if (tool == AmzLabTool.AiUpscale) {
                    SectionHeader("Intelligent Model Routing")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0E1017))
                            .padding(10.dp)
                    ) {
                        ModelRoutingMode.entries.forEach { mode ->
                            val isSel = mode == selectedRouting
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Color(0xFF1E283D) else Color.Transparent)
                                    .clickable {
                                        selectedRouting = mode
                                        onRoutingModeChanged(mode)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSel) Color(0xFF00FF9D) else Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSel) {
                                    Text("✓ Active", color = Color(0xFF00FF9D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Tile Processing Engine
                SectionHeader("Adjustable AI Tile Slicing Matrix")

                // Tile Preview
                TilePreviewOverlay(grid = grid)

                Spacer(Modifier.height(12.dp))

                // Tile Size Selector
                Text(
                    text = "Tile Size: ${if (tileSettings.sizeMode == TileSizeMode.CUSTOM) "${tileSettings.customSize}px" else tileSettings.sizeMode.label}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(TileSizeMode.AUTO, TileSizeMode.SIZE_128, TileSizeMode.SIZE_256, TileSizeMode.SIZE_512).forEach { s ->
                        val isSel = tileSettings.sizeMode == s
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color(0xFF00FF9D) else Color(0xFF1E2436))
                                .clickable {
                                    val updated = tileSettings.copy(sizeMode = s)
                                    tileSettings = updated
                                    onTileSettingsChanged(updated)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (s == TileSizeMode.AUTO) "Auto" else "${s.size}px",
                                color = if (isSel) Color(0xFF0D1117) else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Overlap Selector
                Text(
                    text = "Tile Overlap & Seam Feathering: ${if (tileSettings.overlapMode == TileOverlapMode.CUSTOM) "${tileSettings.customOverlapPercent}%" else tileSettings.overlapMode.label}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(TileOverlapMode.AUTO, TileOverlapMode.OVERLAP_0, TileOverlapMode.OVERLAP_10, TileOverlapMode.OVERLAP_15).forEach { o ->
                        val isSel = tileSettings.overlapMode == o
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color(0xFF00FF9D) else Color(0xFF1E2436))
                                .clickable {
                                    val updated = tileSettings.copy(overlapMode = o)
                                    tileSettings = updated
                                    onTileSettingsChanged(updated)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (o == TileOverlapMode.AUTO) "Auto" else "${o.percent}%",
                                color = if (isSel) Color(0xFF0D1117) else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Mode: Sequential vs Multi-Tile
                Text(
                    text = "Tile Execution Mode",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(TileProcessingMode.SEQUENTIAL, TileProcessingMode.MULTI_TILE).forEach { m ->
                        val isSel = tileSettings.processingMode == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00FF9D) else Color(0xFF1E2436))
                                .clickable {
                                    val updated = tileSettings.copy(processingMode = m)
                                    tileSettings = updated
                                    onTileSettingsChanged(updated)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (m == TileProcessingMode.SEQUENTIAL) "Sequential (Safe)" else "Multi-Tile (Fast)",
                                color = if (isSel) Color(0xFF0D1117) else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Tile Batch Size
                Text(
                    text = "Batch Size: ${tileSettings.batchSize.label}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(TileBatchSize.AUTO, TileBatchSize.BATCH_1, TileBatchSize.BATCH_2, TileBatchSize.BATCH_4).forEach { b ->
                        val isSel = tileSettings.batchSize == b
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color(0xFF00FF9D) else Color(0xFF1E2436))
                                .clickable {
                                    val updated = tileSettings.copy(batchSize = b)
                                    tileSettings = updated
                                    onTileSettingsChanged(updated)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (b == TileBatchSize.AUTO) "Auto" else "${b.batch}",
                                color = if (isSel) Color(0xFF0D1117) else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Safety Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E283D))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "🛡️ OOM Auto-Recovery is active. If your device runs out of memory, tiles are automatically halved and processing safely resumes.",
                        color = Color(0xFF4EE2B7),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF00FF9D),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
