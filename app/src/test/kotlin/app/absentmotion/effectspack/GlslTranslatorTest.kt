package app.absentmotion.effectspack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlslTranslatorTest {

    @Test
    fun translationIncludesAutomaticUniformsAndHelper() {
        val glsl = """
            void main() {
                vec2 uv = acScreenNorm;
                vec4 c = texture2DCv(inputImg.texture, uv);
                gl_FragColor = c;
            }
        """.trimIndent()
        val sksl = GlslTranslator.translate(glsl, emptyList())

        assertTrue(sksl.sksl.contains("uniform shader image;"))
        assertTrue(sksl.sksl.contains("uniform float2 size;"))
        assertTrue(sksl.sksl.contains("uniform float time;"))
        assertTrue(sksl.sksl.contains("half4 main(float2 coord)"))
        assertTrue(sksl.sksl.contains("half4 gmSample(float2 uv)"))
        assertTrue(sksl.sksl.contains("float2(clamped.x, 1.0 - clamped.y) * size"))
        assertTrue(sksl.sksl.contains("float2(coord.x / size.x, 1.0 - coord.y / size.y)"))
    }

    @Test
    fun renamesCollidingUniforms() {
        val params = listOf(
            PackEffectParam("color", "color", "Color", "color", "#ffff0000"),
            PackEffectParam("size", "size", "Size", "float", "0.5")
        )
        val glsl = """
            void main() {
                vec4 col = color;
                float s = size;
                gl_FragColor = col * s;
            }
        """.trimIndent()
        val sksl = GlslTranslator.translate(glsl, params)

        assertTrue(sksl.sksl.contains("layout(color) uniform float4 color_p;"))
        assertTrue(sksl.sksl.contains("uniform float size_p;"))
        assertTrue(sksl.sksl.contains("vec4 col = color_p;"))
        assertTrue(sksl.sksl.contains("float s = size_p;"))
        assertFalse(sksl.sksl.contains("uniform float4 color;"))
    }

    @Test
    fun handlesSwitchBooleans() {
        val params = listOf(
            PackEffectParam("invert", "invert", "Invert", "switch", "false")
        )
        val glsl = """
            void main() {
                if (invert) {
                    gl_FragColor = vec4(1.0);
                } else {
                    gl_FragColor = vec4(0.0);
                }
            }
        """.trimIndent()
        val sksl = GlslTranslator.translate(glsl, params)

        assertTrue(sksl.sksl.contains("uniform int invert_u;"))
        assertTrue(sksl.sksl.contains("bool invert = (invert_u == 1);"))
    }

    @Test
    fun mapsGlslConstructsToSksl() {
        val glsl = """
            void main() {
                vec4 col = texture2DCv(inputImg.texture, acScreenNorm);
                gl_FragColor = col;
            }
        """.trimIndent()
        val sksl = GlslTranslator.translate(glsl, emptyList())

        assertTrue(sksl.sksl.contains("gmSample(acScreenNorm)"))
        assertFalse(sksl.sksl.contains("texture2DCv"))
        assertFalse(sksl.sksl.contains("gl_FragColor ="))
        assertTrue(sksl.sksl.contains("return half4("))
    }
}
