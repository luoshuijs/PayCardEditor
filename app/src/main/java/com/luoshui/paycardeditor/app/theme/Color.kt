package com.luoshui.paycardeditor.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Default brand seed color for payment-card gold. */
val BrandSeedGold: Color = Color(0xFFC9A227)

/** Alternate brand seed color for dark silver card surfaces. */
val BrandSeedDarkSilver: Color = Color(0xFF6C7A89)

/**
 * Seed color menu: two brand colors plus thirteen common variants.
 *
 * The UI prepends a "follow system" option for the `keyColorArgb == 0` sentinel. Values are ARGB
 * Ints so they can be stored directly in DataStore.
 */
val keyColorOptions: List<Int> = listOf(
    BrandSeedGold.toArgb(),
    BrandSeedDarkSilver.toArgb(),
    0xFF2EBF91.toInt(),
    0xFF1976D2.toInt(),
    0xFF8E24AA.toInt(),
    0xFFE53935.toInt(),
    0xFFF4511E.toInt(),
    0xFFFB8C00.toInt(),
    0xFF43A047.toInt(),
    0xFF00897B.toInt(),
    0xFF00ACC1.toInt(),
    0xFF5E35B1.toInt(),
    0xFF3949AB.toInt(),
    0xFF6D4C41.toInt(),
    0xFF546E7A.toInt(),
)
