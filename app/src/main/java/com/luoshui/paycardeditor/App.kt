package com.luoshui.paycardeditor

import android.app.Application
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        @Volatile
        var appContext: Context? = null
            private set

        @Volatile
        var xposedService: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            listeners += listener
            if (notifyImmediately) {
                listener.onServiceStateChanged(xposedService)
            }
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            listeners -= listener
        }

        private fun dispatchServiceState(service: XposedService?) {
            listeners.forEach { it.onServiceStateChanged(service) }
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        dispatchServiceState(service)
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        dispatchServiceState(null)
    }
}
