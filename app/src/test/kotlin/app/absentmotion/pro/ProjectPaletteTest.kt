package app.absentmotion.pro

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectPaletteTest {

    @Test
    fun roundTripSerialization() {
        val palette = ProjectPalette(
            id = "test_pal",
            name = "Brand Identity",
            swatches = listOf(
                PaletteSwatch("sw_teal", "Teal Primary", Color(0xFF008080)),
                PaletteSwatch("sw_orange", "Orange Pop", Color(0xFFFFA500))
            )
        )

        val json = palette.toJson()
        val restored = ProjectPalette.fromJson(json)

        assertEquals("test_pal", restored.id)
        assertEquals("Brand Identity", restored.name)
        assertEquals(2, restored.swatches.size)
        assertEquals("Teal Primary", restored.swatches[0].name)
        assertEquals("Orange Pop", restored.swatches[1].name)
    }

    @Test
    fun addAndRemoveSwatches() {
        val palette = ProjectPalette()
        val newSwatch = PaletteSwatch("custom_1", "Neon Green", Color(0xFF39FF14))

        val withNew = palette.withSwatch(newSwatch)
        assertNotNull(withNew.swatches.firstOrNull { it.id == "custom_1" })

        val without = withNew.withoutSwatch("custom_1")
        assertEquals(palette.swatches.size, without.swatches.size)
    }
}
