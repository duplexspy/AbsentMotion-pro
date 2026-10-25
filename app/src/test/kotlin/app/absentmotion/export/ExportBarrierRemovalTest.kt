package app.absentmotion.export

import app.absentmotion.Feature
import app.absentmotion.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBarrierRemovalTest {

    @Test
    fun exportBlockDoesNotBlockUnsupportedSupport() {
        val unsupported = Support.Unsupported(app.absentmotion.i18n.txt(app.absentmotion.R.string.export_support_no_encoder, "AV1"), null)
        val blocked = exportBlock(StorageStatus.Enough, unsupported)
        assertNull(blocked)
    }

    @Test
    fun exportBlockOnlyBlocksInsufficientStorage() {
        val blocked = exportBlock(StorageStatus.NotEnough(100L, 50L), Support.Supported)
        assertEquals(app.absentmotion.i18n.txt(app.absentmotion.R.string.export_block_storage), blocked)
    }

    @Test
    fun allChoicesAreAlwaysEnabled() {
        val settings = ExportSettings(1080, 30, Codec.Av1, container = Container.Mp4)
        val fakeCheck: (ExportSettings) -> Support = { Support.Unsupported(app.absentmotion.i18n.txt(app.absentmotion.R.string.export_support_no_encoder, "AV1"), null) }

        val containers = containerChoices(settings, fakeCheck)
        assertTrue(containers.all { it.enabled })

        val codecs = codecChoices(settings, fakeCheck)
        assertTrue(codecs.all { it.enabled })

        val resolutions = resolutionChoices(settings, fakeCheck)
        assertTrue(resolutions.all { it.enabled })

        val audioCodecs = audioCodecChoices(Container.Mp4) { false }
        assertTrue(audioCodecs.all { it.enabled })

        val audioFormats = audioFormatChoices { false }
        assertTrue(audioFormats.all { it.enabled })
    }

    @Test
    fun tierChoicesAreNeverLocked() {
        for (feature in Feature.entries) {
            val choice = Tier.choice("value", feature)
            assertFalse(choice.locked)
        }
    }
}
