package com.luoshui.paycardeditor.app.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Central entry point for [enableEdgeToEdge].
 *
 *  * [SystemBarStyle.auto] receives explicit transparent colors so AMOLED dark mode does not keep
 *    a light scrim.
 *  * `detectDarkMode` uses the caller-provided [darkMode] value so system bar icons follow theme.
 *  * `isNavigationBarContrastEnforced` is disabled to avoid an extra system scrim.
 *  * `DisposableEffect(darkMode)` reruns setup when dark mode changes.
 */
@Composable
fun PayCardEdgeToEdge(darkMode: Boolean) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return
    DisposableEffect(darkMode) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ) { darkMode },
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ) { darkMode },
        )
        activity.window.isNavigationBarContrastEnforced = false
        onDispose { }
    }
}
