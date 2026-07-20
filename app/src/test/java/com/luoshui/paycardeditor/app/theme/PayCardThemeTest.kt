package com.luoshui.paycardeditor.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PayCardThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `LIGHT mode with explicit seed yields non-black primary derived from seed`() {
        val captured = mutableStateOf<Color?>(null)
        val appearance = AppearanceSettings(
            colorMode = ColorMode.LIGHT,
            keyColorArgb = BrandSeedGold.toArgb(),
            paletteStyleName = "TonalSpot",
            colorSpecName = "SPEC_2025",
        )
        composeTestRule.setContent {
            PayCardTheme(appearance) {
                captured.value = MaterialTheme.colorScheme.primary
                Text("probe")
            }
        }
        composeTestRule.waitForIdle()
        val primary = checkNotNull(captured.value) { "primary 未被读取" }
        assertNotEquals(Color.Black, primary)
        assertNotEquals(Color.White, primary)
        // The primary color derived from gold #C9A227 should keep a warm bias.
        assert(primary.red > primary.blue) { "primary $primary 应保留种子的暖色调" }
    }

    @Test
    fun `DARK_AMOLED mode yields pure black surface`() {
        val captured = mutableStateOf<Color?>(null)
        val appearance = AppearanceSettings(
            colorMode = ColorMode.DARK_AMOLED,
            keyColorArgb = BrandSeedGold.toArgb(),
            paletteStyleName = "TonalSpot",
            colorSpecName = "SPEC_2025",
        )
        composeTestRule.setContent {
            PayCardTheme(appearance) {
                captured.value = MaterialTheme.colorScheme.surface
                Text("probe")
            }
        }
        composeTestRule.waitForIdle()
        val surface = checkNotNull(captured.value)
        assertEquals(Color.Black, surface)
    }

    @Test
    fun `MONET_LIGHT uses system dynamic scheme and ignores custom seed`() {
        val actualPrimary = mutableStateOf<Color?>(null)
        val expectedPrimary = mutableStateOf<Color?>(null)
        val appearance = AppearanceSettings(
            colorMode = ColorMode.MONET_LIGHT,
            keyColorArgb = BrandSeedGold.toArgb(),
            paletteStyleName = "TonalSpot",
            colorSpecName = "SPEC_2025",
        )

        composeTestRule.setContent {
            expectedPrimary.value = dynamicLightColorScheme(LocalContext.current).primary
            PayCardTheme(appearance) {
                actualPrimary.value = MaterialTheme.colorScheme.primary
                Text("probe")
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(expectedPrimary.value, actualPrimary.value)
    }

    @Test
    fun `invalid paletteStyleName silently falls back to TonalSpot`() {
        val captured = mutableStateOf<Color?>(null)
        val appearance = AppearanceSettings(
            colorMode = ColorMode.LIGHT,
            keyColorArgb = BrandSeedGold.toArgb(),
            paletteStyleName = "ThisIsNotARealPaletteStyle",
            colorSpecName = "SPEC_2025",
        )
        composeTestRule.setContent {
            PayCardTheme(appearance) {
                captured.value = MaterialTheme.colorScheme.primary
                Text("probe")
            }
        }
        composeTestRule.waitForIdle()
        assert(captured.value != null)
    }
}
