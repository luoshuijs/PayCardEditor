package com.luoshui.paycardeditor.hook

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.luoshui.paycardeditor.HookEnvironment
import io.github.libxposed.api.XposedModule

internal class BankCardRuleResolver(
    private val module: XposedModule,
) {
    companion object {
        private const val TAG = "PayCardEditorHook"
        private const val CACHE_TTL_MS = 5_000L
    }

    private val providerUri = "content://${HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY}".toUri()

    @Volatile
    private var cachedRules: List<BankCardRule> = emptyList()

    @Volatile
    private var lastLoadAt: Long = 0L

    fun getRules(context: Context?): List<BankCardRule> {
        val now = System.currentTimeMillis()
        if (now - lastLoadAt < CACHE_TTL_MS) {
            return cachedRules
        }
        val resolvedContext = context ?: return cachedRules
        return runCatching {
            val result = resolvedContext.contentResolver.call(
                providerUri,
                HookEnvironment.METHOD_GET_BANK_RULES,
                null,
                Bundle(),
            )
            val rulesJson = result?.getString(HookEnvironment.EXTRA_RULES_JSON).orEmpty()
            BankCardRule.parseList(rulesJson)
        }.onFailure {
            module.log(Log.ERROR, TAG, "load bank rules failed: ${it.stackTraceToString()}")
        }.getOrDefault(cachedRules).also {
            cachedRules = it
            lastLoadAt = now
        }
    }
}
