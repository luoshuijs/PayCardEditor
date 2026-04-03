package com.luoshui.paycardeditor.core

import com.luoshui.paycardeditor.R


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

object MiPayNavigator {

    private const val TARGET_PACKAGE = "com.miui.tsmclient"
    private const val TARGET_ACTIVITY = "com.miui.tsmclient.ui.quick.DoubleClickActivity"

    fun open(context: Context) {
        val packageManager = context.packageManager
        val launchIntent = Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
        }
        val canResolve = runCatching {
            packageManager.getPackageInfo(TARGET_PACKAGE, 0)
            launchIntent.resolveActivity(packageManager) != null
        }.getOrDefault(false)
        if (!canResolve) {
            Toast.makeText(context, R.string.open_mipay_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(launchIntent) }
            .onFailure {
                val messageRes = if (it is ActivityNotFoundException) R.string.open_mipay_not_supported else R.string.open_mipay_failed
                Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
            }
    }
}
