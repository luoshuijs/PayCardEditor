package com.luoshui.paycardeditor.hook

import android.util.Log
import com.luoshui.paycardeditor.HookEnvironment
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

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "module loaded in ${param.processName}; framework=$frameworkName $frameworkVersion")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage || param.packageName != HookEnvironment.TARGET_PACKAGE) {
            return
        }
        installer.install(param.classLoader, param.applicationInfo.sourceDir.orEmpty())
    }

}
