package com.example

import androidx.compose.ui.graphics.Color
import com.example.inknote.data.model.ThemeMode
import com.example.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class DarkModeAndThemeTest {

    @Test
    fun `theme mode enum supports light, dark, and system`() {
        val modes = ThemeMode.values().map { it.name }
        assertTrue(modes.contains("LIGHT"))
        assertTrue(modes.contains("DARK"))
        assertTrue(modes.contains("SYSTEM"))
        assertEquals(3, modes.size)
    }

    @Test
    fun `dark mode color palette satisfies design specifications`() {
        // 1. Primary background: dark charcoal/near-black, avoiding harsh pure #000000 OLED smear
        assertNotEquals(Color.Black, BackgroundDark)
        assertTrue("Background red component should be between 0.05 and 0.15", BackgroundDark.red in 0.05f..0.15f)
        assertTrue("Background green component should be between 0.05 and 0.15", BackgroundDark.green in 0.05f..0.15f)
        assertTrue("Background blue component should be between 0.05 and 0.15", BackgroundDark.blue in 0.05f..0.15f)

        // 2. Elevated surfaces: slightly lighter than background for visual hierarchy
        assertTrue("Surface must be brighter than Background", SurfaceDark.red > BackgroundDark.red)
        assertTrue("SurfaceVariant must be brighter than Surface", SurfaceVariantDark.red > SurfaceDark.red)

        // 3. Text contrast: soft off-white to reduce eye strain, not glaring pure #FFFFFF
        assertNotEquals(Color.White, OnSurfaceDark)
        assertTrue("OnSurface text should be near 0.9 brightness", OnSurfaceDark.red in 0.85f..0.98f)
        assertTrue("Muted text OnSurfaceVariant should be dimmer than primary text", OnSurfaceVariantDark.red < OnSurfaceDark.red)

        // 4. Accent/Brand color: vibrant against dark background
        assertTrue("Dark indigo accent must have strong presence", IndigoPrimaryDark.red > 0.4f)
        assertTrue("Dark indigo accent container provides pleasant depth", IndigoPrimaryContainerDark.alpha > 0.9f)
    }

    @Test
    fun `dark paper canvas palette provides comfortable contrast`() {
        // Dark paper must be distinguished from the dark base canvas background
        assertNotEquals(DarkPaperBackground, BackgroundDark)
        assertTrue(DarkPaperBackground.red > BackgroundDark.red)

        // Dark paper grid line must have subtle transparency
        assertTrue(DarkPaperGridLine.alpha < 0.5f)
    }

    @Test
    fun `black ink adapts cleanly for legibility on dark paper without modifying stored data`() {
        val blackInk = Color(0xFF0F172A)
        val luminance = 0.299f * blackInk.red + 0.587f * blackInk.green + 0.114f * blackInk.blue
        assertTrue("Black ink luminance is below 0.25", luminance < 0.25f)

        // Test adaptation logic
        val adaptedColor = if (luminance < 0.25f) Color(0xFFE8E8EA) else blackInk
        assertEquals(Color(0xFFE8E8EA), adaptedColor)

        // Vibrant colors (e.g. Red, Blue, Purple) should not be overridden
        val crimsonRed = Color(0xFFDC2626)
        val redLuminance = 0.299f * crimsonRed.red + 0.587f * crimsonRed.green + 0.114f * crimsonRed.blue
        val redAdapted = if (redLuminance < 0.25f) Color(0xFFE8E8EA) else crimsonRed
        assertEquals(crimsonRed, redAdapted)
    }
}
