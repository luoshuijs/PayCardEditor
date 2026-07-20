package com.luoshui.paycardeditor.app.theme

/**
 * Seven-state theme mode. [value] is the stable DataStore wire value; append new entries at the
 * end to preserve backward compatibility.
 *
 * Predicate combinations:
 *  - SYSTEM       : isSystem
 *  - LIGHT        : (none)
 *  - DARK         : isDark
 *  - MONET_SYSTEM : isSystem + isMonet
 *  - MONET_LIGHT  : isMonet
 *  - MONET_DARK   : isDark + isMonet
 *  - DARK_AMOLED  : isDark + isAmoled
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value in 3..5

    companion object {
        fun fromValue(value: Int): ColorMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}
