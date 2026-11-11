@file:Suppress("DEPRECATION")

package app.absentmotion.pro

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.editor.EditorState
import app.absentmotion.effects.EffectCatalog
import app.absentmotion.model.XmlNode
import app.absentmotion.model.addEffect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class CinematicLut(
    val name: String,
    val subtitle: String,
    val temp: Float = 0f,
    val tint: Float = 0f,
    val contrast: Float = 0f,
    val exposure: Float = 0f,
    val sat: Float = 1f,
    val vib: Float = 0f,
    val liftHue: Float = 0f,
    val liftAmt: Float = 0f,
    val gammaHue: Float = 0f,
    val gammaAmt: Float = 0f,
    val gainHue: Float = 0f,
    val gainAmt: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f
)

val ProCinematicLuts = listOf(
    CinematicLut(
        name = "Teal & Orange",
        subtitle = "Hollywood Blockbuster",
        temp = 0.15f,
        contrast = 0.25f,
        sat = 1.15f,
        liftHue = 195f,
        liftAmt = 0.35f,
        gainHue = 32f,
        gainAmt = 0.40f,
        vignette = 0.20f
    ),
    CinematicLut(
        name = "Kodak Portra 400",
        subtitle = "Warm Organic 35mm Film",
        temp = 0.20f,
        tint = -0.05f,
        contrast = 0.10f,
        sat = 0.95f,
        liftHue = 45f,
        liftAmt = 0.15f,
        grain = 0.25f
    ),
    CinematicLut(
        name = "Fuji Eterna",
        subtitle = "Muted Cinema Palette",
        contrast = -0.15f,
        sat = 0.85f,
        vib = -0.10f,
        liftHue = 140f,
        liftAmt = 0.10f,
        vignette = 0.15f
    ),
    CinematicLut(
        name = "Cyberpunk 2077",
        subtitle = "Neon Cyan & Hot Magenta",
        contrast = 0.35f,
        sat = 1.30f,
        vib = 0.40f,
        liftHue = 185f,
        liftAmt = 0.50f,
        gainHue = 310f,
        gainAmt = 0.45f,
        vignette = 0.30f
    ),
    CinematicLut(
        name = "Moody Emerald",
        subtitle = "Dark Forest & Crushed Blacks",
        contrast = 0.20f,
        sat = 0.90f,
        liftHue = 150f,
        liftAmt = 0.30f,
        gainHue = 50f,
        gainAmt = 0.15f,
        vignette = 0.35f
    ),
    CinematicLut(
        name = "Golden Hour",
        subtitle = "Warm Sunset Radiance",
        temp = 0.45f,
        tint = -0.10f,
        exposure = 0.10f,
        contrast = 0.15f,
        sat = 1.10f,
        gainHue = 38f,
        gainAmt = 0.50f
    ),
    CinematicLut(
        name = "Retro 80s VHS",
        subtitle = "Analog Tape Warmth & Noise",
        temp = 0.10f,
        contrast = -0.10f,
        sat = 1.20f,
        liftHue = 270f,
        liftAmt = 0.20f,
        gainHue = 60f,
        gainAmt = 0.25f,
        grain = 0.45f
    ),
    CinematicLut(
        name = "Bleach Bypass",
        subtitle = "Harsh Gritty Action Contrast",
        contrast = 0.55f,
        sat = 0.40f,
        vib = -0.30f,
        liftHue = 210f,
        liftAmt = 0.20f,
        vignette = 0.40f,
        grain = 0.20f
    ),
    CinematicLut(
        name = "Film Noir",
        subtitle = "Dramatic Monochrome Shadows",
        contrast = 0.45f,
        sat = 0.0f,
        liftHue = 0f,
        liftAmt = 0.0f,
        vignette = 0.50f,
        grain = 0.30f
    ),
    CinematicLut(
        name = "Arctic Blue",
        subtitle = "Cold Nordic Thriller",
        temp = -0.45f,
        tint = 0.05f,
        contrast = 0.20f,
        sat = 0.85f,
        liftHue = 215f,
        liftAmt = 0.40f
    ),
    CinematicLut(
        name = "Pastel Dream",
        subtitle = "Soft Lifted Highlights",
        contrast = -0.20f,
        exposure = 0.20f,
        sat = 0.90f,
        vib = 0.20f,
        liftHue = 320f,
        liftAmt = 0.15f,
        gainHue = 45f,
        gainAmt = 0.20f
    ),
    CinematicLut(
        name = "Autumn Foliage",
        subtitle = "Deep Amber & Copper Tones",
        temp = 0.30f,
        contrast = 0.15f,
        sat = 1.15f,
        gainHue = 25f,
        gainAmt = 0.45f
    ),
    CinematicLut(
        name = "Matrix Sci-Fi",
        subtitle = "Digital Green Phosphor",
        contrast = 0.25f,
        sat = 0.80f,
        liftHue = 115f,
        liftAmt = 0.40f,
        gainHue = 125f,
        gainAmt = 0.30f
    ),
    CinematicLut(
        name = "Commercial Clean",
        subtitle = "Crisp Accurate Color Punch",
        exposure = 0.05f,
        contrast = 0.15f,
        sat = 1.05f,
        vib = 0.15f
    ),
    CinematicLut(
        name = "Monolith Dark",
        subtitle = "Deep Matte Black & Silver",
        contrast = 0.30f,
        sat = 0.60f,
        liftHue = 220f,
        liftAmt = 0.20f,
        vignette = 0.35f
    ),
    CinematicLut(
        name = "Sunset Boulevard",
        subtitle = "Twilight Violet & Bronze",
        temp = 0.20f,
        tint = 0.25f,
        contrast = 0.20f,
        liftHue = 265f,
        liftAmt = 0.35f,
        gainHue = 30f,
        gainAmt = 0.40f
    )
)

