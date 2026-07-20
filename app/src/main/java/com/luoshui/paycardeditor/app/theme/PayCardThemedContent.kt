package com.luoshui.paycardeditor.app.theme

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luoshui.paycardeditor.app.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PayCardThemedContent"

/**
 * Activity theme entry point that combines the shared setup:
 *
 *   1. read the `appearance` [StateFlow] from the app settings repository;
 *   2. collect it with `collectAsStateWithLifecycle()` and derive `darkMode`;
 *   3. wrap content in [PayCardTheme] and apply [PayCardEdgeToEdge].
 *
 * The app lookup is nullable because previews and tests can run with a non-[App] Application.
 * In that case, [AppearanceSettings.Default] is exposed through a constant [MutableStateFlow]
 * instead of crashing. A real Activity falling back here logs a warning.
 *
 * Usage:
 * ```
 * setContent { PayCardThemedContent { /* screen body */ } }
 * ```
 */
@Composable
fun PayCardThemedContent(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as? App
    val appearanceFlow: StateFlow<AppearanceSettings> = remember(app) {
        if (app != null) {
            app.settingsRepository.appearance
        } else {
            // Previews and tests may not instantiate App. In production, this warning exposes an
            // unexpected Application replacement or class-loading problem.
            Log.w(TAG, "applicationContext is not App; falling back to AppearanceSettings.Default")
            PreviewAppearanceFlow
        }
    }
    val appearance by appearanceFlow.collectAsStateWithLifecycle()
    val darkMode = resolveDarkMode(appearance)
    PayCardEdgeToEdge(darkMode)
    PayCardTheme(appearance) {
        content()
    }
}

/** Constant fallback [StateFlow] for previews and tests. */
private val PreviewAppearanceFlow: StateFlow<AppearanceSettings> =
    MutableStateFlow(AppearanceSettings.Default).asStateFlow()
