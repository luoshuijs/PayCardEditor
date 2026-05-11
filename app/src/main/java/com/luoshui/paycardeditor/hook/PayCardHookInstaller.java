package com.luoshui.paycardeditor.hook;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.core.HookEnvironment;

import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;

final class PayCardHookInstaller {

    private static final String TAG = "PayCardEditorHook";

    private final XposedModule mModule;
    private final RemoteCardSnapshotStore mSnapshotStore;
    private final List<HookInstallerSupport.HookRecord> mInstalledHookRecords = new ArrayList<>();
    private final HookInstallerSupport mSupport;
    private final CardDataHookRegistrar mCardDataHooks;
    private final ImageCacheHookRegistrar mImageCacheHooks;
    private final MemoryCacheHookRegistrar mMemoryCacheHooks;
    private final HookDebugReporter mDebugReporter;
    private final DexKitTargetsCache mDexKitCache;

    private volatile boolean mInstalled = false;

    PayCardHookInstaller(@NonNull XposedModule module) {
        mModule = module;
        mSnapshotStore = new RemoteCardSnapshotStore(module);
        BankCardReplacer bankCardReplacer = new BankCardReplacer(module);
        mSupport = new HookInstallerSupport(module, mInstalledHookRecords);
        mCardDataHooks = new CardDataHookRegistrar(module, mSnapshotStore, bankCardReplacer, mSupport);
        mImageCacheHooks = new ImageCacheHookRegistrar(module, mSupport);
        mMemoryCacheHooks = new MemoryCacheHookRegistrar(module, mSupport, mImageCacheHooks);
        mDebugReporter = new HookDebugReporter(module, mSnapshotStore, mSupport);
        mDexKitCache = new DexKitTargetsCache(module);
    }

    void install(@NonNull ClassLoader classLoader, @NonNull String apkPath) {
        if (mInstalled) {
            return;
        }
        synchronized (this) {
            if (mInstalled) {
                return;
            }
            try {
                Class<?> cardInfoClass = Class.forName("com.miui.tsmclient.entity.CardInfo", false, classLoader);
                Class<?> cardInfoManagerClass = Class.forName("com.miui.tsmclient.entity.CardInfoManager", false, classLoader);
                Class<?> cacheLauncherClass = Class.forName("com.miui.tsmclient.entity.CardInfoManager$CacheLauncher", false, classLoader);
                DexKitHookTargets dexKitTargets = resolveDexKitTargets(classLoader, apkPath);

                boolean anyInstalled = false;
                anyInstalled |= installHookGroup("CardInfo hooks", () -> mCardDataHooks.installCardInfoHooks(cardInfoClass));
                anyInstalled |= installHookGroup("CardInfoManager hooks", () -> mCardDataHooks.installManagerHooks(cardInfoClass, cardInfoManagerClass, cacheLauncherClass));
                anyInstalled |= installHookGroup("Bank data hooks", () -> mCardDataHooks.installBankHooks(dexKitTargets));
                anyInstalled |= installHookGroup("Image cache hooks", () -> mImageCacheHooks.installImageHooks(dexKitTargets));
                anyInstalled |= installHookGroup("Memory cache hooks", () -> mMemoryCacheHooks.installMemoryHooks(dexKitTargets));
                anyInstalled |= installHookGroup("Transit hooks", () -> mCardDataHooks.installTransitHooks(cardInfoClass, dexKitTargets));
                anyInstalled |= installHookGroup("Mifare hooks", () -> mCardDataHooks.installMifareHooks(dexKitTargets));
                mDebugReporter.publishTroubleshootState(apkPath, cardInfoClass, cardInfoManagerClass, cacheLauncherClass, dexKitTargets, mImageCacheHooks);

                mInstalled = anyInstalled;
                if (anyInstalled) {
                    mModule.log(Log.INFO, TAG, "hooks installed for com.miui.tsmclient");
                } else {
                    mModule.log(Log.ERROR, TAG, "no hook groups were installed for com.miui.tsmclient");
                }
            } catch (Throwable throwable) {
                mSnapshotStore.recordInstallError("install", throwable);
                mModule.log(Log.ERROR, TAG, "failed to install hooks: " + Log.getStackTraceString(throwable));
            }
        }
    }

    /**
     * Returns hydrated DexKit targets, preferring the persisted descriptor cache when it
     * matches the current host APK + module versions. Falls back to a full DexKit query on
     * any cache miss / partial hydration / IO error and rewrites the cache with the fresh
     * descriptors so subsequent process starts skip the scan entirely.
     */
    @NonNull
    private DexKitHookTargets resolveDexKitTargets(@NonNull ClassLoader classLoader, @NonNull String apkPath) {
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context != null) {
            DexKitTargetDescriptors cached = mDexKitCache.load(context, HookEnvironment.TARGET_PACKAGE);
            if (cached != null) {
                DexKitHookTargets cachedTargets = DexKitMethodLocator.hydrate(cached, classLoader, mModule);
                if (DexKitMethodLocator.isFullyHydrated(cached, cachedTargets)) {
                    mModule.log(Log.INFO, TAG, "DexKit targets restored from cache; skipped full scan");
                    return cachedTargets;
                }
                mModule.log(Log.WARN, TAG, "DexKit cache hydration incomplete; running full DexKit scan and refreshing cache");
                mDexKitCache.invalidate(context);
            }
        } else {
            mModule.log(Log.WARN, TAG, "DexKit cache lookup skipped: application context not yet available");
        }
        DexKitTargetDescriptors descriptors = DexKitMethodLocator.query(apkPath, mModule);
        DexKitHookTargets targets = DexKitMethodLocator.hydrate(descriptors, classLoader, mModule);
        if (context != null) {
            mDexKitCache.save(context, HookEnvironment.TARGET_PACKAGE, descriptors);
        }
        return targets;
    }

    private boolean installHookGroup(@NonNull String label, @NonNull InstallAction action) {
        try {
            action.run();
            mModule.log(Log.INFO, TAG, label + " installed");
            return true;
        } catch (Throwable throwable) {
            mSnapshotStore.recordInstallError(label, throwable);
            mModule.log(Log.ERROR, TAG, "failed to install " + label + ": " + Log.getStackTraceString(throwable));
            return false;
        }
    }

    @FunctionalInterface
    private interface InstallAction {
        void run() throws Throwable;
    }
}
