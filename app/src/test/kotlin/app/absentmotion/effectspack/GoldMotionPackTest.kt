package app.absentmotion.effectspack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class GoldMotionPackTest {

    @Test
    fun parseAll77EffectsFromDirectory() {
        val dir = File("goldmotion/GoldMotion-CustomFX-Pack/effects")
        if (!dir.isDirectory) return

        val xmlFiles = dir.listFiles { f -> f.isFile && f.extension == "xml" } ?: emptyArray()
        assertEquals(77, xmlFiles.size)

        val proIds = setOf(
            "gm.custom.cinematic_punch",
            "gm.custom.radial_rush",
            "gm.custom.liquid_rupture",
            "gm.custom.data_collapse",
            "gm.custom.solar_flare",
            "gm.custom.poster_impact",
            "gm.custom.energy_storm",
            "gm.custom.tape_trauma",
            "gm.custom.holo_depth"
        )

        val parsedIds = HashSet<String>()
        for (f in xmlFiles) {
            val text = f.readText()
            val effect = PackParser.parse(text)

            assertTrue(effect.id.startsWith("gm.custom."))
            assertTrue(effect.name.isNotBlank())
            assertTrue(effect.sksl.isNotBlank())
            assertTrue(effect.sksl.contains("half4 main(float2 coord)"))
            assertTrue(effect.sksl.contains("uniform shader image;"))

            val runtimeXml = effect.runtimeXml()
            assertTrue(runtimeXml.startsWith("<effect"))
            assertTrue(runtimeXml.contains("""id="${effect.id}""""))

            val def = effect.toEffectDefinition()
            assertEquals(effect.id, def.id)
            assertEquals("packs", def.category)
            assertEquals(effect.name, def.name)

            parsedIds += effect.id
        }

        assertEquals(77, parsedIds.size)
        assertTrue(parsedIds.containsAll(proIds))
    }

    @Test
    fun verifyBundledEffectspackArchive() {
        val packFile = File("src/main/assets/packs/goldmotion_customfx.effectspack")
        if (!packFile.isFile) return

        ZipFile(packFile).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json")
            assertNotNull(manifestEntry)

            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifest = PackManifest.parse(manifestText)

            assertEquals("goldmotion.customfx", manifest.id)
            assertEquals("Gold Motion Custom FX", manifest.name)
            assertEquals(77, manifest.effects.size)

            val firstNine = manifest.effects.take(9).map { it.file.substringAfterLast('/').removeSuffix(".xml") }
            val expectedPro = listOf(
                "cinematic_punch",
                "radial_rush",
                "liquid_rupture",
                "data_collapse",
                "solar_flare",
                "poster_impact",
                "energy_storm",
                "tape_trauma",
                "holo_depth"
            )
            assertEquals(expectedPro, firstNine)

            for (entry in manifest.effects) {
                val fileEntry = zip.getEntry(entry.file)
                assertNotNull("Missing entry for ${entry.file}", fileEntry)
            }
        }
    }
}
