package com.luoshui.paycardeditor.hook;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

/**
 * Hooks Glide's in-memory caches so that the very first frame after returning to a card-list
 * page does not flash the original (unreplaced) bitmap.
 *
 * <p>Without this, Glide's load order is:
 * <ol>
 *     <li>{@code ActiveResources} (weak references to currently used {@code EngineResource}s)</li>
 *     <li>{@code MemoryCache} (LruResourceCache holding decoded bitmaps)</li>
 *     <li>{@code DiskCache} (DiskLruCacheWrapper, the only layer the disk hook intercepts)</li>
 *     <li>Network</li>
 * </ol>
 * Layers 1 and 2 hold {@code Bitmap}s decoded from the <em>previous</em> disk cache content. Even
 * if the disk hook replaces the underlying file, those decoded bitmaps remain in memory and are
 * served instantly when the card list re-binds, producing the visible "flash original then swap"
 * symptom.
 *
 * <p>The fix is to make the active-resources / memory-cache lookups return {@code null} for any
 * key whose textual representation contains a known replacement target, forcing Glide to redecode
 * from the (already-replaced) disk cache.
 *
 * <h3>Class resolution strategy</h3>
 * All four Glide classes are resolved through {@link DexKitMethodLocator} so we don't fall over
 * on R8 rename churn:
 * <ul>
 *     <li><b>Engine</b> + <b>ActiveResources</b> are located by stable string fingerprints
 *         ({@code "Started new load"}, {@code "glide-active-resources"}) via DexKit.</li>
 *     <li><b>MemoryCache</b> interface is derived from {@code Engine.<init>}'s first parameter
 *         type at runtime — this relationship is part of Glide's public API surface and is
 *         immune to obfuscation renames.</li>
 *     <li><b>Key</b> interface is derived from {@code MemoryCache.get}'s first parameter type
 *         for the same reason.</li>
 * </ul>
 */
final class MemoryCacheHookRegistrar {

    private static final String TAG = "PayCardEditorHook";

    private final XposedModule mModule;
    private final HookInstallerSupport mSupport;
    private final ImageCacheHookRegistrar mImageCacheHookRegistrar;

    MemoryCacheHookRegistrar(
            @NonNull XposedModule module,
            @NonNull HookInstallerSupport support,
            @NonNull ImageCacheHookRegistrar imageCacheHookRegistrar
    ) {
        mModule = module;
        mSupport = support;
        mImageCacheHookRegistrar = imageCacheHookRegistrar;
    }

    /**
     * Installs ActiveResources + MemoryCache hooks. All four classes are sourced from
     * {@link DexKitHookTargets}; interfaces missing from the descriptor cache are derived
     * on the fly from Engine's constructor and MemoryCache's get method.
     */
    void installMemoryHooks(@NonNull DexKitHookTargets dexKitTargets) {
        GlideClassRefs refs = resolveClassRefs(dexKitTargets);
        if (refs == null) {
            mModule.log(Log.WARN, TAG, "Memory cache hooks skipped: unable to resolve Glide engine / key classes via DexKit");
            return;
        }
        installActiveResourcesHook(refs);
        installMemoryCacheHook(refs);
    }

