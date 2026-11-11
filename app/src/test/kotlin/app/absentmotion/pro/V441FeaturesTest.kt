package app.absentmotion.pro

import app.absentmotion.model.Pt
import app.absentmotion.model.PuppetBend
import app.absentmotion.model.PuppetPin
import app.absentmotion.pro.assets.AssetSource
import app.absentmotion.pro.assets.AssetType
import app.absentmotion.pro.assets.OnlineAsset
import app.absentmotion.pro.cache.CacheCategory
import app.absentmotion.pro.cache.CacheManager
import app.absentmotion.pro.cache.CachePreset
import app.absentmotion.pro.effects.GroupSharpnessEnhancer
import app.absentmotion.pro.morph.ImageMorphConfig
import app.absentmotion.pro.morph.ImageMorphEngine
import app.absentmotion.pro.morph.ShapeMorphConfig
import app.absentmotion.pro.morph.ShapeMorphEngine
import app.absentmotion.pro.morph.ShapePreset
import app.absentmotion.pro.text3d.CharacterAnimatorConfig
import app.absentmotion.pro.text3d.CharacterSelectorUnit
import app.absentmotion.pro.text3d.CharacterTransform
import app.absentmotion.pro.text3d.MaterialType
import app.absentmotion.pro.text3d.Text3dConfig
import app.absentmotion.pro.text3d.Text3dRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V441FeaturesTest {

    @Test
    fun testOnlineAssetModels() {
        val asset = OnlineAsset(
            id = "test_1",
            title = "Test Sticker",
            type = AssetType.STICKER,
            source = AssetSource.GIPHY,
            previewUrl = "https://example.com/thumb.webp",
            downloadUrl = "https://example.com/full.webp",
            width = 300,
            height = 300,
            hasAlpha = true
        )
        assertEquals("test_1", asset.id)
        assertEquals(AssetType.STICKER, asset.type)
        assertEquals(AssetSource.GIPHY, asset.source)
        assertTrue(asset.hasAlpha)
    }

    @Test
    fun test3dTextPresetsAndInterpolation() {
        assertEquals(6, CharacterAnimatorConfig.Presets.size)

        val presetNames = CharacterAnimatorConfig.Presets.map { it.presetName }
        assertTrue("Typewriter" in presetNames)
        assertTrue("3D Flip" in presetNames)
        assertTrue("Wave" in presetNames)
        assertTrue("Bounce Drop" in presetNames)
        assertTrue("Explosion" in presetNames)
        assertTrue("Letter-by-Letter Reveal" in presetNames)

        val typewriter = CharacterAnimatorConfig.Presets.first { it.presetName == "Typewriter" }
        assertEquals(CharacterSelectorUnit.Character, typewriter.unit)

        val prog0 = Text3dRenderer.calculateUnitProgress(0, 5, 0L, typewriter)
        val progEnd = Text3dRenderer.calculateUnitProgress(0, 5, 1000L, typewriter)
        assertTrue(prog0 <= 0.1f)
        assertEquals(1f, progEnd, 0.001f)

        val t0 = CharacterTransform(opacity = 0f, scaleX = 0f)
        val t1 = CharacterTransform(opacity = 1f, scaleX = 1f)
        val mid = Text3dRenderer.lerpTransform(t0, t1, 0.5f)
        assertEquals(0.5f, mid.opacity, 0.001f)
        assertEquals(0.5f, mid.scaleX, 0.001f)
    }

    @Test
    fun test3dTextSerialization() {
        val config = Text3dConfig(extrusionDepth = 35f, bevelSize = 5f, material = MaterialType.Plastic)
        val anim = CharacterAnimatorConfig(presetName = "Wave", staggerMs = 50L)
        val serialized = Text3dRenderer.serialize(config, anim)

        val (parsedConfig, parsedAnim) = Text3dRenderer.parseConfig(serialized)
        assertEquals(35f, parsedConfig.extrusionDepth, 0.001f)
        assertEquals(5f, parsedConfig.bevelSize, 0.001f)
        assertEquals(MaterialType.Plastic, parsedConfig.material)
        assertEquals("Wave", parsedAnim.presetName)
        assertEquals(50L, parsedAnim.staggerMs)
    }

    @Test
    fun testCacheManagerCategoriesAndFormat() {
        assertEquals(6, CacheCategory.entries.size)
        assertEquals(4, CachePreset.entries.size)

        assertEquals("0 B", CacheManager.formatBytes(0L))
        assertEquals("500 B", CacheManager.formatBytes(500L))
        assertEquals("1.0 KB", CacheManager.formatBytes(1024L))
        assertEquals("1.0 MB", CacheManager.formatBytes(1024L * 1024L))
        assertEquals("2.00 GB", CacheManager.formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun testPuppetBendSolver() {
        val pins = listOf(
            PuppetPin(1, Pt(50f, 50f), Pt(50f, 50f)), // Fixed pin at rest
            PuppetPin(2, Pt(100f, 70f), Pt(100f, 50f)) // Moved pin: +20 in Y
        )
        val bend = PuppetBend(pins, falloff = 1.0f)

        // Point near pin 1 should remain nearly unchanged
        val ptNear1 = bend.source(Pt(50f, 50f))
        assertEquals(50f, ptNear1.x, 2.0f)
        assertEquals(50f, ptNear1.y, 2.0f)

        // Point near pin 2 should map back towards rest
        val ptNear2 = bend.source(Pt(100f, 70f))
        assertTrue(abs(ptNear2.y - 50f) < abs(70f - 50f))
    }

    @Test
    fun testGroupSharpnessParams() {
        val params = GroupSharpnessEnhancer.SharpnessParams(
            intensity = 1.2f,
            radius = 2.0f,
            edgePreservation = 0.85f,
            detailPreservation = 0.95f
        )
        assertEquals(1.2f, params.intensity, 0.001f)
        assertEquals(2.0f, params.radius, 0.001f)
        assertEquals(0.85f, params.edgePreservation, 0.001f)
        assertEquals(0.95f, params.detailPreservation, 0.001f)
    }

    @Test
    fun testVectorShapeMorphSamplingAndAlignment() {
        val triPoints = ShapeMorphEngine.generatePoints(ShapePreset.Triangle, 200f)
        val circlePoints = ShapeMorphEngine.generatePoints(ShapePreset.Circle, 200f)
        val heartPoints = ShapeMorphEngine.generatePoints(ShapePreset.Heart, 200f)
        val starPoints = ShapeMorphEngine.generatePoints(ShapePreset.Star, 200f)

        assertEquals(ShapeMorphEngine.SAMPLE_COUNT, triPoints.size)
        assertEquals(ShapeMorphEngine.SAMPLE_COUNT, circlePoints.size)
        assertEquals(ShapeMorphEngine.SAMPLE_COUNT, heartPoints.size)
        assertEquals(ShapeMorphEngine.SAMPLE_COUNT, starPoints.size)

        val shift = ShapeMorphEngine.findBestAlignment(triPoints, circlePoints)
        assertTrue(shift >= 0 && shift < ShapeMorphEngine.SAMPLE_COUNT)

        val morphConfig = ShapeMorphConfig(
            startShape = ShapePreset.Triangle,
            endShape = ShapePreset.Circle,
            durationMs = 1000L
        )
        val resultMid = ShapeMorphEngine.interpolate(morphConfig, 500L)
        assertNotNull(resultMid.path)
        assertEquals(0.5f, resultMid.progress, 0.05f)
    }

    @Test
    fun testImageMorphMeshInterpolation() {
        val meshA = ImageMorphEngine.createDefaultMesh(4, 4, 100f, 100f)
        val meshB = ImageMorphEngine.createDefaultMesh(4, 4, 100f, 100f)
        assertEquals(25, meshA.size)

        // Deform one point in B
        meshB[12].x = 75f
        meshB[12].y = 75f

        val intermediateVerts = ImageMorphEngine.interpolateMesh(meshA, meshB, 0.5f)
        assertEquals(50, intermediateVerts.size) // 25 points * 2 (x, y)

        val midX = intermediateVerts[12 * 2]
        val midY = intermediateVerts[12 * 2 + 1]
        val expectedX = (meshA[12].x + 75f) / 2f
        val expectedY = (meshA[12].y + 75f) / 2f
        assertEquals(expectedX, midX, 0.001f)
        assertEquals(expectedY, midY, 0.001f)
    }

    @Test
    fun test3dModelLibrary() {
        val models = kotlinx.coroutines.runBlocking {
            app.absentmotion.pro.assets.OnlineAssetService.search3dModels("")
        }
        assertTrue(models.size >= 12)
        assertTrue(models.all { it.type == AssetType.MODEL_3D })
        assertTrue(models.all { it.source == AssetSource.FREE3D })
        val names = models.map { it.title }
        assertTrue(names.any { it.contains("Suzanne", ignoreCase = true) })
        assertTrue(names.any { it.contains("Car", ignoreCase = true) })
        assertTrue(names.any { it.contains("Spaceship", ignoreCase = true) })
        assertTrue(names.any { it.contains("Teapot", ignoreCase = true) })
    }

    @Test
    fun testFallbackAssetsNoDeadGiphyUrls() {
        val gifs = kotlinx.coroutines.runBlocking {
            app.absentmotion.pro.assets.OnlineAssetService.searchGiphy("")
        }
        assertTrue(gifs.isNotEmpty())
        assertFalse(gifs.any { it.contentUrl.contains("sample") })
        assertFalse(gifs.any { it.previewUrl.contains("sample") })

        val svgs = kotlinx.coroutines.runBlocking {
            app.absentmotion.pro.assets.OnlineAssetService.searchFlaticonSvg("")
        }
        assertTrue(svgs.isNotEmpty())
        assertTrue(svgs.all { it.type == AssetType.SVG })
    }
}
