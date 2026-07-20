package com.luoshui.paycardeditor.data.settings

import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.app.theme.ColorMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Theme preference repository.
 *
 * [appearance] is exposed as a [StateFlow] for Compose `collectAsStateWithLifecycle()` consumers.
 * Writes are `suspend` functions so DataStore IO remains coroutine-bound.
 *
 * Implementations must use a single DataStore delegate per process; creating multiple delegates
 * for the same file name can throw [IllegalStateException].
 */
interface SettingsRepository {
    val appearance: StateFlow<AppearanceSettings>

    /** Persists [ColorMode.value] as the stable Int wire format. */
    suspend fun setColorMode(colorMode: ColorMode)

    /** Persists the seed color as ARGB Int; `0` means follow system Monet. */
    suspend fun setKeyColor(keyColorArgb: Int)

    /** Persists `com.materialkolor.PaletteStyle.<name>`; invalid names fall back in PayCardTheme. */
    suspend fun setPaletteStyleName(name: String)

    /** Persists `ColorSpec.SpecVersion.<name>`; invalid names fall back in PayCardTheme. */
    suspend fun setColorSpecName(name: String)
}
