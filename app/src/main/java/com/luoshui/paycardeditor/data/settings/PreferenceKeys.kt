package com.luoshui.paycardeditor.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences keys.
 *
 * `paycard_settings` is intentionally separate from `paycardeditor_state`; hook cross-process
 * data continues to use SharedPreferences.
 */
internal object PreferenceKeys {
    val COLOR_MODE = intPreferencesKey("color_mode")
    val KEY_COLOR = intPreferencesKey("key_color")
    val COLOR_STYLE = stringPreferencesKey("color_style")
    val COLOR_SPEC = stringPreferencesKey("color_spec")

    const val DATA_STORE_NAME = "paycard_settings"
}
