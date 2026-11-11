package app.absentmotion.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import app.absentmotion.R
import app.absentmotion.editor.EditorState
import app.absentmotion.editor.KeypadField
import app.absentmotion.editor.KeypadRequest
import app.absentmotion.editor.LayerPoints
import app.absentmotion.editor.toggleKeyframe
import app.absentmotion.model.PropRef
import app.absentmotion.effects.EffectDefinition
import app.absentmotion.effects.EffectParam
import app.absentmotion.model.Layer
import app.absentmotion.model.MAX_PINS
import app.absentmotion.model.Pt
import app.absentmotion.model.Scene
import app.absentmotion.model.PuppetBend
import app.absentmotion.model.PuppetPin
import app.absentmotion.model.XmlNode
import app.absentmotion.model.effectProperty
import app.absentmotion.model.effects
import app.absentmotion.model.finiteFloat
import app.absentmotion.model.formatFloats
import app.absentmotion.model.puppetFalloff
import app.absentmotion.model.allPuppetPins
import app.absentmotion.model.puppetPins
import app.absentmotion.model.valueAt
import app.absentmotion.model.withEffect
import app.absentmotion.model.withEffectProperty
import app.absentmotion.model.withPuppetPinAt
import app.absentmotion.model.withoutPuppetPin
import app.absentmotion.ui.editor.ChromeIcons
import app.absentmotion.ui.editor.PanelIcons
import app.absentmotion.ui.theme.Am

internal const val TEXT_PATH_ID = "absent.textpath"
internal const val PUPPET_ID = "absent.puppet"

private fun XmlNode.point(param: EffectParam, t: Float): Pt =
    (effectProperty(param.id)?.valueAt(t) ?: param.missingDefault).split(',').map { it.trim().finiteFloat() ?: 0f }.let { Pt(it.getOrElse(0) { 0f }, it.getOrElse(1) { 0f }) }

private fun XmlNode.number(param: EffectParam, t: Float): Float = (effectProperty(param.id)?.valueAt(t) ?: param.missingDefault).finiteFloat() ?: 0f

internal fun arcGuide(a: Pt, b: Pt, c: Pt, d: Pt, steps: Int = 48): List<Pt> = List(steps + 1) { i ->
    val t = i.toFloat() / steps
    val u = 1 - t
    Pt(u * u * u * a.x + 3 * u * u * t * b.x + 3 * u * t * t * c.x + t * t * t * d.x, u * u * u * a.y + 3 * u * u * t * b.y + 3 * u * t * t * c.y + t * t * t * d.y)
}

internal fun circleGuide(c: Pt, r: Float, steps: Int = 72): List<Pt> = List(steps + 1) { i ->
    val angle = 2 * Math.PI * i / steps
    Pt(c.x + r * kotlin.math.cos(angle).toFloat(), c.y + r * kotlin.math.sin(angle).toFloat())
}

internal fun textPathPoints(state: EditorState, layer: Layer, index: Int, instance: XmlNode, definition: EffectDefinition, active: EffectParam?, select: (EffectParam) -> Unit): LayerPoints? {
    fun param(id: String) = definition.params.firstOrNull { it.id == id }
    fun setPoint(p: EffectParam, to: Pt) = state.editSelected("effect-$index-${p.id}") { current ->
        current.withEffect(index) { it.withEffectProperty(p.id, p.type, formatFloats(floatArrayOf(to.x.coerceIn(p.min, p.max), to.y.coerceIn(p.min, p.max))), current.t, current.snap) }
    }
    val arc = listOf("start", "control1", "control2", "end").mapNotNull(::param).filter { definition.shown(it, instance, layer.t) }
    if (arc.size == 4) {
        val (a, b, c, d) = arc.map { instance.point(it, layer.t) }
        return LayerPoints(listOf(a, b, c, d), arc.indexOf(active), lines = listOf(0 to 1, 3 to 2), guide = arcGuide(a, b, c, d), pick = { select(arc[it]) }) { i, to -> setPoint(arc[i], to) }
    }
    val center = param("center")?.takeIf { definition.shown(it, instance, layer.t) } ?: return null
    val radius = param("radius") ?: return null
    val c = instance.point(center, layer.t)
    val r = kotlin.math.abs(instance.number(radius, layer.t))
    val chosen = when (active) { center -> 0; radius -> 1; else -> -1 }
    return LayerPoints(listOf(c, Pt(c.x, c.y - r)), chosen, lines = listOf(0 to 1), guide = circleGuide(c, r), pick = { select(if (it == 0) center else radius) }) { i, to ->
        if (i == 0) setPoint(center, to)
        else state.editSelected("effect-$index-radius") { current ->
            val now = current.effects.getOrNull(index)?.point(center, current.t) ?: c
            val value = to.distanceTo(now).coerceIn(radius.min, radius.max)
            current.withEffect(index) { it.withEffectProperty(radius.id, radius.type, formatFloats(floatArrayOf(value)), current.t, current.snap) }
        }
    }
}

