package com.luoshui.paycardeditor.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal data class DexKitHookTargets(
    val bankVirtualCardMerge: Method? = null,
    val bankQueryPanMerge: Method? = null,
    val transitUpdateBackground: Method? = null,
    val mifareQuery: Method? = null,
    val glideTokenGenerate: Method? = null,
    val glideDiskCacheWrapperClass: Class<*>? = null,
    val glideDiskCacheGet: Method? = null,
    val glideDiskCachePut: Method? = null,
    /**
     * Glide's Engine class, used by [MemoryCacheHookRegistrar] to hook the unified
     * memory-cache lookup ({@code loadFromCache(EngineKey, boolean, long)}). Fingerprinted
     * by the verbatim string {@code "Started new load"} which Glide v4 emits when debug
     * logging is enabled — present in classes.dex even when logging is off.
     */
    val glideEngineClass: Class<*>? = null,
)

/**
 * Serializable counterpart of [DexKitHookTargets]. Each field stores a DexKit descriptor
 * string produced by [DexMethod.serialize] / [DexClass.serialize] (e.g.
 * `Lj6/e;->O(Lcom/miui/tsmclient/entity/BankCardInfo;...)V` or `Ln3/e;`). Persisting
 * descriptors instead of raw class names lets us round-trip through SharedPreferences and
 * rehydrate reflective members on the next process startup without re-running DexKit.
 */
