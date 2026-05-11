package com.luoshui.paycardeditor.hook.image;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.hook.HookInstallerSupport;
import com.luoshui.paycardeditor.hook.HookProcessContext;
import com.luoshui.paycardeditor.hook.HookReflectionUtils;
import com.luoshui.paycardeditor.hook.dexkit.DexKitHookTargets;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

public final class ImageCacheHookRegistrar {

    private static final String TAG = "PayCardEditorHook";

    private final XposedModule mModule;
    private final HookInstallerSupport mSupport;
    private final ReplacementMapStore mReplacementStore;
    /**
     * Reverse map {@code base36 cache token → CacheReplacementTarget}, populated by the
     * {@code m1.a(...)} interceptor in {@link #installGlideTokenHooks}. This is the only
     * way to resolve a disk-cache {@code sourceKey} back to the original remote URL —
     * see the long-form rationale on that method before considering any change here.
     */
    private final Map<String, CacheReplacementTarget> mDynamicSourceKeyMap = new HashMap<>();
    private final Map<String, CacheReplacementTarget> mProtectedSafeKeyMap = new HashMap<>();
    private final Map<String, Object> mSafeKeyWriteLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> mLastReplacementTimestampMap = new ConcurrentHashMap<>();

    public ImageCacheHookRegistrar(
            @NonNull XposedModule module,
            @NonNull HookInstallerSupport support,
            @NonNull ReplacementMapStore replacementStore
    ) {
        mModule = module;
        mSupport = support;
        mReplacementStore = replacementStore;
    }

    public void installImageHooks(@NonNull DexKitHookTargets dexKitTargets) {
        installGlideTokenHooks(dexKitTargets);
        installGlideDiskCacheHooks(dexKitTargets);
    }

