package com.luoshui.paycardeditor.app.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun PayCardTheme(
    appearance: AppearanceSettings = AppearanceSettings.Default,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkMode(appearance)
    val amoled = appearance.colorMode.isAmoled
    val context = LocalContext.current

    val paletteStyle = runCatching { PaletteStyle.valueOf(appearance.paletteStyleName) }
        .getOrDefault(PaletteStyle.TonalSpot)
    val colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(appearance.colorSpecName) }
        .getOrDefault(ColorSpec.SpecVersion.SPEC_2025)

    // minSdk is 35, so dynamic color schemes are always available.
    val seedColor: Color = if (appearance.keyColorArgb == 0) {
        val baseScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        baseScheme.primary
    } else {
        Color(appearance.keyColorArgb)
    }

    val colorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = amoled,
        style = paletteStyle,
        specVersion = colorSpec,
    )

    MaterialTheme(colorScheme = colorScheme) {
        LaunchedEffect(darkTheme) {
            val activity = context as? Activity ?: return@LaunchedEffect
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
        content()
    }
}

@Composable
@ReadOnlyComposable
fun resolveDarkMode(appearance: AppearanceSettings): Boolean =
    appearance.colorMode.isDark || (appearance.colorMode.isSystem && isSystemInDarkTheme())
