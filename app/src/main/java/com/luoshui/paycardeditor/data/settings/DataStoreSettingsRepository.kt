package com.luoshui.paycardeditor.data.settings

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.luoshui.paycardeditor.app.theme.AppearanceSettings
import com.luoshui.paycardeditor.app.theme.ColorMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// DataStore requires one process-level delegate per preference file name.
private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = PreferenceKeys.DATA_STORE_NAME)

/**
 * [SettingsRepository] backed by DataStore Preferences.
 *
 * The supplied [CoroutineScope] keeps the DataStore flow hot for the app process lifetime.
 *
 * This repository never reads or writes `paycardeditor_state`; hook cross-process state remains on
 * SharedPreferences.
 */
class DataStoreSettingsRepository(
    appContext: Context,
    scope: CoroutineScope,
) : SettingsRepository {

    private val dataStore: DataStore<Preferences> = appContext.applicationContext.settingsDataStore

    override val appearance: StateFlow<AppearanceSettings> = dataStore.data
        .map { prefs ->
            AppearanceSettings(
                colorMode = ColorMode.fromValue(
                    prefs[PreferenceKeys.COLOR_MODE] ?: AppearanceSettings.Default.colorMode.value
                ),
                keyColorArgb = prefs[PreferenceKeys.KEY_COLOR] ?: AppearanceSettings.Default.keyColorArgb,
                paletteStyleName = prefs[PreferenceKeys.COLOR_STYLE] ?: AppearanceSettings.Default.paletteStyleName,
                colorSpecName = prefs[PreferenceKeys.COLOR_SPEC] ?: AppearanceSettings.Default.colorSpecName,
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppearanceSettings.Default,
        )

    override suspend fun setColorMode(colorMode: ColorMode) {
        dataStore.edit { it[PreferenceKeys.COLOR_MODE] = colorMode.value }
    }

    override suspend fun setKeyColor(keyColorArgb: Int) {
        dataStore.edit { it[PreferenceKeys.KEY_COLOR] = keyColorArgb }
    }

    override suspend fun setPaletteStyleName(name: String) {
        dataStore.edit { it[PreferenceKeys.COLOR_STYLE] = name }
    }

    override suspend fun setColorSpecName(name: String) {
        dataStore.edit { it[PreferenceKeys.COLOR_SPEC] = name }
    }

    /** Test-only hook for editing DataStore directly. */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        dataStore.edit { transform(it) }
    }
}
