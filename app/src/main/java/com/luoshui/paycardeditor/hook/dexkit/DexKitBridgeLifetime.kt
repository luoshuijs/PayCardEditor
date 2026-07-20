package com.luoshui.paycardeditor.hook.dexkit

import org.luckypray.dexkit.DexKitBridge

/** Keeps full-scan bridges alive until Android terminates the hooked process. */
internal object DexKitBridgeLifetime {
    @Volatile
    private var retainedBridges: List<DexKitBridge> = emptyList()

    fun retain(bridge: DexKitBridge) {
        synchronized(this) {
            retainedBridges = retainedBridges + bridge
        }
    }
}
