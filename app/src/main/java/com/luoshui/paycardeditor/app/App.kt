package com.luoshui.paycardeditor.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.luoshui.paycardeditor.data.settings.DataStoreSettingsRepository
import com.luoshui.paycardeditor.data.settings.SettingsRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        private const val TAG = "App"

        @Volatile
        var appContext: Context? = null
            private set

        @Volatile
        var xposedService: XposedService? = null
            private set
    }

    private val appExceptionHandler = CoroutineExceptionHandler { _, t ->
        // appScope uses SupervisorJob, so child failures do not cancel the scope.
        // Log crashes explicitly so long-lived background work does not fail silently.
        Log.e(TAG, "appScope coroutine crashed", t)
    }

    /**
     * Application-wide [CoroutineScope] with the same lifetime as [Application].
     *
     * ViewModels share this scope so DataStore `stateIn(Eagerly)` has one process-level
     * subscription instead of one detached scope per screen.
     * Do not launch Activity-bound UI work from this scope.
     */
    val appScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + appExceptionHandler)

    lateinit var settingsRepository: SettingsRepository
        private set

    /**
     * Whether the libxposed service is currently bound to this app process.
     *
     * [onServiceBind] and [onServiceDied] update this value, while Compose screens consume it as
     * a [StateFlow] for the home activation status.
     *
     * This flow and [xposedService] share the same callback source. [xposedService] is the
     * synchronous service handle; [xposedServiceConnected] is the reactive connection flag for
     * `collectAsStateWithLifecycle()`. The same callback path updates both values.
     */
    private val _xposedServiceConnected = MutableStateFlow(false)
    val xposedServiceConnected: StateFlow<Boolean> = _xposedServiceConnected.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        settingsRepository = DataStoreSettingsRepository(applicationContext, appScope)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        _xposedServiceConnected.value = true
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        _xposedServiceConnected.value = false
    }
}
