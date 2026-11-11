package app.absentmotion.pro.morph

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.editor.EditorState
import app.absentmotion.ui.theme.Am

@Composable
fun ShapeMorphPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("Shape Morph") }
    var startShape by remember { mutableStateOf(ShapePreset.Triangle) }
    var endShape by remember { mutableStateOf(ShapePreset.Circle) }
    var durationMs by remember { mutableStateOf(1000L) }
    var easing by remember { mutableStateOf("EaseInOut") }
    var loop by remember { mutableStateOf(true) }
    var reverse by remember { mutableStateOf(true) }
    var meshGridCols by remember { mutableStateOf(8) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1E2B))
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MORPH STUDIO", color = Am.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2E3246))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Close", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Tab Selector: Shape Morph vs Image Morph
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF131520)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Shape Morph", "Image Morph").forEach { tab ->
                val selected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Am.Accent else Color.Transparent)
                        .clickable { activeTab = tab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (activeTab == "Shape Morph") {
            Text("START SHAPE", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShapePreset.entries.forEach { preset ->
                    val active = startShape == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Am.Accent else Color(0xFF282B3E))
                            .clickable { startShape = preset }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(preset.displayName, color = if (active) Color.Black else Color.White, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("TARGET SHAPE", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShapePreset.entries.forEach { preset ->
                    val active = endShape == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Am.Accent else Color(0xFF282B3E))
                            .clickable { endShape = preset }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(preset.displayName, color = if (active) Color.Black else Color.White, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Dual-Mesh Image Morph Controls
            Text("DUAL-MESH IMAGE MORPH SETTINGS", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                "Warps image coordinates through a deformable control grid and seamlessly transitions between photos.",
                color = Color(0xFF8E9AA8),
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text("MESH RESOLUTION: ${meshGridCols}x${meshGridCols}", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(4, 8, 16).forEach { res ->
                    val active = meshGridCols == res
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Am.Accent else Color(0xFF282B3E))
                            .clickable { meshGridCols = res }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${res}x${res} Grid", color = if (active) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("DURATION: ${durationMs} ms", color = Color(0xFF9EA3BA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = durationMs.toFloat(),
            onValueChange = { durationMs = it.toLong() },
            valueRange = 300f..3000f,
            colors = SliderDefaults.colors(thumbColor = Am.Accent, activeTrackColor = Am.Accent)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Loop & Re-reverse", color = Color.White, fontSize = 12.sp)
            Switch(
                checked = loop,
                onCheckedChange = { loop = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Am.Accent, checkedTrackColor = Am.Accent.copy(alpha = 0.5f))
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Am.Accent)
                .clickable {
                    if (activeTab == "Shape Morph") {
                        val config = ShapeMorphConfig(
                            startShape = startShape,
                            endShape = endShape,
                            durationMs = durationMs,
                            easing = easing,
                            loop = loop,
                            reverseOnLoop = reverse
                        )
                        state.editSelected("shape-morph-create") { layer ->
                            layer.copy(
                                node = layer.node
                                    .with("pro.morph.start", startShape.name)
                                    .with("pro.morph.end", endShape.name)
                                    .with("pro.morph.duration", durationMs.toString())
                                    .with("pro.morph.easing", easing)
                                    .with("pro.morph.loop", loop.toString())
                            )
                        }
                        Toast.makeText(context, "Morphed ${startShape.displayName} to ${endShape.displayName}", Toast.LENGTH_SHORT).show()
                    } else {
                        state.editSelected("image-morph-create") { layer ->
                            layer.copy(
                                node = layer.node
                                    .with("pro.morph.type", "image")
                                    .with("pro.morph.grid", meshGridCols.toString())
                                    .with("pro.morph.duration", durationMs.toString())
                                    .with("pro.morph.easing", easing)
                                    .with("pro.morph.loop", loop.toString())
                            )
                        }
                        Toast.makeText(context, "Applied Dual-Mesh Image Morph (${meshGridCols}x${meshGridCols})", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (activeTab == "Shape Morph") "Apply Shape Morph" else "Apply Image Morph", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