internal fun Scene.missesPathBelow(layer: Layer, instance: XmlNode): Boolean {
    if ((instance.effectProperty("shape")?.valueAt(layer.t)?.finiteFloat() ?: 0f).roundToInt() != 2) return false
    val list = layers
    val index = list.indexOfFirst { it.id == layer.id }
    if (index < 0) return false
    val pinned = layer.node["pathLayer"]?.toLongOrNull()?.takeIf { it != layer.id }?.let { id -> list.firstOrNull { it.id == id } }
    val below = pinned ?: list.getOrNull(index - 1)
    return below == null || below.kind in setOf("nullobj", "camera", "audio")
}

@Composable
internal fun TextPathHint(state: EditorState, layer: Layer, instance: XmlNode) {
    if (!state.scene.missesPathBelow(layer, instance)) return
    Text(stringResource(R.string.textpath_nothing_below), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Am.Caption, fontSize = 12.sp, lineHeight = 15.sp)
}

private fun EditorState.movePin(index: Int, pin: PuppetPin, to: Pt, key: Any?) = editSelected(key) { current ->
    current.withEffect(index) { it.withEffectProperty(pin.name, "vec2", formatFloats(floatArrayOf(to.x, to.y)), current.t, current.snap) }
}

internal fun puppetPoints(state: EditorState, layer: Layer, index: Int, instance: XmlNode, pin: String?, adding: Boolean, onPin: (String?) -> Unit,
                          onFull: () -> Unit): LayerPoints {
    val pins = instance.puppetPins(layer.t)
    val bend = PuppetBend(pins, instance.puppetFalloff(layer.t))
    val add: ((Pt) -> Unit)? = if (!adding) null else { at ->
        if (instance.allPuppetPins(layer.t).size >= MAX_PINS) onFull()
        else {
            var made: String? = null
            state.editSelected { current ->
                current.withEffect(index) { effect ->
                    effect.withPuppetPinAt(at, current.t)?.also { made = it.puppetPins(current.t).lastOrNull()?.name } ?: effect
                }
            }
            onPin(made)
        }
    }
    return LayerPoints(pins.map { it.at }, pins.indexOfFirst { it.name == pin }, pick = { onPin(pins[it].name) }, tap = add, source = bend::source) { i, to ->
        pins.getOrNull(i)?.let { state.movePin(index, it, to, "puppet-$index-${it.name}") }
    }
}

