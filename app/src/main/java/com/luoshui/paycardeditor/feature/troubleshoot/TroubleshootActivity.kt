package com.luoshui.paycardeditor.feature.troubleshoot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luoshui.paycardeditor.app.theme.PayCardThemedContent
import com.luoshui.paycardeditor.data.ModuleStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Troubleshooting entry Activity.
 *
 * Owns only Compose wiring. [PayCardThemedContent] manages theme and
 * edge-to-edge behavior, and [TroubleshootViewModel] reads troubleshooting
 * state from [ModuleStateRepository] on the IO dispatcher because the repository
 * performs synchronous SharedPreferences reads. The lifecycle observer refreshes
 * on resume while the Activity ViewModelStore preserves state across configuration changes.
 */
class TroubleshootActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PayCardThemedContent {
                val viewModel: TroubleshootViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            TroubleshootViewModel(
                                stateReader = {
                                    // ModuleStateRepository is synchronous, so the injected reader switches to IO.
                                    withContext(Dispatchers.IO) { ModuleStateRepository.loadTroubleshootState() }
                                },
                            )
                        }
                    }
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Resume refresh is idempotent and may duplicate the ViewModel's initial load.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.handleEvent(TroubleshootEvent.Refresh)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                TroubleshootScreen(
                    uiState = uiState,
                    effects = viewModel.effects,
                    onEvent = viewModel::handleEvent,
                    onBack = { finish() },
                    errorEvents = viewModel.errorEvents,
                )
            }
        }
    }
}
