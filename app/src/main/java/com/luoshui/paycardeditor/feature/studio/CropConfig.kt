package com.luoshui.paycardeditor.feature.studio

import android.content.Context
import com.luoshui.paycardeditor.core.HookEnvironment

/**
 * Persisted UCrop sizing parameters.
 *
 * Stored alongside the rest of the module state in
 * [HookEnvironment.LOCAL_STATE_PREFS_NAME]. Defaults preserve the historical
 * hardcoded values (192:121 aspect, 960x605 max output) so an upgrade with no
 * user-visible settings change behaves identically to previous versions.
 */
object CropConfig {

    const val DEFAULT_ASPECT_X: Int = 192
    const val DEFAULT_ASPECT_Y: Int = 121
    const val DEFAULT_MAX_WIDTH: Int = 960
    const val DEFAULT_MAX_HEIGHT: Int = 605

    const val ASPECT_MIN: Int = 1
    const val ASPECT_MAX: Int = 9999
    const val SIZE_MIN: Int = 64
    const val SIZE_MAX: Int = 4096

    private const val KEY_ASPECT_X = "crop_aspect_x"
    private const val KEY_ASPECT_Y = "crop_aspect_y"
    private const val KEY_MAX_WIDTH = "crop_max_width"
    private const val KEY_MAX_HEIGHT = "crop_max_height"

    data class Values(
        val aspectX: Int,
        val aspectY: Int,
        val maxWidth: Int,
        val maxHeight: Int,
    )

    val defaults: Values = Values(
        aspectX = DEFAULT_ASPECT_X,
        aspectY = DEFAULT_ASPECT_Y,
        maxWidth = DEFAULT_MAX_WIDTH,
        maxHeight = DEFAULT_MAX_HEIGHT,
    )

    fun load(context: Context): Values {
        val prefs = context.applicationContext.getSharedPreferences(
            HookEnvironment.LOCAL_STATE_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        return Values(
            aspectX = readAspect(prefs, KEY_ASPECT_X, DEFAULT_ASPECT_X),
            aspectY = readAspect(prefs, KEY_ASPECT_Y, DEFAULT_ASPECT_Y),
            maxWidth = readSize(prefs, KEY_MAX_WIDTH, DEFAULT_MAX_WIDTH),
            maxHeight = readSize(prefs, KEY_MAX_HEIGHT, DEFAULT_MAX_HEIGHT),
        )
    }

    /**
     * Persist [values] synchronously. Uses [android.content.SharedPreferences.Editor.commit]
     * so the next consumer of [load] (typically the next crop session) is
     * guaranteed to read the latest values, even if the user immediately
     * navigates away.
     */
    fun save(context: Context, values: Values): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(
            HookEnvironment.LOCAL_STATE_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        return prefs.edit()
            .putInt(KEY_ASPECT_X, values.aspectX)
            .putInt(KEY_ASPECT_Y, values.aspectY)
            .putInt(KEY_MAX_WIDTH, values.maxWidth)
            .putInt(KEY_MAX_HEIGHT, values.maxHeight)
            .commit()
    }

    private fun readAspect(
        prefs: android.content.SharedPreferences,
        key: String,
        default: Int,
    ): Int {
        val stored = prefs.getInt(key, default)
        return if (stored in ASPECT_MIN..ASPECT_MAX) stored else default
    }

    private fun readSize(
        prefs: android.content.SharedPreferences,
        key: String,
        default: Int,
    ): Int {
        val stored = prefs.getInt(key, default)
        return if (stored in SIZE_MIN..SIZE_MAX) stored else default
    }
}
