package app.absentmotion.pro

import app.absentmotion.export.ExportSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPresetsTest {

    @Test
    fun verifyPresetsCoverage() {
        val presets = SocialPresets.presets
        assertTrue(presets.isNotEmpty())

        val tiktok = presets.firstOrNull { it.aspectLabel == "9:16" }
        assertNotNull(tiktok)
        assertEquals(1080, tiktok!!.width)
        assertEquals(1920, tiktok.height)

        val square = presets.firstOrNull { it.aspectLabel == "1:1" }
        assertNotNull(square)
        assertEquals(1080, square!!.width)
        assertEquals(1080, square.height)

        val landscape = presets.firstOrNull { it.aspectLabel == "16:9" }
        assertNotNull(landscape)
        assertEquals(1920, landscape!!.width)
        assertEquals(1080, landscape.height)
    }

    @Test
    fun exportQueueLifecycle() {
        ExportQueue.clearCompleted()
        val item = ExportQueue.enqueue("proj_1", "Social Reel", ExportSettings(1080, 30))

        assertTrue(ExportQueue.items.value.any { it.id == item.id })

        ExportQueue.updateStatus(item.id, QueueItemStatus.Exporting, 0.5f)
        val inProgress = ExportQueue.items.value.first { it.id == item.id }
        assertEquals(QueueItemStatus.Exporting, inProgress.status)
        assertEquals(0.5f, inProgress.progress, 0.001f)

        ExportQueue.updateStatus(item.id, QueueItemStatus.Completed, 1.0f)
        ExportQueue.clearCompleted()
        assertTrue(ExportQueue.items.value.none { it.id == item.id })
    }
}
