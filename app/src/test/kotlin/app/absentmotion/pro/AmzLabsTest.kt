package app.absentmotion.pro

import app.absentmotion.pro.labs.AmzLabCategory
import app.absentmotion.pro.labs.AmzLabTool
import app.absentmotion.pro.labs.AntiCompressConfig
import app.absentmotion.pro.labs.AudioConvertConfig
import app.absentmotion.pro.labs.ConvertConfig
import app.absentmotion.pro.labs.DeadFrameConfig
import app.absentmotion.pro.labs.DepthMapConfig
import app.absentmotion.pro.labs.FrameInterpolationConfig
import app.absentmotion.pro.labs.ReverseConfig
import app.absentmotion.pro.labs.StabilizerConfig
import app.absentmotion.pro.labs.TwixtorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmzLabsTest {

    @Test
    fun testAllTwelveSatriyaidToolsPresent() {
        assertEquals(12, AmzLabTool.entries.size)

        val expectedTools = listOf(
            "depth_map" to "Depth Map Generator",
            "bg_remover" to "Background Remover",
            "ai_upscale" to "AI Upscale",
            "tiktok_anti_compress" to "TikTok Anti Compress",
            "twixtor" to "Twixtor",
            "dead_frame_remover" to "Dead Frame Remover",
            "stabilizer" to "Stabilizer",
            "audio_converter" to "Audio Converter",
            "ai_frame_interpolation" to "AI Frame Interpolation",
            "audio_denoise" to "Audio Denoise",
            "reverse_video" to "Reverse Video",
            "convert_video" to "Convert Video"
        )

        for ((id, title) in expectedTools) {
            val tool = AmzLabTool.entries.firstOrNull { it.id == id }
            assertNotNull("Tool with id $id must exist", tool)
            assertEquals("Tool title must match screenshot", title, tool?.title)
            assertTrue("Subtitle must not be blank", tool?.subtitle?.isNotBlank() == true)
        }
    }

    @Test
    fun testSubfolderNamesMatchAmzConvention() {
        assertEquals("AmzDepth", AmzLabTool.DepthMap.subfolder)
        assertEquals("AmzBgRemove", AmzLabTool.BackgroundRemover.subfolder)
        assertEquals("AmzUpscale", AmzLabTool.AiUpscale.subfolder)
        assertEquals("AmzAntiCompress", AmzLabTool.TikTokAntiCompress.subfolder)
        assertEquals("AmzTwixtor", AmzLabTool.Twixtor.subfolder)
        assertEquals("AmzDeadFrame", AmzLabTool.DeadFrameRemover.subfolder)
        assertEquals("AmzStabilizer", AmzLabTool.Stabilizer.subfolder)
        assertEquals("AmzAudio", AmzLabTool.AudioConverter.subfolder)
        assertEquals("AmzInterpolation", AmzLabTool.AiFrameInterpolation.subfolder)
        assertEquals("AmzDenoise", AmzLabTool.AudioDenoise.subfolder)
        assertEquals("AmzReverse", AmzLabTool.ReverseVideo.subfolder)
        assertEquals("AmzConvert", AmzLabTool.ConvertVideo.subfolder)
    }

    @Test
    fun testAiClassification() {
        assertTrue(AmzLabTool.DepthMap.isAi)
        assertTrue(AmzLabTool.BackgroundRemover.isAi)
        assertTrue(AmzLabTool.AiUpscale.isAi)
        assertTrue(AmzLabTool.AiFrameInterpolation.isAi)
        assertTrue(AmzLabTool.AudioDenoise.isAi)

        assertEquals(AmzLabCategory.METADATA, AmzLabTool.TikTokAntiCompress.category)
        assertEquals(AmzLabCategory.VIDEO, AmzLabTool.ReverseVideo.category)
        assertEquals(AmzLabCategory.VIDEO, AmzLabTool.ConvertVideo.category)
        assertEquals(AmzLabCategory.AUDIO, AmzLabTool.AudioConverter.category)
    }

    @Test
    fun testDefaultConfigs() {
        val antiCompress = AntiCompressConfig()
        assertTrue(antiCompress.fastStart)
        assertTrue(antiCompress.iphoneTag)
        assertTrue(antiCompress.colorBt709)

        val twixtor = TwixtorConfig()
        assertEquals(50, twixtor.speedPercent)
        assertEquals("Motion", twixtor.motionMode)

        val convert = ConvertConfig()
        assertEquals("1080p", convert.resolution)
        assertEquals(30, convert.fps)
        assertEquals("H.264", convert.codec)

        val reverse = ReverseConfig()
        assertTrue(reverse.reverseAudio)

        val audio = AudioConvertConfig()
        assertEquals("M4A", audio.format)
        assertEquals(256, audio.bitrateKbps)
        assertEquals(44100, audio.sampleRateHz)

        val deadFrame = DeadFrameConfig()
        assertTrue(deadFrame.syncAudio)

        val stabilizer = StabilizerConfig()
        assertEquals("Smooth Motion", stabilizer.mode)
        assertTrue(stabilizer.autoCrop)

        val interp = FrameInterpolationConfig()
        assertEquals(2, interp.targetMultiplier)

        val depth = DepthMapConfig()
        assertEquals("MiDaS", depth.model)
    }
}