@Composable
internal fun PuppetSection(
    state: EditorState,
    layer: Layer,
    index: Int,
    instance: XmlNode,
    pin: String?,
    adding: Boolean,
    onPin: (String?) -> Unit,
    onAdding: (Boolean) -> Unit
) {
    val all = instance.allPuppetPins(layer.t)
    val pins = all.take(MAX_PINS)
    val activePin = pins.firstOrNull { it.name == pin } ?: pins.firstOrNull()
    var stepSize by remember { mutableIntStateOf(5) }
    val falloff = instance.puppetFalloff(layer.t)

    // Mode Toggle Bar (Move & Animate vs Add Pins)
    Row(
        Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val moveSelected = !adding
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(PanelShape)
                .background(if (moveSelected) Am.Accent else PanelDark)
                .clickable { onAdding(false) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelIcon(PanelIcons.MoveTransform, if (moveSelected) Color.White else Am.OnDark, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Move & Animate", color = if (moveSelected) Color.White else Am.OnDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier.weight(1f).fillMaxHeight().clip(PanelShape)
                .background(if (adding) Am.Accent else PanelDark)
                .clickable { onAdding(true) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelIcon(PanelIcons.Pin, if (adding) Color.White else Am.OnDark, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.puppet_add_pins), color = if (adding) Color.White else Am.OnDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (adding) {
        Text(
            stringResource(R.string.puppet_pins_hint),
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Am.Caption,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    } else if (activePin != null) {
        // Manual Move & Keyframing Card for Active Pin
        val pinPropRef = PropRef.effect(index, activePin.name)
        val onKeyframe = layer.onKeyframe(pinPropRef)
        val dx = activePin.at.x - activePin.rest.x
        val dy = activePin.at.y - activePin.rest.y

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E2130)).padding(8.dp)
        ) {
            // Pin Header: Badge, Keyframe button, Reset button
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Am.Accent))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.puppet_pin_name, "${activePin.number}"),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Δ: (${formatFixed(dx, 1)}, ${formatFixed(dy, 1)})",
                        color = Am.Caption,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Reset to Rest
                    Box(
                        Modifier.height(28.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF282B3E))
                            .clickable { state.movePin(index, activePin, activePin.rest, "puppet-reset-${activePin.name}") }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ChromeIcons.Undo, null, Modifier.size(14.dp), tint = Am.OnDark)
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", color = Am.OnDark, fontSize = 11.sp)
                        }
                    }

                    // Keyframe toggle
                    Box(
                        Modifier.height(28.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (onKeyframe) Am.Gold.copy(alpha = 0.25f) else Color(0xFF282B3E))
                            .clickable {
                                onPin(activePin.name)
                                state.toggleKeyframe()
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PanelIcon(
                                if (onKeyframe) PanelIcons.KeyframeRemove else PanelIcons.Keyframe,
                                if (onKeyframe) Am.Gold else Am.OnDark,
                                size = 14.dp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (onKeyframe) "Keyed" else "Keyframe", color = if (onKeyframe) Am.Gold else Am.OnDark, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // D-Pad Manual Nudge Controller + Step Selector
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Step selector (1px, 5px, 20px)
                Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step", color = Am.Caption, fontSize = 10.sp)
                    listOf(1, 5, 20).forEach { s ->
                        Box(
                            Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (stepSize == s) Am.Accent else Color(0xFF282B3E))
                                .clickable { stepSize = s },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${s}px", color = if (stepSize == s) Color.White else Am.OnDark, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // D-Pad 4-way direction pad
                Box(Modifier.weight(1f).height(80.dp), contentAlignment = Alignment.Center) {
                    // Up
                    Box(
                        Modifier.align(Alignment.TopCenter).size(38.dp, 26.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF282B3E))
                            .clickable {
                                val newPt = Pt(activePin.at.x, activePin.at.y - stepSize)
                                state.movePin(index, activePin, newPt, "puppet-nudge-${activePin.name}")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▲", color = Color.White, fontSize = 12.sp)
                    }

                    // Down
                    Box(
                        Modifier.align(Alignment.BottomCenter).size(38.dp, 26.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF282B3E))
                            .clickable {
                                val newPt = Pt(activePin.at.x, activePin.at.y + stepSize)
                                state.movePin(index, activePin, newPt, "puppet-nudge-${activePin.name}")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▼", color = Color.White, fontSize = 12.sp)
                    }

                    // Left
                    Box(
                        Modifier.align(Alignment.CenterStart).size(34.dp, 30.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF282B3E))
                            .clickable {
                                val newPt = Pt(activePin.at.x - stepSize, activePin.at.y)
                                state.movePin(index, activePin, newPt, "puppet-nudge-${activePin.name}")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("◀", color = Color.White, fontSize = 12.sp)
                    }

                    // Right
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(34.dp, 30.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF282B3E))
                            .clickable {
                                val newPt = Pt(activePin.at.x + stepSize, activePin.at.y)
                                state.movePin(index, activePin, newPt, "puppet-nudge-${activePin.name}")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", color = Color.White, fontSize = 12.sp)
                    }

                    // Center indicator
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Am.Accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Am.Accent))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mesh Influence (Falloff) Slider
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Deform Influence", Modifier.width(96.dp), color = Am.Caption, fontSize = 11.sp)
                Slider(
                    value = falloff,
                    onValueChange = { next ->
                        state.editSelected("puppet-falloff") { current ->
                            current.withEffect(index) { it.withEffectProperty("falloff", "float", formatFloats(floatArrayOf(next.coerceIn(0.1f, 4f))), current.t, current.snap) }
                        }
                    },
                    valueRange = 0.1f..4.0f,
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = Am.Accent, activeTrackColor = Am.Accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(formatFixed(falloff, 2), color = Color.White, fontSize = 11.sp, modifier = Modifier.width(32.dp))
            }
        }
    }

    // Animation Presets Row
    if (pins.size >= 2) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("Animation Presets", color = Am.Caption, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Wave", "Bend", "Breathe", "Swing").forEach { presetName ->
                    Box(
                        Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF282B3E)).clickable {
                            applyPuppetPreset(state, index, pins, presetName)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(presetName, color = Am.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (pins.isEmpty()) {
        Text(stringResource(R.string.puppet_pins_hint), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Am.Caption, fontSize = 12.sp, lineHeight = 15.sp)
    }
    if (all.size > MAX_PINS) {
        Text(stringResource(R.string.puppet_pins_full, "$MAX_PINS"), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Am.Caption, fontSize = 12.sp, lineHeight = 15.sp)
    }

    // Individual Pins List with selection and direct actions
    for (p in pins) {
        val chosen = p.name == (pin ?: activePin?.name)
        fun keypad(which: Int) {
            onPin(p.name)
            state.keypad = KeypadRequest(listOf(KeypadField("X", p.at.x), KeypadField("Y", p.at.y)), which) { v -> state.movePin(index, p, Pt(v[0], v[1]), null) }
        }
        val label = stringResource(R.string.puppet_pin_name, "${p.number}")
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 3.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            PropertyLabel(label, chosen, true, Modifier.width(75.dp).fillMaxHeight()) { onPin(if (chosen) null else p.name) }
            Spacer(Modifier.width(4.dp))
            ValueBox(formatFixed(p.at.x, 1), false, chosen, Modifier.weight(1f).fillMaxHeight(), 5.5f, true) { keypad(0) }
            Spacer(Modifier.width(3.dp))
            ValueBox(formatFixed(p.at.y, 1), false, chosen, Modifier.weight(1f).fillMaxHeight(), 5.5f, true) { keypad(1) }
            Box(Modifier.size(40.dp).clickable { state.movePin(index, p, p.rest, null) }, contentAlignment = Alignment.Center) {
                Icon(ChromeIcons.Undo, "$label, ${stringResource(R.string.puppet_reset_pin)}", Modifier.size(18.dp), tint = Am.OnDark)
            }
            Box(Modifier.size(40.dp).clickable {
                if (chosen) onPin(null)
                state.editSelected { current -> current.withEffect(index) { it.withoutPuppetPin(p.number) } }
            }, contentAlignment = Alignment.Center) {
                Icon(ChromeIcons.Trash, "$label, ${stringResource(R.string.puppet_delete_pin)}", Modifier.size(18.dp), tint = Am.OnDark)
            }
        }
    }
}

