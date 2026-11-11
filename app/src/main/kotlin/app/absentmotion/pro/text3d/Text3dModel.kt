package app.absentmotion.pro.text3d

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class MaterialType(val displayName: String, val specularStrength: Float, val roughness: Float) {
    Matte("Matte", 0.1f, 0.9f),
    Metallic("Metallic", 0.9f, 0.2f),
    Plastic("Plastic Gloss", 0.5f, 0.4f),
    Glass("Glass Shimmer", 0.8f, 0.1f),
    Emissive("Neon Emissive", 1.0f, 0.0f)
}

enum class CharacterSelectorUnit {
    Character,
    Word,
    Line
}

enum class AnimationOrder {
    Forward,
    Backward,
    CenterOut,
    Random
}

data class Text3dConfig(
    val extrusionDepth: Float = 25f,
    val bevelSize: Float = 3f,
    val bevelSegments: Int = 3,
    val material: MaterialType = MaterialType.Metallic,
    val ambientLight: Float = 0.35f,
    val directionalLightIntensity: Float = 0.75f,
    val lightAngleDeg: Float = 45f,
    val lightElevationDeg: Float = 60f,
    val shadowSoftness: Float = 8f,
    val shadowOpacity: Float = 0.6f,
    val perspectiveFov: Float = 45f,
    val frontColor: Color = Color(0xFFFFFFFF),
    val sideColor: Color = Color(0xFF7A869A),
    val bevelColor: Color = Color(0xFFB0C4DE),
    val glowRadius: Float = 12f,
    val glowColor: Color = Color(0x6600FF9D)
)

data class CharacterTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val translateZ: Float = 0f,
    val rotateXDeg: Float = 0f,
    val rotateYDeg: Float = 0f,
    val rotateZDeg: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f,
    val opacity: Float = 1f,
    val blur: Float = 0f
)

data class CharacterAnimatorConfig(
    val unit: CharacterSelectorUnit = CharacterSelectorUnit.Character,
    val order: AnimationOrder = AnimationOrder.Forward,
    val staggerMs: Long = 60L,
    val durationPerUnitMs: Long = 400L,
    val startOffsetPercent: Float = 0f,
    val endOffsetPercent: Float = 1f,
    val initialTransform: CharacterTransform = CharacterTransform(translateY = 40f, opacity = 0f),
    val targetTransform: CharacterTransform = CharacterTransform(),
    val presetName: String = "None"
) {
    companion object {
        val Presets = listOf(
            CharacterAnimatorConfig(
                presetName = "Typewriter",
                unit = CharacterSelectorUnit.Character,
                staggerMs = 50L,
                durationPerUnitMs = 80L,
                initialTransform = CharacterTransform(opacity = 0f, scaleX = 0.8f, scaleY = 0.8f)
            ),
            CharacterAnimatorConfig(
                presetName = "3D Flip",
                unit = CharacterSelectorUnit.Character,
                staggerMs = 70L,
                durationPerUnitMs = 500L,
                initialTransform = CharacterTransform(rotateXDeg = 90f, translateZ = -60f, opacity = 0f)
            ),
            CharacterAnimatorConfig(
                presetName = "Wave",
                unit = CharacterSelectorUnit.Character,
                staggerMs = 40L,
                durationPerUnitMs = 450L,
                initialTransform = CharacterTransform(translateY = -50f, rotateZDeg = -15f, opacity = 0.5f)
            ),
            CharacterAnimatorConfig(
                presetName = "Bounce Drop",
                unit = CharacterSelectorUnit.Word,
                staggerMs = 120L,
                durationPerUnitMs = 600L,
                initialTransform = CharacterTransform(translateY = -120f, scaleY = 1.3f, opacity = 0f)
            ),
            CharacterAnimatorConfig(
                presetName = "Explosion",
                unit = CharacterSelectorUnit.Character,
                staggerMs = 25L,
                durationPerUnitMs = 550L,
                initialTransform = CharacterTransform(scaleX = 2.5f, scaleY = 2.5f, translateZ = 100f, opacity = 0f, rotateZDeg = 45f)
            ),
            CharacterAnimatorConfig(
                presetName = "Letter-by-Letter Reveal",
                unit = CharacterSelectorUnit.Character,
                staggerMs = 60L,
                durationPerUnitMs = 400L,
                initialTransform = CharacterTransform(scaleX = 0f, scaleY = 0f, rotateYDeg = 180f, opacity = 0f)
            )
        )
    }
}
