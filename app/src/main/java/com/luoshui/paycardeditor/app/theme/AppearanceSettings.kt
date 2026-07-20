package com.luoshui.paycardeditor.app.theme

/**
 * Current theme choices persisted by [com.luoshui.paycardeditor.data.settings.SettingsRepository].
 *
 * @param colorMode seven-state theme mode
 * @param keyColorArgb seed color as ARGB Int; `0` means use the system Monet primary color
 * @param paletteStyleName `com.materialkolor.PaletteStyle.<name>`; invalid values fall back to TonalSpot
 * @param colorSpecName `com.materialkolor.dynamiccolor.ColorSpec.SpecVersion.<name>`; invalid values fall back to SPEC_2025
 */
data class AppearanceSettings(
    val colorMode: ColorMode,
    val keyColorArgb: Int,
    val paletteStyleName: String,
    val colorSpecName: String,
) {
    companion object {
        val Default: AppearanceSettings = AppearanceSettings(
            colorMode = ColorMode.SYSTEM,
            keyColorArgb = 0,
            paletteStyleName = "TonalSpot",
            colorSpecName = "SPEC_2025",
        )
    }
}
