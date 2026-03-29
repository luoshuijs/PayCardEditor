package com.luoshui.paycardeditor.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge
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
)

internal object DexKitMethodLocator {
    private const val TAG = "PayCardEditorHook"

    private data class GlideDexKitTargets(
        val tokenGenerate: Method? = null,
        val diskCacheWrapperClass: Class<*>? = null,
        val diskCacheGet: Method? = null,
        val diskCachePut: Method? = null,
    )

    @JvmStatic
    fun resolve(apkPath: String, classLoader: ClassLoader, module: XposedModule): DexKitHookTargets {
        if (apkPath.isBlank()) {
            module.log(Log.WARN, TAG, "skip DexKit lookup: empty apk path")
            return DexKitHookTargets()
        }
        return runCatching {
            DexKitBridge.create(apkPath).use { bridge ->
                val glideTargets = findGlideTargets(bridge, classLoader)
                DexKitHookTargets(
                    bankVirtualCardMerge = findBankVirtualCardMerge(bridge, classLoader),
                    bankQueryPanMerge = findBankQueryPanMerge(bridge, classLoader),
                    transitUpdateBackground = findTransitUpdateBackground(bridge, classLoader),
                    mifareQuery = findMifareQuery(bridge, classLoader),
                    glideTokenGenerate = glideTargets.tokenGenerate,
                    glideDiskCacheWrapperClass = glideTargets.diskCacheWrapperClass,
                    glideDiskCacheGet = glideTargets.diskCacheGet,
                    glideDiskCachePut = glideTargets.diskCachePut,
                )
            }
        }.onFailure {
            module.log(Log.WARN, TAG, "DexKit lookup failed: ${it.stackTraceToString()}")
        }.getOrDefault(DexKitHookTargets()).also { targets ->
            module.log(
                Log.INFO,
                TAG,
                "DexKit resolved methods: bankO=${targets.bankVirtualCardMerge?.declaringClass?.name}.${targets.bankVirtualCardMerge?.name}, " +
                    "bankN=${targets.bankQueryPanMerge?.declaringClass?.name}.${targets.bankQueryPanMerge?.name}, " +
                    "transit=${targets.transitUpdateBackground?.declaringClass?.name}.${targets.transitUpdateBackground?.name}, " +
                    "mifare=${targets.mifareQuery?.declaringClass?.name}.${targets.mifareQuery?.name}, " +
                    "token=${targets.glideTokenGenerate?.declaringClass?.name}.${targets.glideTokenGenerate?.name}, " +
                    "diskCache=${targets.glideDiskCacheWrapperClass?.name}, " +
                    "diskGet=${targets.glideDiskCacheGet?.declaringClass?.name}.${targets.glideDiskCacheGet?.name}, " +
                    "diskPut=${targets.glideDiskCachePut?.declaringClass?.name}.${targets.glideDiskCachePut?.name}"
            )
        }
    }

    private fun findBankVirtualCardMerge(bridge: DexKitBridge, classLoader: ClassLoader): Method? =
        bridge.findMethod {
            matcher {
                returnType = "void"
                paramTypes(
                    "com.miui.tsmclient.entity.BankCardInfo",
                    "com.miui.tsmclient.seitsm.TsmRpcModels\$VirtualCardInfoResponse",
                )
            }
        }.singleOrNull()?.getMethodInstance(classLoader)

    private fun findBankQueryPanMerge(bridge: DexKitBridge, classLoader: ClassLoader): Method? =
        bridge.findMethod {
            matcher {
                returnType = "void"
                paramTypes(
                    "com.miui.tsmclient.entity.BankCardInfo",
                    "com.miui.tsmclient.seitsm.TsmRpcModels\$QueryPanResponse",
                )
            }
        }.singleOrNull()?.getMethodInstance(classLoader)

    private fun findTransitUpdateBackground(bridge: DexKitBridge, classLoader: ClassLoader): Method? =
        bridge.findMethod {
            matcher {
                returnType = "java.lang.Boolean"
                paramTypes("android.content.Context")
                usingStrings("PersonalCardFaceRequest")
            }
        }.singleOrNull()?.getMethodInstance(classLoader)

    private fun findMifareQuery(bridge: DexKitBridge, classLoader: ClassLoader): Method? =
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
        }.singleOrNull()?.getMethodInstance(classLoader)

    private fun findGlideTargets(bridge: DexKitBridge, classLoader: ClassLoader): GlideDexKitTargets {
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
        }.singleOrNull()?.getMethodInstance(classLoader)
        val diskCacheGet = diskCacheWrapperClasses.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC
                returnType = "java.io.File"
                paramCount = 1
                usingStrings("Get: Obtained:", "Unable to get from disk cache")
            }
        }.singleOrNull()?.getMethodInstance(classLoader)
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
        }.singleOrNull()?.getMethodInstance(classLoader)
        val diskCacheWrapperClass = diskCacheWrapperClasses.singleOrNull()?.getInstance(classLoader)
            ?: diskCacheGet?.declaringClass
            ?: diskCachePut?.declaringClass
        return GlideDexKitTargets(
            tokenGenerate = tokenGenerate,
            diskCacheWrapperClass = diskCacheWrapperClass,
            diskCacheGet = diskCacheGet,
            diskCachePut = diskCachePut,
        )
    }
}
