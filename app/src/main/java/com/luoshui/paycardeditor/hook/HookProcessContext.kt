package com.luoshui.paycardeditor.hook

import android.content.Context

internal object HookProcessContext {
    fun resolve(): Context? {
        val fromActivityThread = runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Context
        }.getOrNull()
        if (fromActivityThread != null) {
            return fromActivityThread
        }
        return runCatching {
            val clazz = Class.forName("android.app.AppGlobals")
            val method = clazz.getDeclaredMethod("getInitialApplication")
            method.isAccessible = true
            method.invoke(null) as? Context
        }.getOrNull()
    }

}
