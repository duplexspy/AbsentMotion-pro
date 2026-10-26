package app.absentmotion.pro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.absentmotion.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumUiHarnessTest {

    @Test
    fun testTextPrimaryContrastOnAllSurfaces() {
        val contrast0 = ProDesignTokens.contrastRatio(ProDesignTokens.TextPrimary, ProDesignTokens.Surface0)
        val contrast1 = ProDesignTokens.contrastRatio(ProDesignTokens.TextPrimary, ProDesignTokens.Surface1)
        val contrast2 = ProDesignTokens.contrastRatio(ProDesignTokens.TextPrimary, ProDesignTokens.Surface2)

        assertTrue(contrast0 >= 4.5f)
        assertTrue(contrast1 >= 4.5f)
        assertTrue(contrast2 >= 4.5f)
        assertTrue(contrast0 > 12.0f)
    }

    @Test
    fun testTextSecondaryContrastOnAllSurfaces() {
        val contrast0 = ProDesignTokens.contrastRatio(ProDesignTokens.TextSecondary, ProDesignTokens.Surface0)
        val contrast1 = ProDesignTokens.contrastRatio(ProDesignTokens.TextSecondary, ProDesignTokens.Surface1)
        val contrast2 = ProDesignTokens.contrastRatio(ProDesignTokens.TextSecondary, ProDesignTokens.Surface2)

        assertTrue(contrast0 >= 4.5f)
        assertTrue(contrast1 >= 4.5f)
        assertTrue(contrast2 >= 4.5f)
    }

    @Test
    fun testOnAccentContrastOnAccentPrimary() {
        val contrast = ProDesignTokens.contrastRatio(ProDesignTokens.OnAccent, ProDesignTokens.AccentPrimary)
        assertTrue(contrast >= 4.5f)
    }

    @Test
    fun testTouchTargetBoundsMinimum() {
        assertTrue(ProDesignTokens.TouchTargetMin >= 48.dp)
        assertEquals(48.dp, ProDesignTokens.TouchTargetMin)
    }

    @Test
    fun testSpacingAndGridConsistency() {
        assertEquals(2.dp, ProDesignTokens.Space2)
        assertEquals(4.dp, ProDesignTokens.Space4)
        assertEquals(8.dp, ProDesignTokens.Space8)
        assertEquals(12.dp, ProDesignTokens.Space12)
        assertEquals(16.dp, ProDesignTokens.Space16)
        assertEquals(20.dp, ProDesignTokens.Space20)
        assertEquals(24.dp, ProDesignTokens.Space24)
        assertEquals(32.dp, ProDesignTokens.Space32)
        assertEquals(48.dp, ProDesignTokens.Space48)

        assertTrue(ProDesignTokens.Space8.value % 4f == 0f)
        assertTrue(ProDesignTokens.Space12.value % 4f == 0f)
        assertTrue(ProDesignTokens.Space16.value % 4f == 0f)
        assertTrue(ProDesignTokens.Space24.value % 4f == 0f)
        assertTrue(ProDesignTokens.Space32.value % 4f == 0f)
        assertTrue(ProDesignTokens.Space48.value % 4f == 0f)
    }

    @Test
    fun testRadiiHierarchy() {
        assertTrue(ProDesignTokens.RadiusXs < ProDesignTokens.RadiusSmall)
        assertTrue(ProDesignTokens.RadiusSmall < ProDesignTokens.RadiusMedium)
        assertTrue(ProDesignTokens.RadiusMedium < ProDesignTokens.RadiusLarge)
        assertTrue(ProDesignTokens.RadiusLarge < ProDesignTokens.RadiusPill)
    }

    @Test
    fun testPremiumPaletteTokensIntegrity() {
        val premium = Palette.Premium
        assertEquals(ProDesignTokens.TextPrimary, premium.OnDark)
        assertEquals(ProDesignTokens.AccentPrimary, premium.Accent)
        assertEquals(ProDesignTokens.AccentGlow, premium.AccentGlow)
        assertEquals(ProDesignTokens.Surface1, premium.Panel)
        assertEquals(ProDesignTokens.Surface0, premium.HomeBg)
        assertEquals(Color(0xFF121419), premium.TopBar)

        val builtInNames = Palette.BuiltIn.map { it.first }
        assertTrue(builtInNames.contains("Premium"))
        assertTrue(builtInNames.contains("AbsentMotion"))
        assertTrue(builtInNames.contains("Daylight"))
        assertEquals("Premium", builtInNames.first())
    }

    @Test
    fun testSafeModeCleanReversionToClassic() {
        ProFeatures.setSafeMode(null, false)
        assertTrue(ProFeatures.isEnabled(ProFeatures.premiumUi))

        ProFeatures.setSafeMode(null, true)
        assertTrue(ProFeatures.isSafeMode)
        assertFalse(ProFeatures.isEnabled(ProFeatures.premiumUi))

        ProFeatures.setSafeMode(null, false)
        assertFalse(ProFeatures.isSafeMode)
        assertTrue(ProFeatures.isEnabled(ProFeatures.premiumUi))
    }

    @Test
    fun testRelativeLuminanceCalculations() {
        val blackLuminance = ProDesignTokens.relativeLuminance(Color(0xFF000000))
        val whiteLuminance = ProDesignTokens.relativeLuminance(Color(0xFFFFFFFF))
        val maxContrast = ProDesignTokens.contrastRatio(Color(0xFFFFFFFF), Color(0xFF000000))

        assertEquals(0.0f, blackLuminance, 0.001f)
        assertEquals(1.0f, whiteLuminance, 0.001f)
        assertEquals(21.0f, maxContrast, 0.01f)
    }
}
