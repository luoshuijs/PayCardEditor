package com.luoshui.paycardeditor.hook

import android.util.Log
import com.luoshui.paycardeditor.core.HookEnvironment
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class ModuleMain : XposedModule() {

    companion object {
        private const val TAG = "PayCardEditorHook"
        init {
            System.loadLibrary("dexkit")
        }
    }

    private val installer = PayCardHookInstaller(this)
    @Volatile private var processName: String = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        log(Log.INFO, TAG, "module loaded in ${param.processName}; framework=$frameworkName $frameworkVersion")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage || param.packageName != HookEnvironment.TARGET_PACKAGE) {
            return
        }
        // Skip non-main processes (e.g. `com.miui.tsmclient:daemon`). They only host MiPush
        // SDK services and a binder-forwarding `DaemonService`; none of our hook targets
        // (CardInfoManager, BankCardInfo, Glide cache, etc.) execute there, while running
        // DexKit's native scan in those subprocesses has triggered SIGABRT crashes.
        if (processName.isNotEmpty() && processName != HookEnvironment.TARGET_PACKAGE) {
            log(Log.INFO, TAG, "skip hook install in subprocess $processName")
            return
        }
        installer.install(param.classLoader, param.applicationInfo.sourceDir.orEmpty())
    }

}
