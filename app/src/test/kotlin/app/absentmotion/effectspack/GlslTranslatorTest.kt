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

        assertTrue(sksl.contains("uniform shader image;"))
        assertTrue(sksl.contains("uniform float2 size;"))
        assertTrue(sksl.contains("uniform float time;"))
        assertTrue(sksl.contains("half4 main(float2 coord)"))
        assertTrue(sksl.contains("half4 gmSample(float2 uv)"))
        assertTrue(sksl.contains("float2(clamped.x, 1.0 - clamped.y) * size"))
        assertTrue(sksl.contains("float2(coord.x / size.x, 1.0 - coord.y / size.y)"))
    }

    @Test
    fun renamesCollidingUniforms() {
        val params = listOf(
            PackEffectParam("color", "Color", "color", "#ffff0000"),
            PackEffectParam("size", "Size", "float", "0.5")
        )
        val glsl = """
            void main() {
                vec4 col = color;
                float s = size;
                gl_FragColor = col * s;
            }
        """.trimIndent()
        val sksl = GlslTranslator.translate(glsl, params)

        assertTrue(sksl.contains("layout(color) uniform float4 color_p;"))
        assertTrue(sksl.contains("uniform float size_p;"))
        assertTrue(sksl.contains("vec4 col = color_p;"))
        assertTrue(sksl.contains("float s = size_p;"))
        assertFalse(sksl.contains("uniform float4 color;"))
    }

    @Test
    fun handlesSwitchBooleans() {
        val params = listOf(
            PackEffectParam("invert", "Invert", "switch", "false")
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

        assertTrue(sksl.contains("uniform int invert_u;"))
        assertTrue(sksl.contains("bool invert = (invert_u == 1);"))
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

        assertTrue(sksl.contains("gmSample(acScreenNorm)"))
        assertFalse(sksl.contains("texture2DCv"))
        assertFalse(sksl.contains("gl_FragColor ="))
        assertTrue(sksl.contains("return half4("))
    }
}
