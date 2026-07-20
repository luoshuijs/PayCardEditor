package com.luoshui.paycardeditor.app.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorModeTest {

    @Test
    fun `values map back to enum by Int`() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(0))
        assertEquals(ColorMode.LIGHT, ColorMode.fromValue(1))
        assertEquals(ColorMode.DARK, ColorMode.fromValue(2))
        assertEquals(ColorMode.MONET_SYSTEM, ColorMode.fromValue(3))
        assertEquals(ColorMode.MONET_LIGHT, ColorMode.fromValue(4))
        assertEquals(ColorMode.MONET_DARK, ColorMode.fromValue(5))
        assertEquals(ColorMode.DARK_AMOLED, ColorMode.fromValue(6))
    }

    @Test
    fun `unknown Int falls back to SYSTEM`() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(-1))
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(7))
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(99))
    }

    @Test
    fun `isSystem true for SYSTEM and MONET_SYSTEM only`() {
        assertTrue(ColorMode.SYSTEM.isSystem)
        assertTrue(ColorMode.MONET_SYSTEM.isSystem)
        assertFalse(ColorMode.LIGHT.isSystem)
        assertFalse(ColorMode.DARK.isSystem)
        assertFalse(ColorMode.MONET_LIGHT.isSystem)
        assertFalse(ColorMode.MONET_DARK.isSystem)
        assertFalse(ColorMode.DARK_AMOLED.isSystem)
    }

    @Test
    fun `isDark true for DARK MONET_DARK DARK_AMOLED only`() {
        assertTrue(ColorMode.DARK.isDark)
        assertTrue(ColorMode.MONET_DARK.isDark)
        assertTrue(ColorMode.DARK_AMOLED.isDark)
        assertFalse(ColorMode.SYSTEM.isDark)
        assertFalse(ColorMode.LIGHT.isDark)
        assertFalse(ColorMode.MONET_SYSTEM.isDark)
        assertFalse(ColorMode.MONET_LIGHT.isDark)
    }

    @Test
    fun `isAmoled true for DARK_AMOLED only`() {
        assertTrue(ColorMode.DARK_AMOLED.isAmoled)
        ColorMode.entries.filter { it != ColorMode.DARK_AMOLED }.forEach {
            assertFalse("$it should not be amoled", it.isAmoled)
        }
    }

    @Test
    fun `isMonet true for MONET_SYSTEM MONET_LIGHT MONET_DARK only`() {
        assertTrue(ColorMode.MONET_SYSTEM.isMonet)
        assertTrue(ColorMode.MONET_LIGHT.isMonet)
        assertTrue(ColorMode.MONET_DARK.isMonet)
        assertFalse(ColorMode.SYSTEM.isMonet)
        assertFalse(ColorMode.LIGHT.isMonet)
        assertFalse(ColorMode.DARK.isMonet)
        // DARK_AMOLED is dark mode with AMOLED black surfaces, not necessarily Monet.
        assertFalse(ColorMode.DARK_AMOLED.isMonet)
    }
}
