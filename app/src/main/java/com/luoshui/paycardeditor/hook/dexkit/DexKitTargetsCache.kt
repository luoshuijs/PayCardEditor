package com.luoshui.paycardeditor.hook.dexkit

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.luoshui.paycardeditor.core.HookEnvironment
import io.github.libxposed.api.XposedModule
import org.json.JSONObject

/**
 * Persists DexKit query results across host process restarts.
 *
 * The cache is keyed by an *invalidation tuple* (host package versionCode + module
 * versionCode). Whenever either side changes — host APK upgraded, or module rebuilt with
 * new matchers — the cached descriptors are discarded and DexKit is re-run on the next
 * startup.
 *
 * Storage goes through [com.luoshui.paycardeditor.data.SnapshotSyncProvider] so that the
 * hook process (running inside `com.miui.tsmclient`) and the module's own UI process share
 * a single SharedPreferences entry guarded by `grantUriPermissions`. Direct file I/O on the
 * module's `filesDir` from the host process would be blocked by SELinux on most ROMs.
 *
 * Failure mode: any read/write/parse failure is logged and treated as a cache miss; the
 * caller is expected to fall back to a full DexKit query. We never let cache problems
 * abort hook installation.
 */
internal class DexKitTargetsCache(private val module: XposedModule) {

    companion object {
        private const val TAG = "PayCardEditorHook"

        private const val KEY_HOST_VERSION_CODE = "hostVersionCode"
        private const val KEY_HOST_VERSION_NAME = "hostVersionName"
        private const val KEY_MODULE_VERSION_CODE = "moduleVersionCode"
        private const val KEY_DESCRIPTORS = "descriptors"
        private const val KEY_CACHED_AT = "cachedAt"
    }

    private val providerUri = "content://${HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY}".toUri()

    /**
     * Resolves the cached descriptors when the stored invalidation tuple matches the
     * current host + module versions. Returns null on any miss (no cache, mismatched
     * versions, malformed JSON, missing context).
     */
    fun load(context: Context, hostPackageName: String): DexKitTargetDescriptors? {
        val expected = buildVersionKey(context, hostPackageName) ?: run {
            module.log(Log.WARN, TAG, "DexKit cache load skipped: cannot resolve version key")
            return null
        }
        val rawJson = runCatching {
            val response = context.contentResolver.call(
                providerUri,
                HookEnvironment.METHOD_GET_DEXKIT_CACHE,
                null,
                Bundle()
            )
            response?.getString(HookEnvironment.EXTRA_DEXKIT_CACHE_JSON).orEmpty()
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit cache load failed: ${it.stackTraceToString()}")
        }.getOrDefault("")
        if (rawJson.isBlank()) {
            module.log(Log.INFO, TAG, "DexKit cache miss: empty payload")
            return null
        }
        return runCatching {
            val root = JSONObject(rawJson)
            val cachedHostCode = root.optLong(KEY_HOST_VERSION_CODE, -1L)
            val cachedHostName = root.optString(KEY_HOST_VERSION_NAME, "")
            val cachedModuleCode = root.optLong(KEY_MODULE_VERSION_CODE, -1L)
            if (cachedHostCode != expected.hostVersionCode ||
                cachedHostName != expected.hostVersionName ||
                cachedModuleCode != expected.moduleVersionCode
            ) {
                module.log(
                    Log.INFO,
                    TAG,
                    "DexKit cache invalidated: cached=$cachedHostCode/$cachedHostName/$cachedModuleCode " +
                        "current=${expected.hostVersionCode}/${expected.hostVersionName}/${expected.moduleVersionCode}"
                )
                return@runCatching null
            }
            val descriptors = DexKitTargetDescriptors.fromJson(root.optJSONObject(KEY_DESCRIPTORS))
            if (descriptors.isEmpty()) {
                module.log(Log.INFO, TAG, "DexKit cache hit but descriptors empty; treating as miss")
                null
            } else {
                module.log(
                    Log.INFO,
                    TAG,
                    "DexKit cache hit: cachedAt=${root.optLong(KEY_CACHED_AT, 0L)} versionKey=$expected"
                )
                descriptors
            }
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit cache parse failed: ${it.stackTraceToString()}")
        }.getOrNull()
    }

    /**
     * Writes [descriptors] to the cache stamped with the current invalidation tuple. No-op
     * when [descriptors] is empty (we never persist a wholly-failed query) or the version
     * key cannot be resolved.
     */
    fun save(context: Context, hostPackageName: String, descriptors: DexKitTargetDescriptors) {
        if (descriptors.isEmpty()) {
            module.log(Log.INFO, TAG, "DexKit cache save skipped: descriptors empty")
            return
        }
        val versionKey = buildVersionKey(context, hostPackageName) ?: run {
            module.log(Log.WARN, TAG, "DexKit cache save skipped: cannot resolve version key")
            return
        }
        val payload = JSONObject().apply {
            put(KEY_HOST_VERSION_CODE, versionKey.hostVersionCode)
            put(KEY_HOST_VERSION_NAME, versionKey.hostVersionName)
            put(KEY_MODULE_VERSION_CODE, versionKey.moduleVersionCode)
            put(KEY_CACHED_AT, System.currentTimeMillis())
            put(KEY_DESCRIPTORS, descriptors.toJson())
        }
        runCatching {
            context.contentResolver.call(
                providerUri,
                HookEnvironment.METHOD_UPSERT_DEXKIT_CACHE,
                null,
                Bundle().apply {
                    putString(HookEnvironment.EXTRA_DEXKIT_CACHE_JSON, payload.toString())
                }
            )
            module.log(Log.INFO, TAG, "DexKit cache saved versionKey=$versionKey")
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit cache save failed: ${it.stackTraceToString()}")
        }
    }

    /** Drops the cache entry; useful when partial hydration suggests the cache is stale. */
    fun invalidate(context: Context) {
        runCatching {
            context.contentResolver.call(
                providerUri,
                HookEnvironment.METHOD_CLEAR_DEXKIT_CACHE,
                null,
                Bundle()
            )
            module.log(Log.INFO, TAG, "DexKit cache invalidated by request")
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit cache invalidate failed: ${it.stackTraceToString()}")
        }
    }

    private fun buildVersionKey(context: Context, hostPackageName: String): VersionKey? {
        val packageManager = context.packageManager ?: return null
        val hostInfo = runCatching { packageManager.getPackageInfo(hostPackageName, 0) }
            .getOrElse {
                module.log(Log.WARN, TAG, "host package info missing: ${it.message}")
                return null
            }
        val moduleInfo = runCatching { packageManager.getPackageInfo(HookEnvironment.MODULE_PACKAGE, 0) }
            .getOrElse {
                module.log(Log.WARN, TAG, "module package info missing: ${it.message}")
                return null
            }
        return VersionKey(
            hostVersionCode = hostInfo.longVersionCode,
            hostVersionName = hostInfo.versionName.orEmpty(),
            moduleVersionCode = moduleInfo.longVersionCode,
        )
    }

    private data class VersionKey(
        val hostVersionCode: Long,
        val hostVersionName: String,
        val moduleVersionCode: Long,
    )
}
