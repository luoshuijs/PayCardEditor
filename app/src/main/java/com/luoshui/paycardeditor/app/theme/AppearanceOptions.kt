package com.luoshui.paycardeditor.app.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * String projections of MaterialKolor options for UI selection controls.
 *
 * Screens consume these strings instead of importing MaterialKolor directly, keeping theme
 * construction and enum access inside this package.
 *
 * Persisted values are enum `.name` strings. [PayCardTheme] falls back to `TonalSpot` and
 * `SPEC_2025` when stored names are unknown.
 */
val paletteStyleOptions: List<String> = PaletteStyle.entries.map { it.name }

val colorSpecOptions: List<String> = ColorSpec.SpecVersion.entries.map { it.name }