@Composable
internal fun rememberPinsFull(): () -> Unit {
    val context = LocalContext.current
    val message = stringResource(R.string.puppet_pins_full, "$MAX_PINS")
    return { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show() }
}

private fun applyPuppetPreset(state: EditorState, index: Int, pins: List<PuppetPin>, preset: String) {
    state.editSelected("puppet-preset-$preset") { current ->
        var updated = current
        val steps = 12
        for (step in 0..steps) {
            val t = step.toFloat() / steps.toFloat()
            val phase = t * 2.0 * Math.PI
            for (i in 1 until pins.size) {
                val p = pins[i]
                val (offsetX, offsetY) = when (preset) {
                    "Wave" -> kotlin.math.sin(phase * 2.0).toFloat() * 25f * i to kotlin.math.cos(phase * 2.0).toFloat() * 5f * i
                    "Bend" -> kotlin.math.sin(phase).toFloat() * 30f * (i * 0.5f) to (1f - kotlin.math.cos(phase).toFloat()) * 10f * (i * 0.5f)
                    "Breathe" -> kotlin.math.sin(phase).toFloat() * 12f * (i * 0.4f) to kotlin.math.sin(phase).toFloat() * 16f * (i * 0.4f)
                    "Swing" -> kotlin.math.sin(phase * 1.5).toFloat() * 20f * i to kotlin.math.abs(kotlin.math.cos(phase * 1.5)).toFloat() * 8f * i
                    else -> kotlin.math.sin(phase).toFloat() * 20f * (i * 0.5f) to 0f
                }
                val newPt = Pt(p.rest.x + offsetX, p.rest.y + offsetY)
                updated = updated.withEffect(index) {
                    it.withEffectProperty(p.name, "vec2", formatFloats(floatArrayOf(newPt.x, newPt.y)), t, current.snap)
                }
            }
        }
        updated
    }
}