/**
 * Massive Pro Color Grading Studio Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorGradingStudioDialog(
    state: EditorState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Wheels, 1: Primaries, 2: LUTs, 3: Finishing

    // Wheels State
    var liftHue by remember { mutableFloatStateOf(0f) }
    var liftAmt by remember { mutableFloatStateOf(0f) }
    var gammaHue by remember { mutableFloatStateOf(0f) }
    var gammaAmt by remember { mutableFloatStateOf(0f) }
    var gainHue by remember { mutableFloatStateOf(0f) }
    var gainAmt by remember { mutableFloatStateOf(0f) }

    // Primaries State
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var tint by remember { mutableFloatStateOf(0f) }
    var highlights by remember { mutableFloatStateOf(0f) }
    var shadows by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }
    var vibrance by remember { mutableFloatStateOf(0f) }

    // Finishing State
    var vignette by remember { mutableFloatStateOf(0f) }
    var grain by remember { mutableFloatStateOf(0f) }

    fun resetAll() {
        liftHue = 0f; liftAmt = 0f
        gammaHue = 0f; gammaAmt = 0f
        gainHue = 0f; gainAmt = 0f
        exposure = 0f; contrast = 0f
        temperature = 0f; tint = 0f
        highlights = 0f; shadows = 0f
        saturation = 1.0f; vibrance = 0f
        vignette = 0f; grain = 0f
    }

    fun applyLut(lut: CinematicLut) {
        temperature = lut.temp
        tint = lut.tint
        contrast = lut.contrast
        exposure = lut.exposure
        saturation = lut.sat
        vibrance = lut.vib
        liftHue = lut.liftHue
        liftAmt = lut.liftAmt
        gammaHue = lut.gammaHue
        gammaAmt = lut.gammaAmt
        gainHue = lut.gainHue
        gainAmt = lut.gainAmt
        vignette = lut.vignette
        grain = lut.grain
    }

    fun commitToSelectedLayer() {
        val selected = state.selected
        if (selected == null) {
            Toast.makeText(context, "Please select a layer to apply Color Grading", Toast.LENGTH_SHORT).show()
            return
        }

        val effectNode = XmlNode(
            "effect",
            mapOf("id" to "colorgrade"),
            listOf(
                XmlNode("property", mapOf("name" to "exposure", "value" to "$exposure")),
                XmlNode("property", mapOf("name" to "contrast", "value" to "$contrast")),
                XmlNode("property", mapOf("name" to "temperature", "value" to "$temperature")),
                XmlNode("property", mapOf("name" to "tint", "value" to "$tint")),
                XmlNode("property", mapOf("name" to "highlights", "value" to "$highlights")),
                XmlNode("property", mapOf("name" to "shadows", "value" to "$shadows")),
                XmlNode("property", mapOf("name" to "saturation", "value" to "$saturation")),
                XmlNode("property", mapOf("name" to "vibrance", "value" to "$vibrance")),
                XmlNode("property", mapOf("name" to "liftHue", "value" to "$liftHue")),
                XmlNode("property", mapOf("name" to "liftAmount", "value" to "$liftAmt")),
                XmlNode("property", mapOf("name" to "gammaHue", "value" to "$gammaHue")),
                XmlNode("property", mapOf("name" to "gammaAmount", "value" to "$gammaAmt")),
                XmlNode("property", mapOf("name" to "gainHue", "value" to "$gainHue")),
                XmlNode("property", mapOf("name" to "gainAmount", "value" to "$gainAmt")),
                XmlNode("property", mapOf("name" to "vignette", "value" to "$vignette")),
                XmlNode("property", mapOf("name" to "grain", "value" to "$grain"))
            )
        )

        state.editSelected { layer ->
            // Replace existing colorgrade effect if present, or append
            val filteredChildren = layer.node.children.filterNot { it.name == "effect" && it["id"] == "colorgrade" }
            layer.copy(node = layer.node.copy(children = filteredChildren + effectNode))
        }

        Toast.makeText(context, "Applied Pro Color Grade to layer!", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF10131C))
                .border(1.dp, Color(0xFF242A3D), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Color Grading Studio",
                            color = Color(0xFF00FF9D),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "3-Way Wheels • Tone Curve • 16 Cinematic LUTs • Film Grain",
                            color = Color(0xFF8E9AA8),
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222838))
                                .clickable { resetAll() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Reset", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222838))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Navigation Tabs
                val tabs = listOf("3-Way Wheels", "Primaries & Tone", "Cinematic LUTs", "Finishing")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF171B26))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { idx, title ->
                        val active = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0xFF00FF9D) else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (active) Color.Black else Color(0xFF8E9AA8),
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Tab Content Body
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> {
                            // 3-Way Color Wheels
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ColorWheelWidget(
                                        title = "Shadows (Lift)",
                                        hue = liftHue,
                                        amount = liftAmt,
                                        onHueChange = { liftHue = it },
                                        onAmtChange = { liftAmt = it }
                                    )

                                    ColorWheelWidget(
                                        title = "Midtones (Gamma)",
                                        hue = gammaHue,
                                        amount = gammaAmt,
                                        onHueChange = { gammaHue = it },
                                        onAmtChange = { gammaAmt = it }
                                    )

                                    ColorWheelWidget(
                                        title = "Highlights (Gain)",
                                        hue = gainHue,
                                        amount = gainAmt,
                                        onHueChange = { gainHue = it },
                                        onAmtChange = { gainAmt = it }
                                    )
                                }
                            }
                        }
                        1 -> {
                            // Primaries & Tone Adjustments
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp)
                            ) {
                                GradingSliderRow("Exposure", exposure, -2.0f, 2.0f, "EV") { exposure = it }
                                GradingSliderRow("Contrast", contrast, -1.0f, 1.0f, "") { contrast = it }
                                GradingSliderRow("Temperature", temperature, -1.0f, 1.0f, "") { temperature = it }
                                GradingSliderRow("Tint", tint, -1.0f, 1.0f, "") { tint = it }
                                GradingSliderRow("Highlights", highlights, -1.0f, 1.0f, "") { highlights = it }
                                GradingSliderRow("Shadows", shadows, -1.0f, 1.0f, "") { shadows = it }
                                GradingSliderRow("Saturation", saturation, 0.0f, 2.0f, "x") { saturation = it }
                                GradingSliderRow("Vibrance", vibrance, -1.0f, 1.0f, "") { vibrance = it }
                            }
                        }
                        2 -> {
                            // 16 Cinematic LUT Presets
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ProCinematicLuts.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { lut ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF1B202E))
                                                    .border(1.dp, Color(0xFF2C344A), RoundedCornerShape(10.dp))
                                                    .clickable { applyLut(lut) }
                                                    .padding(10.dp)
                                            ) {
                                                Column {
                                                    Text(lut.name, color = Color(0xFF00FF9D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text(lut.subtitle, color = Color(0xFF8E9AA8), fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Finishing (Vignette & Film Grain)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp)
                            ) {
                                GradingSliderRow("Vignette Amount", vignette, 0.0f, 1.0f, "") { vignette = it }
                                GradingSliderRow("Film Grain Intensity", grain, 0.0f, 1.0f, "") { grain = it }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Apply to Layer Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00FF9D))
                        .clickable { commitToSelectedLayer() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Apply Color Grade to Layer",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ColorWheelWidget(
    title: String,
    hue: Float,
    amount: Float,
    onHueChange: (Float) -> Unit,
    onAmtChange: (Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        val wheelSize = 88.dp
        Box(
            modifier = Modifier
                .size(wheelSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val offset = change.position - center
                        val radius = size.width / 2f
                        val dist = min(1f, sqrt(offset.x * offset.x + offset.y * offset.y) / radius)
                        var angleDeg = Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())).toFloat()
                        if (angleDeg < 0) angleDeg += 360f

                        onHueChange(angleDeg)
                        onAmtChange(dist)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Draw chromatic ring
                val sweepColors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
                drawCircle(
                    brush = Brush.sweepGradient(sweepColors, center),
                    radius = radius - 3.dp.toPx(),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw central dark disc
                drawCircle(color = Color(0xFF141722), radius = radius - 7.dp.toPx())

                // Draw active indicator pointer
                val rad = Math.toRadians(hue.toDouble())
                val pointerDist = amount * (radius - 9.dp.toPx())
                val pointerX = center.x + (pointerDist * cos(rad)).toFloat()
                val pointerY = center.y + (pointerDist * sin(rad)).toFloat()

                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(pointerX, pointerY))
                drawCircle(color = Color(0xFF00FF9D), radius = 3.dp.toPx(), center = Offset(pointerX, pointerY))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("${(hue).roundToInt()}° / ${(amount * 100).roundToInt()}%", color = Color(0xFF8E9AA8), fontSize = 10.sp)
    }
}

@Composable
fun GradingSliderRow(
    label: String,
    value: Float,
    minVal: Float,
    maxVal: Float,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "${(value * 100).roundToInt() / 100f} $unit".trim(),
                color = Color(0xFF00FF9D),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = minVal..maxVal,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00FF9D),
                activeTrackColor = Color(0xFF00FF9D),
                inactiveTrackColor = Color(0xFF262C3E)
            ),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
    }
}