internal data class DexKitTargetDescriptors(
    val bankVirtualCardMerge: String? = null,
    val bankQueryPanMerge: String? = null,
    val transitUpdateBackground: String? = null,
    val mifareQuery: String? = null,
    val glideTokenGenerate: String? = null,
    val glideDiskCacheWrapperClass: String? = null,
    val glideDiskCacheGet: String? = null,
    val glideDiskCachePut: String? = null,
    val glideEngineClass: String? = null,
) {
    /** Returns true when no descriptor was produced; treated as a cache-miss sentinel. */
    fun isEmpty(): Boolean = bankVirtualCardMerge == null &&
        bankQueryPanMerge == null &&
        transitUpdateBackground == null &&
        mifareQuery == null &&
        glideTokenGenerate == null &&
        glideDiskCacheWrapperClass == null &&
        glideDiskCacheGet == null &&
        glideDiskCachePut == null &&
        glideEngineClass == null

    fun toJson(): JSONObject = JSONObject().apply {
        putOpt(KEY_BANK_VIRTUAL_CARD_MERGE, bankVirtualCardMerge)
        putOpt(KEY_BANK_QUERY_PAN_MERGE, bankQueryPanMerge)
        putOpt(KEY_TRANSIT_UPDATE_BACKGROUND, transitUpdateBackground)
        putOpt(KEY_MIFARE_QUERY, mifareQuery)
        putOpt(KEY_GLIDE_TOKEN_GENERATE, glideTokenGenerate)
        putOpt(KEY_GLIDE_DISK_CACHE_WRAPPER_CLASS, glideDiskCacheWrapperClass)
        putOpt(KEY_GLIDE_DISK_CACHE_GET, glideDiskCacheGet)
        putOpt(KEY_GLIDE_DISK_CACHE_PUT, glideDiskCachePut)
        putOpt(KEY_GLIDE_ENGINE_CLASS, glideEngineClass)
    }

    companion object {
        private const val KEY_BANK_VIRTUAL_CARD_MERGE = "bankVirtualCardMerge"
        private const val KEY_BANK_QUERY_PAN_MERGE = "bankQueryPanMerge"
        private const val KEY_TRANSIT_UPDATE_BACKGROUND = "transitUpdateBackground"
        private const val KEY_MIFARE_QUERY = "mifareQuery"
        private const val KEY_GLIDE_TOKEN_GENERATE = "glideTokenGenerate"
        private const val KEY_GLIDE_DISK_CACHE_WRAPPER_CLASS = "glideDiskCacheWrapperClass"
        private const val KEY_GLIDE_DISK_CACHE_GET = "glideDiskCacheGet"
        private const val KEY_GLIDE_DISK_CACHE_PUT = "glideDiskCachePut"
        private const val KEY_GLIDE_ENGINE_CLASS = "glideEngineClass"

        fun fromJson(json: JSONObject?): DexKitTargetDescriptors {
            if (json == null) return DexKitTargetDescriptors()
            return DexKitTargetDescriptors(
                bankVirtualCardMerge = json.optStringOrNull(KEY_BANK_VIRTUAL_CARD_MERGE),
                bankQueryPanMerge = json.optStringOrNull(KEY_BANK_QUERY_PAN_MERGE),
                transitUpdateBackground = json.optStringOrNull(KEY_TRANSIT_UPDATE_BACKGROUND),
                mifareQuery = json.optStringOrNull(KEY_MIFARE_QUERY),
                glideTokenGenerate = json.optStringOrNull(KEY_GLIDE_TOKEN_GENERATE),
                glideDiskCacheWrapperClass = json.optStringOrNull(KEY_GLIDE_DISK_CACHE_WRAPPER_CLASS),
                glideDiskCacheGet = json.optStringOrNull(KEY_GLIDE_DISK_CACHE_GET),
                glideDiskCachePut = json.optStringOrNull(KEY_GLIDE_DISK_CACHE_PUT),
                glideEngineClass = json.optStringOrNull(KEY_GLIDE_ENGINE_CLASS),
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            val raw = optString(key, "")
            return raw.ifBlank { null }
        }
    }
}

internal object DexKitMethodLocator {
    private const val TAG = "PayCardEditorHook"

    /**
     * Backwards-compatible entry point. Performs the full DexKit query against [apkPath] and
     * returns reflective handles. Each call opens / closes a fresh [DexKitBridge]. Prefer
     * [query] + [hydrate] when results need to be cached across invocations.
     */
    @JvmStatic
    fun resolve(apkPath: String, classLoader: ClassLoader, module: XposedModule): DexKitHookTargets {
        val descriptors = query(apkPath, module)
        return hydrate(descriptors, classLoader, module)
    }

    /**
     * Runs the DexKit lookup and returns the result as serializable descriptors. Returns an
     * empty bundle when the apk path is missing or the bridge fails to open.
     */
    @JvmStatic
    fun query(apkPath: String, module: XposedModule): DexKitTargetDescriptors {
        if (apkPath.isBlank()) {
            module.log(Log.WARN, TAG, "skip DexKit lookup: empty apk path")
            return DexKitTargetDescriptors()
        }
        return runCatching {
            DexKitBridge.create(apkPath).use { bridge ->
                val glide = findGlideDescriptors(bridge)
                DexKitTargetDescriptors(
                    bankVirtualCardMerge = findBankVirtualCardMerge(bridge),
                    bankQueryPanMerge = findBankQueryPanMerge(bridge),
                    transitUpdateBackground = findTransitUpdateBackground(bridge),
                    mifareQuery = findMifareQuery(bridge),
                    glideTokenGenerate = glide.tokenGenerate,
                    glideDiskCacheWrapperClass = glide.diskCacheWrapperClass,
                    glideDiskCacheGet = glide.diskCacheGet,
                    glideDiskCachePut = glide.diskCachePut,
                    glideEngineClass = glide.engineClass,
                )
            }
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit lookup failed: ${it.stackTraceToString()}")
        }.getOrDefault(DexKitTargetDescriptors()).also { descriptors ->
            module.log(Log.INFO, TAG, "DexKit query produced descriptors: ${descriptors.toJson()}")
        }
    }

    /**
     * Resolves cached descriptors back into reflective members. A null/blank descriptor maps
     * to a null result; a non-null descriptor that fails to resolve is logged but doesn't
     * abort the pass — callers can detect partial misses through [isFullyHydrated].
     */
    @JvmStatic
    fun hydrate(
        descriptors: DexKitTargetDescriptors,
        classLoader: ClassLoader,
        module: XposedModule,
    ): DexKitHookTargets {
        val targets = DexKitHookTargets(
            bankVirtualCardMerge = descriptors.bankVirtualCardMerge.toMethodOrNull(classLoader, module, "bankVirtualCardMerge"),
            bankQueryPanMerge = descriptors.bankQueryPanMerge.toMethodOrNull(classLoader, module, "bankQueryPanMerge"),
            transitUpdateBackground = descriptors.transitUpdateBackground.toMethodOrNull(classLoader, module, "transitUpdateBackground"),
            mifareQuery = descriptors.mifareQuery.toMethodOrNull(classLoader, module, "mifareQuery"),
            glideTokenGenerate = descriptors.glideTokenGenerate.toMethodOrNull(classLoader, module, "glideTokenGenerate"),
            glideDiskCacheWrapperClass = descriptors.glideDiskCacheWrapperClass.toClassOrNull(classLoader, module, "glideDiskCacheWrapperClass"),
            glideDiskCacheGet = descriptors.glideDiskCacheGet.toMethodOrNull(classLoader, module, "glideDiskCacheGet"),
            glideDiskCachePut = descriptors.glideDiskCachePut.toMethodOrNull(classLoader, module, "glideDiskCachePut"),
            glideEngineClass = descriptors.glideEngineClass.toClassOrNull(classLoader, module, "glideEngineClass"),
        )
        module.log(
            Log.INFO,
            TAG,
            "DexKit hydrated targets: bankO=${targets.bankVirtualCardMerge?.declaringClass?.name}.${targets.bankVirtualCardMerge?.name}, " +
                "bankN=${targets.bankQueryPanMerge?.declaringClass?.name}.${targets.bankQueryPanMerge?.name}, " +
                "transit=${targets.transitUpdateBackground?.declaringClass?.name}.${targets.transitUpdateBackground?.name}, " +
                "mifare=${targets.mifareQuery?.declaringClass?.name}.${targets.mifareQuery?.name}, " +
                "token=${targets.glideTokenGenerate?.declaringClass?.name}.${targets.glideTokenGenerate?.name}, " +
                "diskCache=${targets.glideDiskCacheWrapperClass?.name}, " +
                "diskGet=${targets.glideDiskCacheGet?.declaringClass?.name}.${targets.glideDiskCacheGet?.name}, " +
                "diskPut=${targets.glideDiskCachePut?.declaringClass?.name}.${targets.glideDiskCachePut?.name}, " +
                "engine=${targets.glideEngineClass?.name}"
        )
        return targets
    }

    /**
     * Returns true when every non-null descriptor in [descriptors] yielded a matching
     * reflective member in [targets]. Used by the cache layer to decide whether stored
     * descriptors are still valid for the current host APK; on first miss the cache should
     * be invalidated and a full DexKit query re-run.
     */
    @JvmStatic
    fun isFullyHydrated(descriptors: DexKitTargetDescriptors, targets: DexKitHookTargets): Boolean {
        if (descriptors.bankVirtualCardMerge != null && targets.bankVirtualCardMerge == null) return false
        if (descriptors.bankQueryPanMerge != null && targets.bankQueryPanMerge == null) return false
        if (descriptors.transitUpdateBackground != null && targets.transitUpdateBackground == null) return false
        if (descriptors.mifareQuery != null && targets.mifareQuery == null) return false
        if (descriptors.glideTokenGenerate != null && targets.glideTokenGenerate == null) return false
        if (descriptors.glideDiskCacheWrapperClass != null && targets.glideDiskCacheWrapperClass == null) return false
        if (descriptors.glideDiskCacheGet != null && targets.glideDiskCacheGet == null) return false
        if (descriptors.glideDiskCachePut != null && targets.glideDiskCachePut == null) return false
        if (descriptors.glideEngineClass != null && targets.glideEngineClass == null) return false
        return true
    }

    private data class GlideDescriptors(
        val tokenGenerate: String? = null,
        val diskCacheWrapperClass: String? = null,
        val diskCacheGet: String? = null,
        val diskCachePut: String? = null,
        val engineClass: String? = null,
    )

    private fun findBankVirtualCardMerge(bridge: DexKitBridge): String? =
        bridge.findMethod {
            matcher {
                returnType = "void"
                paramTypes(
                    "com.miui.tsmclient.entity.BankCardInfo",
                    "com.miui.tsmclient.seitsm.TsmRpcModels\$VirtualCardInfoResponse",
                )
            }
        }.singleOrNull()?.toDexMethod()?.serialize()

    private fun findBankQueryPanMerge(bridge: DexKitBridge): String? =
        bridge.findMethod {
            matcher {
                returnType = "void"
                paramTypes(
                    "com.miui.tsmclient.entity.BankCardInfo",
                    "com.miui.tsmclient.seitsm.TsmRpcModels\$QueryPanResponse",
                )
            }
        }.singleOrNull()?.toDexMethod()?.serialize()

    private fun findTransitUpdateBackground(bridge: DexKitBridge): String? =
        bridge.findMethod {
            matcher {
                returnType = "java.lang.Boolean"
                paramTypes("android.content.Context")
                usingStrings("PersonalCardFaceRequest")
            }
        }.singleOrNull()?.toDexMethod()?.serialize()

    private fun findMifareQuery(bridge: DexKitBridge): String? =
        bridge.findMethod {
            matcher {
                returnType = "com.miui.tsmclient.model.h"
                paramTypes("android.content.Context", null)
                usingStrings(
                    "key_notify_server_update_card",
                    "updateCommunityCardFlowStatus failed",
                    " getCalculateWaitingTime failed",
                )
            }
        }.singleOrNull()?.toDexMethod()?.serialize()

    private fun findGlideDescriptors(bridge: DexKitBridge): GlideDescriptors {
        // The "token generator" hook intercepts the host's MD5-based filename generator that
        // turns image URLs into base36 hashes used as Glide source keys downstream. Despite
        // sharing the legacy `Md5FileNameGenerator` filename with a deprecated Glide v3
        // helper, this is in fact MiPay's own utility (`com.miui.tsmclient.util.m1`) on the
        // hot path: SlideView/h0 call it to derive cache tokens, then hand the token to
        // Glide.load(...) as the model. Without intercepting it we cannot map back from the
        // base36 sourceKey we observe in the disk-cache wrapper to the original URL needed
        // for replacement lookup. Empirical verification: probing real disk-cache keys shows
        // shapes like `ResourceCacheKey{sourceKey=wos5kmf6987y95dky4dw23yb, ...}` — pure
        // base36 hashes — confirming this is the dynamic mapping's only source of truth.
        //
        // jadx re-verification (host APK 9.21.0.001):
        //   * `com.miui.tsmclient.util.m1` source filename is `Md5FileNameGenerator.java`
        //     (jadx INFO comment), but the class belongs to MiPay, NOT Glide v3. Body:
        //         BigInteger(md5(url.bytes)).abs().toString(36)
        //   * `com.miui.tsmclient.util.h0` (`CustomGlideUrl extends GlideUrl`) overrides
        //     `getCacheKey()` and pipes the URL through `new m1().a(...)` — i.e. m1 IS the
        //     Glide v4 cache-key transform on the hot path.
        //   * `com.miui.tsmclient.ui.widget.SlideView.g(CardInfo)` derives the
        //     `R.id.cardstack_url_tag` dedup token via `new m1().a(cardArt)` to detect
        //     when a card face URL changes between rebinds.
        // See ImageCacheHookRegistrar.installGlideTokenHooks for the consumer side.
        val tokenGeneratorClasses = bridge.findClass {
            matcher {
                fields {
                    addForType("java.util.regex.Pattern")
                    count(1..2)
                }
                methods {
                    add {
                        modifiers = Modifier.PRIVATE
                        returnType = "byte[]"
                        paramTypes("byte[]")
                        usingStrings("MD5", "getMD5 failed")
                    }
                    add {
                        modifiers = Modifier.PUBLIC
                        returnType = "java.lang.String"
                        paramTypes("java.lang.String")
                    }
                    count(2..4)
                }
                usingStrings("MD5", "getMD5 failed")
            }
        }
        val diskCacheWrapperClasses = bridge.findClass {
            matcher {
                fields {
                    addForType("java.io.File")
                    addForType("long")
                    count(4..8)
                }
                methods {
                    add {
                        modifiers = Modifier.PUBLIC
                        returnType = "java.io.File"
                        paramCount = 1
                        usingStrings("Get: Obtained:", "Unable to get from disk cache")
                    }
                    add {
                        modifiers = Modifier.PUBLIC
                        returnType = "void"
                        paramCount = 2
                        usingStrings(
                            "Put: Obtained:",
                            "Unable to put to disk cache",
                            "Had two simultaneous puts for:",
                        )
                    }
                    count(3..8)
                }
                usingStrings(
                    "DiskLruCacheWrapper",
                    "Get: Obtained:",
                    "Put: Obtained:",
                    "Unable to get from disk cache",
                    "Unable to put to disk cache",
                    "Had two simultaneous puts for:",
                )
            }
        }
        val tokenGenerate = tokenGeneratorClasses.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC
                returnType = "java.lang.String"
                paramTypes("java.lang.String")
            }
        }.singleOrNull()
        val diskCacheGet = diskCacheWrapperClasses.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC
                returnType = "java.io.File"
                paramCount = 1
                usingStrings("Get: Obtained:", "Unable to get from disk cache")
            }
        }.singleOrNull()
        val diskCachePut = diskCacheWrapperClasses.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC
                returnType = "void"
                paramCount = 2
                usingStrings(
                    "Put: Obtained:",
                    "Unable to put to disk cache",
                    "Had two simultaneous puts for:",
                )
            }
        }.singleOrNull()
        // Prefer the wrapper class detected by findClass; fall back to the declaring class
        // of either disk-cache method so we still expose a usable Class<?> handle even when
        // the class-level matcher narrows differently across APK versions.
        val diskCacheWrapperClassData = diskCacheWrapperClasses.singleOrNull()
        val diskCacheWrapperDescriptor = diskCacheWrapperClassData?.toDexClass()?.serialize()
            ?: diskCacheGet?.declaredClass?.toDexClass()?.serialize()
            ?: diskCachePut?.declaredClass?.toDexClass()?.serialize()

        // Glide's memory-cache flash-mitigation hook needs the Engine class, located by the
        // verbatim string "Started new load" (kept by Glide v4 across releases regardless of
        // debug logging). We don't fingerprint MemoryCache / ActiveResources / Key separately
        // because the Engine class exposes a unified `loadFromCache(EngineKey, boolean, long)`
        // private method that funnels both ActiveResources and MemoryCache lookups — hooking
        // that single, non-abstract method covers both layers with one entry point. See
        // MemoryCacheHookRegistrar.findLoadFromCacheMethod for the shape-based selection.
        val engineClassDescriptor = bridge.findClass {
            matcher {
                usingStrings("Started new load", "Loaded resource from cache")
            }
        }.singleOrNull()?.toDexClass()?.serialize()

        return GlideDescriptors(
            tokenGenerate = tokenGenerate?.toDexMethod()?.serialize(),
            diskCacheWrapperClass = diskCacheWrapperDescriptor,
            diskCacheGet = diskCacheGet?.toDexMethod()?.serialize(),
            diskCachePut = diskCachePut?.toDexMethod()?.serialize(),
            engineClass = engineClassDescriptor,
        )
    }

    private fun String?.toMethodOrNull(
        classLoader: ClassLoader,
        module: XposedModule,
        label: String,
    ): Method? {
        if (this.isNullOrBlank()) return null
        return runCatching {
            DexMethod(this).getMethodInstance(classLoader)
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit hydrate $label failed: descriptor=$this err=${it.message}")
        }.getOrNull()
    }

    private fun String?.toClassOrNull(
        classLoader: ClassLoader,
        module: XposedModule,
        label: String,
    ): Class<*>? {
        if (this.isNullOrBlank()) return null
        return runCatching {
            DexClass(this).getInstance(classLoader)
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit hydrate $label failed: descriptor=$this err=${it.message}")
        }.getOrNull()
    }
}
