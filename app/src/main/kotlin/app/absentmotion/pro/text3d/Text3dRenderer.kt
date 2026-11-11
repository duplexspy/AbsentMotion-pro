package app.absentmotion.pro.text3d

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import app.absentmotion.model.Layer
import app.absentmotion.model.XmlNode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Text3dRenderer {

    data class GlyphSlice(
        val charStr: String,
        val x: Float,
        val y: Float,
        val transform: CharacterTransform,
        val depth: Float
    )

    fun calculateUnitProgress(
        index: Int,
        totalCount: Int,
        elapsedMs: Long,
        animator: CharacterAnimatorConfig
    ): Float {
        if (animator.presetName == "None" || totalCount <= 0) return 1f

        val orderIndex = when (animator.order) {
            AnimationOrder.Forward -> index
            AnimationOrder.Backward -> totalCount - 1 - index
            AnimationOrder.CenterOut -> {
                val mid = (totalCount - 1) / 2f
                val dist = kotlin.math.abs(index - mid)
                (dist * 2).toInt()
            }
            AnimationOrder.Random -> ((index * 37) % totalCount)
        }

        val startTime = orderIndex * animator.staggerMs
        val duration = max(1L, animator.durationPerUnitMs)
        val rawProgress = (elapsedMs - startTime).toFloat() / duration.toFloat()
        val clamped = rawProgress.coerceIn(0f, 1f)

        // Ease in-out cubic
        return if (clamped < 0.5f) {
            4f * clamped * clamped * clamped
        } else {
            1f - (-2f * clamped + 2f).let { it * it * it } / 2f
        }
    }

    fun lerpTransform(from: CharacterTransform, to: CharacterTransform, t: Float): CharacterTransform {
        return CharacterTransform(
            translateX = from.translateX + (to.translateX - from.translateX) * t,
            translateY = from.translateY + (to.translateY - from.translateY) * t,
            translateZ = from.translateZ + (to.translateZ - from.translateZ) * t,
            rotateXDeg = from.rotateXDeg + (to.rotateXDeg - from.rotateXDeg) * t,
            rotateYDeg = from.rotateYDeg + (to.rotateYDeg - from.rotateYDeg) * t,
            rotateZDeg = from.rotateZDeg + (to.rotateZDeg - from.rotateZDeg) * t,
            scaleX = from.scaleX + (to.scaleX - from.scaleX) * t,
            scaleY = from.scaleY + (to.scaleY - from.scaleY) * t,
            scaleZ = from.scaleZ + (to.scaleZ - from.scaleZ) * t,
            opacity = from.opacity + (to.opacity - from.opacity) * t,
            blur = from.blur + (to.blur - from.blur) * t
        )
    }

    data class RenderUnit(
        val str: String,
        val width: Float,
        val cx: Float,
        val cy: Float,
        val drawX: Float,
        val drawY: Float,
        val tf: CharacterTransform,
        val fovScale: Float,
        val alphaInt: Int
    )

    fun render(
        canvas: Canvas,
        text: String,
        typeface: Typeface,
        textSize: Float,
        config: Text3dConfig,
        animator: CharacterAnimatorConfig,
        elapsedMs: Long,
        originX: Float,
        originY: Float
    ) {
        if (text.isEmpty()) return

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
        }

        val units = when (animator.unit) {
            CharacterSelectorUnit.Character -> text.map { it.toString() }
            CharacterSelectorUnit.Word -> text.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
            CharacterSelectorUnit.Line -> text.split("\n")
        }

        // Light direction vector in 3D
        val lightRad = config.lightAngleDeg * (PI.toFloat() / 180f)
        val elevRad = config.lightElevationDeg * (PI.toFloat() / 180f)
        val lx = cos(lightRad) * cos(elevRad)
        val ly = sin(lightRad) * cos(elevRad)

        // Shading intensity
        val sideShadeFactor = (config.ambientLight + config.directionalLightIntensity * max(0f, lx * 0.7f + ly * 0.7f)).coerceIn(0.1f, 1.2f)
        val sideColorArgb = config.sideColor.toArgb()
        val rSide = ((sideColorArgb shr 16 and 0xFF) * sideShadeFactor).toInt().coerceIn(0, 255)
        val gSide = ((sideColorArgb shr 8 and 0xFF) * sideShadeFactor).toInt().coerceIn(0, 255)
        val bSide = ((sideColorArgb and 0xFF) * sideShadeFactor).toInt().coerceIn(0, 255)

        val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
            color = config.frontColor.toArgb()
        }

        val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
        }

        val glowPaint = if (config.glowRadius > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = textSize
                color = config.glowColor.toArgb()
                maskFilter = BlurMaskFilter(config.glowRadius, BlurMaskFilter.Blur.OUTER)
            }
        } else null

        val shadowPaint = if (config.shadowOpacity > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = textSize
                color = ((((config.shadowOpacity * 255).toInt().coerceIn(0, 255)) shl 24) or 0x000000)
                maskFilter = BlurMaskFilter(max(1f, config.shadowSoftness), BlurMaskFilter.Blur.NORMAL)
            }
        } else null

        val slices = config.extrusionDepth.toInt().coerceIn(0, 60)

        // Compute transforms and layout for all units first
        val unitStates = mutableListOf<RenderUnit>()
        var currentX = originX
        val currentY = originY

        for (i in units.indices) {
            val u = units[i]
            val width = textPaint.measureText(u)
            val prog = calculateUnitProgress(i, units.size, elapsedMs, animator)
            val tf = lerpTransform(animator.initialTransform, animator.targetTransform, prog)
            val cx = currentX + width / 2f
            val cy = currentY - textSize / 3f
            val fovScale = 1000f / (1000f - tf.translateZ.coerceIn(-800f, 800f))
            val drawX = -width / 2f
            val drawY = textSize / 3f
            val alphaInt = ((tf.opacity.coerceIn(0f, 1f)) * 255).toInt()
            unitStates.add(RenderUnit(u, width, cx, cy, drawX, drawY, tf, fovScale, alphaInt))
            currentX += width
        }

        // Pass 1: Drop shadows for all units
        if (shadowPaint != null) {
            for (u in unitStates) {
                if (u.alphaInt <= 12) continue
                canvas.save()
                canvas.translate(u.cx + u.tf.translateX, u.cy + u.tf.translateY)
                canvas.scale(u.tf.scaleX * u.fovScale, u.tf.scaleY * u.fovScale)
                canvas.rotate(u.tf.rotateZDeg)
                canvas.drawText(u.str, u.drawX + config.extrusionDepth * 0.4f + 6f, u.drawY + config.extrusionDepth * 0.4f + 6f, shadowPaint)
                canvas.restore()
            }
        }

        // Pass 2: Glow for all units
        if (glowPaint != null) {
            for (u in unitStates) {
                if (u.alphaInt <= 12) continue
                glowPaint.alpha = (u.alphaInt * 0.8f).toInt()
                canvas.save()
                canvas.translate(u.cx + u.tf.translateX, u.cy + u.tf.translateY)
                canvas.scale(u.tf.scaleX * u.fovScale, u.tf.scaleY * u.fovScale)
                canvas.rotate(u.tf.rotateZDeg)
                canvas.drawText(u.str, u.drawX, u.drawY, glowPaint)
                canvas.restore()
            }
        }

        // Pass 3: Extrusion slices from back to front (across all units)
        // Drawing all slices before any front faces ensures front faces are never occluded!
        if (slices > 0) {
            val stepX = cos(lightRad + PI.toFloat()) * (config.extrusionDepth / slices)
            val stepY = sin(lightRad + PI.toFloat()) * (config.extrusionDepth / slices)
            for (s in slices downTo 1) {
                val depthDarken = 0.65f + 0.35f * (1f - (s.toFloat() / slices.toFloat()))
                val rS = (rSide * depthDarken).toInt().coerceIn(0, 255)
                val gS = (gSide * depthDarken).toInt().coerceIn(0, 255)
                val bS = (bSide * depthDarken).toInt().coerceIn(0, 255)
                val sliceColor = (rS shl 16) or (gS shl 8) or bS
                val sx = stepX * s
                val sy = stepY * s

                for (u in unitStates) {
                    if (u.alphaInt <= 12) continue
                    sidePaint.color = (u.alphaInt shl 24) or sliceColor
                    canvas.save()
                    canvas.translate(u.cx + u.tf.translateX, u.cy + u.tf.translateY)
                    canvas.scale(u.tf.scaleX * u.fovScale, u.tf.scaleY * u.fovScale)
                    canvas.rotate(u.tf.rotateZDeg)
                    canvas.drawText(u.str, u.drawX + sx, u.drawY + sy, sidePaint)
                    canvas.restore()
                }
            }
        }

        // Pass 4: Crisp Front Faces & Bevel Highlights on top of all extrusion
        for (u in unitStates) {
            if (u.alphaInt <= 12) continue
            canvas.save()
            canvas.translate(u.cx + u.tf.translateX, u.cy + u.tf.translateY)
            canvas.scale(u.tf.scaleX * u.fovScale, u.tf.scaleY * u.fovScale)
            canvas.rotate(u.tf.rotateZDeg)

            // Bevel edges highlight
            if (config.bevelSize > 0f) {
                val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    this.textSize = textSize
                    color = config.bevelColor.toArgb()
                    alpha = (u.alphaInt * 0.9f).toInt()
                    strokeWidth = config.bevelSize
                    style = Paint.Style.STROKE
                }
                canvas.drawText(u.str, u.drawX, u.drawY, bevelPaint)
            }

            // Front face fill with optional material shader
            frontPaint.alpha = u.alphaInt
            if (config.material == MaterialType.Metallic) {
                frontPaint.shader = LinearGradient(
                    u.drawX, u.drawY - textSize, u.drawX + u.width, u.drawY,
                    intArrayOf(config.frontColor.toArgb(), 0xFFFFFFFF.toInt(), config.frontColor.toArgb()),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else {
                frontPaint.shader = null
            }
            canvas.drawText(u.str, u.drawX, u.drawY, frontPaint)
            canvas.restore()
        }
    }

    // Serialization & Parsing for Scene XML preservation
    fun serialize(config: Text3dConfig, animator: CharacterAnimatorConfig): Map<String, String> {
        return mapOf(
            "pro.text3d.depth" to config.extrusionDepth.toString(),
            "pro.text3d.bevel" to config.bevelSize.toString(),
            "pro.text3d.material" to config.material.name,
            "pro.text3d.fov" to config.perspectiveFov.toString(),
            "pro.text3d.lightAngle" to config.lightAngleDeg.toString(),
            "pro.text3d.lightIntensity" to config.directionalLightIntensity.toString(),
            "pro.text3d.glow" to config.glowRadius.toString(),
            "pro.text3d.preset" to animator.presetName,
            "pro.text3d.unit" to animator.unit.name,
            "pro.text3d.stagger" to animator.staggerMs.toString(),
            "pro.text3d.duration" to animator.durationPerUnitMs.toString()
        )
    }

    private data class AnimPresetValues(val pos: String, val rot: String, val scale: String, val alpha: String)

    fun applyToLayer(layer: Layer, config: Text3dConfig, animator: CharacterAnimatorConfig): Layer {
        val serialized = serialize(config, animator)
        var n = layer.node
        serialized.forEach { (k, v) -> n = n.with(k, v) }

        // Remove previous 3D effect nodes to re-sync
        var newChildren = n.children.filterNot {
            it.name == "effect" && (it["id"] == "absent.rasterextrude" || it["id"] == "rasterextrude" || it["from"] == "text3d")
        }

        // 1. Attach 3D Extrusion if extrusionDepth > 0
        if (config.extrusionDepth > 0f) {
            val depthVal = (config.extrusionDepth / 100f).coerceIn(0.01f, 1.0f)
            val sideHex = String.format("#%08x", config.sideColor.toArgb())
            val extrudeEffect = XmlNode("effect", mapOf("id" to "absent.rasterextrude", "hidden" to "false", "from" to "text3d"), listOf(
                XmlNode("property", mapOf("name" to "depth", "type" to "float", "value" to "$depthVal")),
                XmlNode("property", mapOf("name" to "rotX", "type" to "angle", "value" to "-15.0")),
                XmlNode("property", mapOf("name" to "rotY", "type" to "angle", "value" to "-25.0")),
                XmlNode("property", mapOf("name" to "rotZ", "type" to "angle", "value" to "0.0")),
                XmlNode("property", mapOf("name" to "interior", "type" to "color", "value" to sideHex)),
                XmlNode("property", mapOf("name" to "light", "type" to "angle", "value" to "${config.lightAngleDeg}")),
                XmlNode("property", mapOf("name" to "shading", "type" to "float", "value" to "${config.directionalLightIntensity.coerceIn(0.1f, 1f)}")),
                XmlNode("property", mapOf("name" to "shine", "type" to "float", "value" to "${config.material.specularStrength}")),
                XmlNode("property", mapOf("name" to "perspective", "type" to "float", "value" to "${(config.perspectiveFov / 90f).coerceIn(0.1f, 1f)}"))
            ))
            newChildren = newChildren + extrudeEffect
        }

        // 2. Attach Character Animator (texttransform) if preset is active
        if (animator.presetName != "None") {
            val basedOnVal = when (animator.unit) {
                CharacterSelectorUnit.Character -> "0"
                CharacterSelectorUnit.Word -> "1"
                CharacterSelectorUnit.Line -> "2"
            }
            val presetVals = when (animator.presetName) {
                "Typewriter" -> AnimPresetValues("0,0", "0", "1", "0")
                "3D Flip" -> AnimPresetValues("0,0", "90", "1", "0")
                "Wave" -> AnimPresetValues("0,-40", "-15", "1", "0.5")
                "Bounce Drop" -> AnimPresetValues("0,-80", "0", "1.2", "0")
                else -> AnimPresetValues("0,40", "0", "1", "0")
            }
            val animEffect = XmlNode("effect", mapOf("id" to "absent.texttransform", "hidden" to "false", "from" to "text3d"), listOf(
                XmlNode("property", mapOf("name" to "basedon", "type" to "choice", "value" to basedOnVal)),
                XmlNode("property", mapOf("name" to "position", "type" to "point", "value" to presetVals.pos)),
                XmlNode("property", mapOf("name" to "rotation", "type" to "angle", "value" to presetVals.rot)),
                XmlNode("property", mapOf("name" to "scale", "type" to "float", "value" to presetVals.scale)),
                XmlNode("property", mapOf("name" to "opacity", "type" to "float", "value" to presetVals.alpha)),
                XmlNode("property", mapOf("name" to "order", "type" to "choice", "value" to if (animator.order == AnimationOrder.Random) "1" else "0"))
            ))
            newChildren = newChildren + animEffect
        }

        return layer.copy(node = n.copy(children = newChildren))
    }

    fun parseConfig(props: Map<String, String>): Pair<Text3dConfig, CharacterAnimatorConfig> {
        val depth = props["pro.text3d.depth"]?.toFloatOrNull() ?: 25f
        val bevel = props["pro.text3d.bevel"]?.toFloatOrNull() ?: 3f
        val mat = runCatching { MaterialType.valueOf(props["pro.text3d.material"] ?: "") }.getOrDefault(MaterialType.Metallic)
        val fov = props["pro.text3d.fov"]?.toFloatOrNull() ?: 45f
        val lightAngle = props["pro.text3d.lightAngle"]?.toFloatOrNull() ?: 45f
        val lightIntensity = props["pro.text3d.lightIntensity"]?.toFloatOrNull() ?: 0.75f
        val glow = props["pro.text3d.glow"]?.toFloatOrNull() ?: 12f

        val config = Text3dConfig(
            extrusionDepth = depth,
            bevelSize = bevel,
            material = mat,
            perspectiveFov = fov,
            lightAngleDeg = lightAngle,
            directionalLightIntensity = lightIntensity,
            glowRadius = glow
        )

        val presetName = props["pro.text3d.preset"] ?: "None"
        val basePreset = CharacterAnimatorConfig.Presets.firstOrNull { it.presetName == presetName }
            ?: CharacterAnimatorConfig(presetName = presetName)

        val unit = runCatching { CharacterSelectorUnit.valueOf(props["pro.text3d.unit"] ?: "") }.getOrDefault(basePreset.unit)
        val stagger = props["pro.text3d.stagger"]?.toLongOrNull() ?: basePreset.staggerMs
        val duration = props["pro.text3d.duration"]?.toLongOrNull() ?: basePreset.durationPerUnitMs

        val animator = basePreset.copy(unit = unit, staggerMs = stagger, durationPerUnitMs = duration)
        return Pair(config, animator)
    }
}
