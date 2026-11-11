package app.absentmotion.pro.text3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.ui.theme.Am

@Composable
fun Text3dControlsPanel(
    config: Text3dConfig,
    animator: CharacterAnimatorConfig,
    onConfigChange: (Text3dConfig) -> Unit,
    onAnimatorChange: (CharacterAnimatorConfig) -> Unit,
    sampleText: String = "3D TEXT",
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: 3D Geometry, 1: Materials & Light, 2: Character Anim

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF181A24))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Live 3D Text Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F111A))
                .border(1.dp, Color(0xFF282B3E), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val tf = android.graphics.Typeface.DEFAULT_BOLD
                var textSizePx = 28.dp.toPx()
                val previewStr = if (sampleText.isNotBlank()) sampleText.take(16) else "3D TEXT"
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = textSizePx
                }
                var textWidth = paint.measureText(previewStr)
                val maxAllowedWidth = size.width - 36.dp.toPx()
                if (textWidth > maxAllowedWidth && maxAllowedWidth > 0f) {
                    val scale = (maxAllowedWidth / textWidth).coerceIn(0.4f, 1f)
                    textSizePx *= scale
                    paint.textSize = textSizePx
                    textWidth = paint.measureText(previewStr)
                }
                val textX = (size.width - textWidth) / 2f
                val textY = (size.height / 2f) + (textSizePx / 3f)

                Text3dRenderer.render(
                    canvas = nativeCanvas,
                    text = previewStr,
                    typeface = tf,
                    textSize = textSizePx,
                    config = config,
                    animator = animator,
                    elapsedMs = 400L,
                    originX = textX,
                    originY = textY
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF242738))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("3D Geometry", "Material & Light", "Character Anim").forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Am.Accent else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (selectedTab) {
            0 -> {
                // 3D Geometry
                SectionTitle("EXTRUSION DEPTH (${config.extrusionDepth.toInt()} px)")
                Slider(
                    value = config.extrusionDepth,
                    onValueChange = { onConfigChange(config.copy(extrusionDepth = it)) },
                    valueRange = 0f..80f,
                    colors = sliderColors()
                )

                SectionTitle("BEVEL SIZE (${config.bevelSize.toInt()} px)")
                Slider(
                    value = config.bevelSize,
                    onValueChange = { onConfigChange(config.copy(bevelSize = it)) },
                    valueRange = 0f..12f,
                    colors = sliderColors()
                )

                SectionTitle("PERSPECTIVE FOV (${config.perspectiveFov.toInt()}°)")
                Slider(
                    value = config.perspectiveFov,
                    onValueChange = { onConfigChange(config.copy(perspectiveFov = it)) },
                    valueRange = 15f..90f,
                    colors = sliderColors()
                )
            }
            1 -> {
                // Material & Lighting
                SectionTitle("SURFACE MATERIAL")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MaterialType.entries.forEach { mat ->
                        val active = config.material == mat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Am.Accent.copy(alpha = 0.2f) else Color(0xFF282B3E))
                                .border(1.dp, if (active) Am.Accent else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { onConfigChange(config.copy(material = mat)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(mat.displayName, color = if (active) Am.Accent else Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionTitle("LIGHT DIRECTION (${config.lightAngleDeg.toInt()}°)")
                Slider(
                    value = config.lightAngleDeg,
                    onValueChange = { onConfigChange(config.copy(lightAngleDeg = it)) },
                    valueRange = 0f..360f,
                    colors = sliderColors()
                )

                SectionTitle("LIGHT INTENSITY (${(config.directionalLightIntensity * 100).toInt()}%)")
                Slider(
                    value = config.directionalLightIntensity,
                    onValueChange = { onConfigChange(config.copy(directionalLightIntensity = it)) },
                    valueRange = 0f..1.5f,
                    colors = sliderColors()
                )

                SectionTitle("NEON GLOW RADIUS (${config.glowRadius.toInt()} px)")
                Slider(
                    value = config.glowRadius,
                    onValueChange = { onConfigChange(config.copy(glowRadius = it)) },
                    valueRange = 0f..40f,
                    colors = sliderColors()
                )
            }
            2 -> {
                // Character Animation (Node Video inspired)
                SectionTitle("ANIMATION PRESET")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CharacterAnimatorConfig.Presets.forEach { preset ->
                        val active = animator.presetName == preset.presetName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Am.Accent.copy(alpha = 0.25f) else Color(0xFF282B3E))
                                .border(1.dp, if (active) Am.Accent else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { onAnimatorChange(preset) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                preset.presetName,
                                color = if (active) Am.Accent else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                SectionTitle("SELECTOR UNIT")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CharacterSelectorUnit.entries.forEach { unit ->
                        val active = animator.unit == unit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) Am.Accent.copy(alpha = 0.25f) else Color(0xFF282B3E))
                                .border(1.dp, if (active) Am.Accent else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { onAnimatorChange(animator.copy(unit = unit)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(unit.name, color = if (active) Am.Accent else Color.White, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                SectionTitle("STAGGER TIMING (${animator.staggerMs} ms)")
                Slider(
                    value = animator.staggerMs.toFloat(),
                    onValueChange = { onAnimatorChange(animator.copy(staggerMs = it.toLong())) },
                    valueRange = 10f..250f,
                    colors = sliderColors()
                )

                SectionTitle("UNIT DURATION (${animator.durationPerUnitMs} ms)")
                Slider(
                    value = animator.durationPerUnitMs.toFloat(),
                    onValueChange = { onAnimatorChange(animator.copy(durationPerUnitMs = it.toLong())) },
                    valueRange = 100f..1500f,
                    colors = sliderColors()
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF9EA3BA),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Am.Accent,
    activeTrackColor = Am.Accent,
    inactiveTrackColor = Color(0xFF383C52)
)