    /**
     * Installs an interceptor on {@code com.miui.tsmclient.util.m1.a(String)}.
     *
     * <h3>DO NOT REMOVE — this is the only bridge between remote URLs and Glide cache tokens.</h3>
     *
     * <p><b>Why the name is misleading:</b> jadx labels the source as
     * {@code Md5FileNameGenerator.java}. That filename is shared with a deprecated Glide v3
     * helper, which makes it tempting to assume the hook is dead code. <em>It is not.</em>
     * The actual class is MiPay's own utility {@code com.miui.tsmclient.util.m1}, and Glide
     * v4 uses it as the cache-key transform on the hot path.
     *
     * <p><b>Verified call chain (jadx, host APK 9.21.0.001):</b>
     * <pre>
     *  remoteUrl  ──CustomGlideUrl──▶  GlideUrl.getCacheKey()
     *                                       │
     *                                       ▼
     *                             new m1().a(url)   ◀── this hook
     *                                       │  (md5 → base36 hash)
     *                                       ▼
     *                       SafeKeyGenerator.getSafeKey(Key)
     *                                       │
     *                                       ▼
     *                          safeKey = SHA-256(cacheKey)
     *                                       │
     *                                       ▼
     *                  /cache/image_manager_disk_cache/&lt;safeKey&gt;.0
     * </pre>
     * Concrete callers proven via jadx xref:
     * <ul>
     *   <li>{@code com.miui.tsmclient.util.h0} (CustomGlideUrl) — overrides
     *       {@code GlideUrl.getCacheKey()} and pipes the URL through {@code m1.a(...)}.</li>
     *   <li>{@code com.miui.tsmclient.ui.widget.SlideView.g(CardInfo)} — derives the
     *       {@code R.id.cardstack_url_tag} dedup token via {@code new m1().a(cardArt)} so it
     *       can detect when a card face URL changes.</li>
     * </ul>
     *
     * <p><b>Why we must hook it:</b> the {@code DiskLruCacheWrapper} hooks below see the
     * cache {@code Key} as a {@code toString()} blob containing
     * {@code sourceKey=&lt;base36&gt;}, which is the OUTPUT of {@code m1.a(...)}. Without the
     * inverse mapping recorded here ({@code base36 → remoteUrl → CacheReplacementTarget})
     * we cannot decide whether a given disk-cache lookup belongs to a card art we want to
     * replace. Empirical confirmation: keys observed at runtime have the shape
     * {@code ResourceCacheKey{sourceKey=wos5kmf6987y95dky4dw23yb,...}}, i.e. pure base36
     * hashes that only this hook can resolve back to URLs.
     *
     * <p><b>Why the result is not persisted to SharedPreferences:</b> {@code m1.a} is
     * deterministic for a given URL, so caching tokens across launches is technically
     * possible — but harmful in practice. The mapping we actually care about
     * ({@code sourceKey → CacheReplacementTarget}) depends on the user's current rules,
     * which already live in {@link ReplacementMapStore} and refresh every 5s from the
     * provider. Stale cross-process token caches would pin retired {@code assetId}s after a
     * rule change. Letting the hook re-fill {@link #mDynamicSourceKeyMap} on each run keeps
     * the rule edit → effect window bounded by Glide's own re-fetch cadence.
     */
    void installGlideTokenHooks(@NonNull DexKitHookTargets dexKitTargets) {
        try {
            Method generateToken = dexKitTargets.getGlideTokenGenerate();
            if (generateToken == null) {
                mModule.log(Log.WARN, TAG, "Glide token hook install skipped: DexKit did not resolve Md5FileNameGenerator.generate");
                return;
            }
            mSupport.prepareMethod(generateToken, "Md5FileNameGenerator.generate");
            mModule.hook(generateToken)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object input = chain.getArg(0);
                        Object result = chain.proceed();
                        if (input instanceof String && result instanceof String) {
                            rememberDynamicSourceKey((String) input, (String) result);
                        }
                        return result;
                    });
            mSupport.recordInstalledHook("Md5FileNameGenerator.generate", generateToken);
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "Glide token hook install failed: " + Log.getStackTraceString(throwable));
        }
    }

    void installGlideDiskCacheHooks(@NonNull DexKitHookTargets dexKitTargets) {
        Class<?> diskCacheWrapperClass = dexKitTargets.getGlideDiskCacheWrapperClass();
        Method getFromDiskCache = dexKitTargets.getGlideDiskCacheGet();
        Method putToDiskCache = dexKitTargets.getGlideDiskCachePut();
        if (diskCacheWrapperClass == null) {
            throw new IllegalStateException("DexKit did not resolve Glide disk cache wrapper class");
        }
        if (getFromDiskCache == null) {
            getFromDiskCache = HookReflectionUtils.findDiskCacheMethod(diskCacheWrapperClass, File.class, 1);
        }
        if (putToDiskCache == null) {
            putToDiskCache = HookReflectionUtils.findDiskCacheMethod(diskCacheWrapperClass, Void.TYPE, 2);
        }
        if (getFromDiskCache == null || putToDiskCache == null) {
            mModule.log(Log.WARN, TAG, "Glide disk cache candidates: " + HookReflectionUtils.describeMethods(diskCacheWrapperClass));
            throw new IllegalStateException("DexKit did not resolve Glide disk cache methods");
        }
        mModule.log(Log.INFO, TAG, "Glide disk cache hooks target: get="
                + getFromDiskCache.getDeclaringClass().getName()
                + '#'
                + getFromDiskCache.getName()
                + ", put="
                + putToDiskCache.getDeclaringClass().getName()
                + '#'
                + putToDiskCache.getName());
        installDiskCacheRemoveHook(diskCacheWrapperClass);

        Method finalGetFromDiskCache = getFromDiskCache;
        mSupport.prepareMethod(finalGetFromDiskCache, "DiskLruCacheWrapper.get");
        mModule.hook(finalGetFromDiskCache)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    CacheReplacementTarget replacementTarget = resolveCacheReplacementTarget(chain.getArg(0));
                    if (replacementTarget != null) {
                        File pinnedFile = ensureDiskCacheEntryReplaced(chain.getThisObject(), chain.getArg(0), replacementTarget);
                        return pinnedFile;
                    }
                    return chain.proceed();
                });
        mSupport.recordInstalledHook("DiskLruCacheWrapper.get", finalGetFromDiskCache);

        Method finalPutToDiskCache = putToDiskCache;
        mSupport.prepareMethod(finalPutToDiskCache, "DiskLruCacheWrapper.put");
        mModule.hook(finalPutToDiskCache)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    CacheReplacementTarget replacementTarget = resolveCacheReplacementTarget(chain.getArg(0));
                    if (replacementTarget != null) {
                        ensureDiskCacheEntryReplaced(chain.getThisObject(), chain.getArg(0), replacementTarget);
                        return null;
                    }
                    return chain.proceed();
                });
        mSupport.recordInstalledHook("DiskLruCacheWrapper.put", finalPutToDiskCache);
    }

    private void installDiskCacheRemoveHook(@NonNull Class<?> diskCacheWrapperClass) {
        try {
            Class<?> diskLruCacheClass = DiskLruCacheReflector.resolveDiskLruCacheClass(diskCacheWrapperClass);
            if (diskLruCacheClass == null) {
                mModule.log(Log.WARN, TAG, "Unable to resolve DiskLruCache backend class for removal hook");
                return;
            }
            Method removeMethod = HookReflectionUtils.findMethodBySignature(diskLruCacheClass, Boolean.TYPE, String.class);
            if (removeMethod == null) {
                mModule.log(Log.WARN, TAG, "Unable to resolve DiskLruCache remove method in " + diskLruCacheClass.getName());
                return;
            }
            mSupport.prepareMethod(removeMethod, "DiskLruCache.remove");
            mModule.hook(removeMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object key = chain.getArg(0);
                        if (key instanceof String && isProtectedSafeKey((String) key)) {
                            mModule.log(Log.INFO, TAG, "Glide disk cache remove blocked: safeKey=" + key);
                            return false;
                        }
                        return chain.proceed();
                    });
            mSupport.recordInstalledHook("DiskLruCache.remove", removeMethod);
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "DiskLruCache remove hook install failed: " + Log.getStackTraceString(throwable));
        }
    }

    private CacheReplacementTarget resolveCacheReplacementTarget(Object diskCacheKey) {
        String remoteUrl = GlideCacheKeyParser.extractRemoteUrl(diskCacheKey);
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            return null;
        }
        if (remoteUrl != null) {
            CacheReplacementTarget replacementTarget = mReplacementStore
                    .getOrLoad(context)
                    .get(GlideCacheKeyParser.normalizeRemoteUrl(remoteUrl));
            if (replacementTarget != null && !replacementTarget.assetId.isEmpty() && !replacementTarget.remoteUrl.isEmpty()) {
                return replacementTarget;
            }
        }
        String sourceKeyToken = GlideCacheKeyParser.extractSourceKeyToken(diskCacheKey);
        if (sourceKeyToken == null || sourceKeyToken.isEmpty()) {
            return null;
        }
        // Fallback path: the disk-cache key only exposed a base36 sourceKey blob, not the
        // raw URL. Look it up in the reverse map populated by the m1.a hook (see
        // installGlideTokenHooks). Without that hook this branch is dead and replacement
        // misses any card whose disk-cache Key.toString() format hides the URL.
        synchronized (mDynamicSourceKeyMap) {
            CacheReplacementTarget dynamicSourceKeyTarget = mDynamicSourceKeyMap.get(sourceKeyToken);
            if (dynamicSourceKeyTarget != null) {
                return dynamicSourceKeyTarget;
            }
        }
        return mReplacementStore.getOrLoad(context).get(sourceKeyToken);
    }

    private File ensureDiskCacheEntryReplaced(@NonNull Object diskCacheWrapper, Object diskCacheKey, @NonNull CacheReplacementTarget replacementTarget) throws Throwable {
        File mirrorFile = PinnedAssetMirror.ensure(replacementTarget.assetId, replacementTarget.assetUri);
        String safeKey = DiskLruCacheReflector.resolveSafeKey(diskCacheWrapper, diskCacheKey);
        if (safeKey == null || safeKey.isEmpty()) {
            mModule.log(Log.WARN, TAG, "Unable to resolve disk cache safe key for key=" + diskCacheKey);
            return mirrorFile;
        }
        // Serialize concurrent replace operations against the same safeKey so that one thread
        // never observes another thread's half-written .tmp dirty file. Glide is multi-threaded
        // and both the get/put hooks may race against each other for the same key.
        Object lock = mSafeKeyWriteLocks.computeIfAbsent(safeKey, key -> new Object());
        synchronized (lock) {
            File committedFile = DiskLruCacheReflector.resolveCommittedCacheFile(diskCacheWrapper, safeKey);
            long now = System.currentTimeMillis();
            Long lastReplacedAt = mLastReplacementTimestampMap.get(safeKey);
            // Skip rewrite if the committed file is already present, has the right size, and we
            // wrote it recently. This breaks the put -> get -> put cascade visible in logs where
            // the same safeKey was rewritten 5+ times within ~0.5 s.
            if (committedFile != null
                    && committedFile.exists()
                    && committedFile.length() == mirrorFile.length()
                    && lastReplacedAt != null
                    && now - lastReplacedAt < 30_000L) {
                synchronized (mProtectedSafeKeyMap) {
                    mProtectedSafeKeyMap.put(safeKey, replacementTarget);
                }
                return committedFile;
            }
            File cacheFile = DiskLruCacheReflector.overwrite(diskCacheWrapper, safeKey, mirrorFile);
            synchronized (mProtectedSafeKeyMap) {
                mProtectedSafeKeyMap.put(safeKey, replacementTarget);
            }
            mLastReplacementTimestampMap.put(safeKey, System.currentTimeMillis());
            mModule.log(Log.INFO, TAG, "Glide disk cache file replaced: safeKey="
                    + safeKey
                    + " remoteUrl="
                    + replacementTarget.remoteUrl
                    + " file="
                    + (cacheFile != null ? cacheFile.getAbsolutePath() : "null"));
            return cacheFile != null ? cacheFile : mirrorFile;
        }
    }

    private boolean isProtectedSafeKey(@NonNull String safeKey) {
        synchronized (mProtectedSafeKeyMap) {
            return mProtectedSafeKeyMap.containsKey(safeKey);
        }
    }

    /**
     * Records a {@code rawInput → sourceKeyToken} pair observed inside the {@code m1.a}
     * interceptor. Only stores entries whose {@code rawInput} matches a current replacement
     * target, so the map stays bounded by the active rule set rather than every URL Glide
     * happens to load. See {@link #installGlideTokenHooks} for the rationale.
     */
    private void rememberDynamicSourceKey(@NonNull String rawInput, @NonNull String sourceKeyToken) {
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            return;
        }
        CacheReplacementTarget replacementTarget = mReplacementStore.findForRawInput(context, rawInput);
        if (replacementTarget == null) {
            return;
        }
        synchronized (mDynamicSourceKeyMap) {
            mDynamicSourceKeyMap.put(sourceKeyToken, replacementTarget);
        }
    }
}