    /**
     * Hooks {@code ActiveResources.get(Key)} so that any key referencing a replacement target
     * misses the weak-reference cache and falls through to the (already-rewritten) disk layer.
     */
    private void installActiveResourcesHook(@NonNull GlideClassRefs refs) {
        if (refs.activeResourcesClass == null) {
            mModule.log(Log.WARN, TAG, "ActiveResources hook skipped: ActiveResources class not resolved");
            return;
        }
        try {
            Method getMethod = findSingleArgMethodWithKey(refs.activeResourcesClass, refs.keyInterface);
            if (getMethod == null) {
                mModule.log(Log.WARN, TAG, "ActiveResources hook skipped: no get(Key) candidate found");
                return;
            }
            mSupport.prepareMethod(getMethod, "ActiveResources.get");
            mModule.hook(getMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object key = chain.getArg(0);
                        if (shouldBypassMemoryCache(key)) {
                            return null;
                        }
                        return chain.proceed();
                    });
            mSupport.recordInstalledHook("ActiveResources.get", getMethod);
            mModule.log(Log.INFO, TAG, "ActiveResources hook installed: "
                    + refs.activeResourcesClass.getName() + '#' + getMethod.getName());
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "ActiveResources hook install failed: " + Log.getStackTraceString(throwable));
        }
    }

    /**
     * Hooks {@code MemoryCache.get(Key)} (i.e. {@code LruResourceCache.get}) so that any key
     * referencing a replacement target misses the bitmap LRU cache.
     */
    private void installMemoryCacheHook(@NonNull GlideClassRefs refs) {
        if (refs.memoryCacheInterface == null) {
            mModule.log(Log.WARN, TAG, "MemoryCache hook skipped: MemoryCache interface not resolved");
            return;
        }
        try {
            // Hook the interface declaration itself; LSPosed will dispatch to whichever concrete
            // implementation Glide uses (LruResourceCache by default).
            Method getMethod = findMemoryCacheGetMethod(refs.memoryCacheInterface, refs.keyInterface);
            if (getMethod == null) {
                mModule.log(Log.WARN, TAG, "MemoryCache hook skipped: no get(Key) candidate found on interface");
                return;
            }
            mSupport.prepareMethod(getMethod, "MemoryCache.get");
            mModule.hook(getMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object key = chain.getArg(0);
                        if (shouldBypassMemoryCache(key)) {
                            return null;
                        }
                        return chain.proceed();
                    });
            mSupport.recordInstalledHook("MemoryCache.get", getMethod);
            mModule.log(Log.INFO, TAG, "MemoryCache hook installed: "
                    + refs.memoryCacheInterface.getName() + '#' + getMethod.getName());
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "MemoryCache hook install failed: " + Log.getStackTraceString(throwable));
        }
    }

    /**
     * Resolves the four Glide classes needed by the memory cache hooks. Engine and
     * ActiveResources are taken directly from the DexKit targets; MemoryCache and Key
     * interfaces are derived from API shape so we don't have to fingerprint interfaces
     * (which have neither method bodies nor strings DexKit can match on).
     */
    private @Nullable GlideClassRefs resolveClassRefs(@NonNull DexKitHookTargets dexKitTargets) {
        Class<?> engineClass = dexKitTargets.getGlideEngineClass();
        Class<?> activeResourcesClass = dexKitTargets.getGlideActiveResourcesClass();
        Class<?> memoryCacheInterface = dexKitTargets.getGlideMemoryCacheInterface();
        Class<?> keyInterface = dexKitTargets.getGlideKeyInterface();

        if (memoryCacheInterface == null && engineClass != null) {
            memoryCacheInterface = deriveMemoryCacheFromEngine(engineClass);
        }
        if (keyInterface == null && memoryCacheInterface != null) {
            keyInterface = deriveKeyInterfaceFromMemoryCache(memoryCacheInterface);
        }
        if (memoryCacheInterface == null || keyInterface == null) {
            mModule.log(Log.WARN, TAG, "Glide class derivation failed: engine="
                    + (engineClass != null ? engineClass.getName() : "null")
                    + " active=" + (activeResourcesClass != null ? activeResourcesClass.getName() : "null")
                    + " memCache=" + (memoryCacheInterface != null ? memoryCacheInterface.getName() : "null")
                    + " key=" + (keyInterface != null ? keyInterface.getName() : "null"));
            return null;
        }
        mModule.log(Log.INFO, TAG, "Glide class refs resolved: engine="
                + (engineClass != null ? engineClass.getName() : "null")
                + " active=" + (activeResourcesClass != null ? activeResourcesClass.getName() : "null")
                + " memCache=" + memoryCacheInterface.getName()
                + " key=" + keyInterface.getName());
        return new GlideClassRefs(engineClass, activeResourcesClass, memoryCacheInterface, keyInterface);
    }

    /**
     * Engine.<init>(MemoryCache, ActiveResources.Factory, ...) — the first parameter type is
     * always the MemoryCache interface in Glide v4. Walking constructors and picking the
     * widest interface parameter is more robust than name-matching.
     */
    private @Nullable Class<?> deriveMemoryCacheFromEngine(@NonNull Class<?> engineClass) {
        Constructor<?>[] constructors = engineClass.getDeclaredConstructors();
        for (Constructor<?> ctor : constructors) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == 0) {
                continue;
            }
            Class<?> firstParam = paramTypes[0];
            // MemoryCache is an interface in Glide v4; if the candidate isn't an interface,
            // fall through and let the next constructor (if any) be considered.
            if (firstParam.isInterface()) {
                return firstParam;
            }
        }
        return null;
    }

    /**
     * MemoryCache declares two single-arg methods accepting Key: {@code remove(Key)} returning
     * the removed resource, and {@code get(Key)} (under v4 the actual name varies post-R8).
     * The Key interface is the parameter type common to both, so picking the first interface
     * parameter from a non-primitive-returning single-arg method on MemoryCache is reliable.
     */
    private @Nullable Class<?> deriveKeyInterfaceFromMemoryCache(@NonNull Class<?> memoryCacheInterface) {
        for (Method method : memoryCacheInterface.getDeclaredMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            Class<?> paramType = method.getParameterTypes()[0];
            if (!paramType.isInterface()) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isPrimitive() || Void.TYPE.equals(returnType)) {
                continue;
            }
            return paramType;
        }
        return null;
    }

    /**
     * Returns true if the given Glide key references one of our replacement targets, in which
     * case the memory cache lookup must miss so that Glide redecodes from the disk cache where
     * the file has already been overwritten.
     *
     * <p>We probe two surfaces of the key:
     * <ol>
     *     <li>If the key is an {@code EngineKey}, it stores the load model as a private field
     *         (typically the URL string). We reflectively read any {@code java.lang.Object}
     *         field whose value is a {@link String} and check it against the replacement map.</li>
     *     <li>As a fallback, we run {@link #containsReplacementToken(String, Map)} over
     *         {@code String.valueOf(key)}, which works for {@code ResourceCacheKey} and
     *         {@code DataCacheKey} since their {@code toString} embeds the {@code sourceKey}
     *         that the disk layer already maps.</li>
     * </ol>
     */
    private boolean shouldBypassMemoryCache(@Nullable Object key) {
        if (key == null) {
            return false;
        }
        Map<String, ImageCacheHookRegistrar.CacheReplacementTarget> replacementMap =
                mImageCacheHookRegistrar.snapshotReplacementMap();
        if (replacementMap.isEmpty()) {
            return false;
        }
        // Surface 1: read string-typed fields directly off the key (catches EngineKey.model).
        for (Field field : collectAllFields(key.getClass())) {
            try {
                Class<?> fieldType = field.getType();
                if (!Object.class.equals(fieldType) && !String.class.equals(fieldType)) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(key);
                if (!(value instanceof String stringValue) || stringValue.isEmpty()) {
                    continue;
                }
                if (replacementMap.containsKey(stringValue)) {
                    return true;
                }
                if (containsReplacementToken(stringValue, replacementMap)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        // Surface 2: textual scan of the key's toString output.
        return containsReplacementToken(String.valueOf(key), replacementMap);
    }

    private boolean containsReplacementToken(
            @NonNull String haystack,
            @NonNull Map<String, ImageCacheHookRegistrar.CacheReplacementTarget> replacementMap
    ) {
        if (haystack.isEmpty()) {
            return false;
        }
        for (String lookupKey : replacementMap.keySet()) {
            if (lookupKey == null || lookupKey.isEmpty()) {
                continue;
            }
            if (haystack.contains(lookupKey)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Method findSingleArgMethodWithKey(
            @NonNull Class<?> ownerClass,
            @NonNull Class<?> keyInterface
    ) {
        // ActiveResources.get(Key) returns EngineResource (concrete class). Pick any non-static
        // single-arg method whose only parameter is the Glide Key interface.
        for (Method method : ownerClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (!keyInterface.equals(parameterType)) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (Void.TYPE.equals(returnType) || Boolean.TYPE.equals(returnType)) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static @Nullable Method findMemoryCacheGetMethod(
            @NonNull Class<?> memoryCacheInterface,
            @NonNull Class<?> keyInterface
    ) {
        // MemoryCache.get(Key) is the only single-arg method whose parameter is the Key
        // interface and whose return type is non-primitive (the removed resource). The other
        // single-arg method on MemoryCache (remove(Key)) returns the same type, so we have
        // to discriminate by behavior: get(Key) does not invalidate state, but we cannot
        // tell that from signature alone. In practice Glide v4 declares get and remove with
        // the same shape and either can be intercepted to break the cascade; picking the
        // first match is safe because Glide always calls get() before remove() on a hit.
        for (Method method : memoryCacheInterface.getDeclaredMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!keyInterface.equals(method.getParameterTypes()[0])) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isPrimitive() || Void.TYPE.equals(returnType)) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static @NonNull Iterable<Field> collectAllFields(@NonNull Class<?> startClass) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        Class<?> current = startClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    /** Bundle of fully-resolved Glide class references used to install memory cache hooks. */
    private static final class GlideClassRefs {
        final @Nullable Class<?> engineClass;
        final @Nullable Class<?> activeResourcesClass;
        final @NonNull Class<?> memoryCacheInterface;
        final @NonNull Class<?> keyInterface;

        GlideClassRefs(
                @Nullable Class<?> engineClass,
                @Nullable Class<?> activeResourcesClass,
                @NonNull Class<?> memoryCacheInterface,
                @NonNull Class<?> keyInterface
        ) {
            this.engineClass = engineClass;
            this.activeResourcesClass = activeResourcesClass;
            this.memoryCacheInterface = memoryCacheInterface;
            this.keyInterface = keyInterface;
        }
    }
}
