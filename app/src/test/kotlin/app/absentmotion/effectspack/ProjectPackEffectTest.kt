package app.absentmotion.effectspack

import app.absentmotion.effects.EffectCatalog
import app.absentmotion.model.Scene
import app.absentmotion.model.XmlNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPackEffectTest {

    @Test
    fun packEffectCreatesValidXmlInstance() {
        val effect = PackEffect(
            id = "gm.custom.teal_orange",
            name = "Teal and Orange",
            description = "Grade",
            group = "Color and Grade",
            tags = listOf("color", "grade"),
            params = listOf(
                PackEffectParam("intensity", "Intensity", "float", "0.75", 0f, 1f, 0.01f),
                PackEffectParam("tint", "Tint", "color", "#ff008080")
            ),
            glsl = "void main() { gl_FragColor = vec4(1.0); }",
            sksl = "half4 main(float2 coord) { return half4(1.0); }",
            rawXml = "<effect id=\"gm.custom.teal_orange\"/>"
        )

        val node = effect.instance()
        assertEquals("effect", node.name)
        assertEquals("gm.custom.teal_orange", node["id"])
        assertEquals("false", node["hidden"])
        assertEquals(2, node.children.size)

        val param0 = node.children[0]
        assertEquals("property", param0.name)
        assertEquals("intensity", param0["name"])
        assertEquals("float", param0["type"])
        assertEquals("0.75", param0["value"])

        val param1 = node.children[1]
        assertEquals("property", param1.name)
        assertEquals("tint", param1["name"])
        assertEquals("color", param1["type"])
        assertEquals("#ff008080", param1["value"])
    }

    @Test
    fun sceneRoundTripsUnknownPackEffectWithoutError() {
        val xml = """
            <scene width="1080" height="1920" fps="30" duration="5000">
                <layer id="1" name="Video" type="shape" startTime="0" endTime="5000">
                    <effect id="gm.custom.uninstalled_pack_effect" hidden="false">
                        <property name="intensity" type="float" value="0.8"/>
                        <property name="tint" type="color" value="#ff112233"/>
                    </effect>
                </layer>
            </scene>
        """.trimIndent()

        val parsed = Scene.fromXml(xml)
        assertNotNull(parsed)

        val layer = parsed.layers.firstOrNull()
        assertNotNull(layer)

        val effects = layer!!.children.filter { it.name == "effect" }
        assertEquals(1, effects.size)

        val effectNode = effects.first()
        assertEquals("gm.custom.uninstalled_pack_effect", effectNode["id"])
        assertEquals(2, effectNode.children.size)

        val outXml = parsed.toXml()
        assertTrue(outXml.contains("gm.custom.uninstalled_pack_effect"))
        assertTrue(outXml.contains("intensity"))
        assertTrue(outXml.contains("0.8"))
    }

    @Test
    fun catalogReturnsNullForMissingPackWithoutException() {
        val emptyCatalog = EffectCatalog()
        assertNull(emptyCatalog.find("gm.custom.missing_effect"))
    }
}
