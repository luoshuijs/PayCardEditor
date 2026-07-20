package com.luoshui.paycardeditor.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luoshui.paycardeditor.app.App
import com.luoshui.paycardeditor.app.theme.PayCardThemedContent
import com.luoshui.paycardeditor.feature.studio.CropConfig

/**
 * Settings entry Activity.
 *
 * Owns only Compose wiring. [PayCardThemedContent] manages theme and
 * edge-to-edge behavior, while [SettingsViewModel] bridges theme settings from
 * [App.settingsRepository] and crop settings from [CropConfig]. Crop settings
 * intentionally remain in the shared `paycardeditor_state` SharedPreferences.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PayCardThemedContent {
                val app = applicationContext as App
                val context = this@SettingsActivity
                val viewModel: SettingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                settingsRepository = app.settingsRepository,
                                cropConfigReader = {
                                    val v = CropConfig.load(context)
                                    CropValues(v.aspectX, v.aspectY, v.maxWidth, v.maxHeight)
                                },
                                cropConfigWriter = { x, y, w, h ->
                                    CropConfig.save(context, CropConfig.Values(x, y, w, h))
                                },
                            )
                        }
                    }
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = uiState,
                    onEvent = viewModel::handleEvent,
                    onBack = { finish() },
                    errorEvents = viewModel.errorEvents,
                )
            }
        }
    }
}
