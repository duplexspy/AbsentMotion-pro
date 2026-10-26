package app.absentmotion.pro

import app.absentmotion.effectspack.PackManifest
import app.absentmotion.effectspack.PackParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class Ultimate520EffectsPackTest {
    private val packFile: File get() = listOf(
        File("src/main/assets/packs/absent_motion_pro_520_ultimate.effectspack"),
        File("app/src/main/assets/packs/absent_motion_pro_520_ultimate.effectspack")
    ).firstOrNull { it.isFile } ?: File("src/main/assets/packs/absent_motion_pro_520_ultimate.effectspack")

    @Test
    fun testPackArchiveIntegrity() {
        assertTrue(packFile.isFile)
        assertTrue(packFile.length() > 100_000L)

        ZipFile(packFile).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
            assertNotNull(manifestEntry)

            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifest = PackManifest.parse(manifestText)

            assertEquals("amp.pro.ultimate520", manifest.id)
            assertEquals("Absent Motion Pro — Ultimate 520 FX Suite", manifest.name)
            assertEquals("Gold Motion", manifest.author)
            assertEquals(520, manifest.effects.size)
            assertTrue(manifest.description.contains("Gold Motion"))

            val groups = manifest.effects.map { it.group }.distinct().toSet()
            assertEquals(10, groups.size)

            val expectedCategories = setOf(
                "3D, Depth, Parallax & Hologram",
                "Blur, Focus & Camera Lens",
                "Color & Cinematic Grading",
                "Distort, Warp & Liquid",
                "Glitch, Datamosh & Digital",
                "Light, Glow & Optical Flares",
                "Procedural, Energy & Atmos",
                "Retro, Analog, VHS & CRT",
                "Stylize, Art, Toon & Print",
                "Transitions, Motion & Split Screen",
            )
            assertEquals(expectedCategories, groups)

            val effectsWithTags = manifest.effects.count { it.tags.isNotEmpty() }
            assertEquals(520, effectsWithTags)

            val sampleFiles = listOf(
                "effects/aces_filmic_tonemap.xml",
                "effects/reinhard_extended_luma.xml",
                "effects/agx_punchy_cinema.xml"
            )
            for (rel in sampleFiles) {
                val entry = zip.getEntry(rel)
                assertNotNull(entry)
                val xmlText = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val parsed = PackParser.parse(xmlText, defaultGroup = "Color & Cinematic Grading")
                assertTrue(parsed.id.startsWith("amp.pro."))
                assertTrue(parsed.name.isNotBlank())
                assertTrue(parsed.params.isNotEmpty())
            }
        }
    }

    @Test
    fun testEffectDefinitionAuthorAndTagsPreservation() {
        val json = JSONObject().apply {
            put("id", "amp.pro.test_effect")
            put("name", "Test Effect")
            put("desc", "A test shader")
            put("category", "custom")
            put("tags", "test,grading,pro")
        }
        val xml = """<?xml version='1.0' encoding='UTF-8' ?>
<effect id="amp.pro.test_effect" name="Test Effect" desc="A test shader" category="custom" tags="test,grading,pro">
<params>
<texture id="inputImg" srcType="content" />
<spinner id="intensity" default="1.0" min="0.0" max="2.0" step="0.1" label="Intensity" />
</params>
<shader>
vec4 process(vec4 col, vec2 uv) {
    return col * intensity;
}
</shader>
</effect>"""
        val effect = PackParser.parse(xml, "Color & Cinematic Grading")
        val def = effect.toEffectDefinition(packName = "520 Pack", author = "Gold Motion")

        assertEquals("520 Pack", def.packName)
        assertEquals("Gold Motion", def.author)
        assertEquals("Color & Cinematic Grading", def.group)
        assertTrue(def.tags.contains("grading"))
        assertTrue(def.tags.contains("test"))
    }
}
