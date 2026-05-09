package com.luoshui.paycardeditor.hook;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 */
final class MemoryCacheHookRegistrar {

    private static final String TAG = "PayCardEditorHook";

    private static final String GLIDE_ACTIVE_RESOURCES_CLASS = "com.bumptech.glide.load.engine.a";
    private static final String GLIDE_LRU_RESOURCE_CACHE_CLASS = "p210n3.g";
    private static final String GLIDE_LRU_RESOURCE_CACHE_CLASS_FALLBACK = "n3.g";
    private static final String GLIDE_MEMORY_CACHE_INTERFACE = "p210n3.h";
    private static final String GLIDE_MEMORY_CACHE_INTERFACE_FALLBACK = "n3.h";
    private static final String GLIDE_KEY_INTERFACE = "p171k3.f";
    private static final String GLIDE_KEY_INTERFACE_FALLBACK = "k3.f";

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

    void installMemoryHooks(@NonNull ClassLoader classLoader) {
        installActiveResourcesHook(classLoader);
        installMemoryCacheHook(classLoader);
    }

    /**
     * Hooks {@code ActiveResources.get(Key)} so that any key referencing a replacement target
     * misses the weak-reference cache and falls through to the (already-rewritten) disk layer.
     */
    private void installActiveResourcesHook(@NonNull ClassLoader classLoader) {
        try {
            Class<?> activeResourcesClass = loadClass(classLoader, GLIDE_ACTIVE_RESOURCES_CLASS);
            Class<?> keyInterface = loadKeyInterface(classLoader);
            if (activeResourcesClass == null || keyInterface == null) {
                mModule.log(Log.WARN, TAG, "ActiveResources hook skipped: classes not resolved");
                return;
            }
            Method getMethod = findSingleArgMethodWithKey(activeResourcesClass, keyInterface);
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
                    + activeResourcesClass.getName() + '#' + getMethod.getName());
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "ActiveResources hook install failed: " + Log.getStackTraceString(throwable));
        }
    }

    /**
     * Hooks {@code MemoryCache.get(Key)} (i.e. {@code LruResourceCache.get}) so that any key
     * referencing a replacement target misses the bitmap LRU cache.
     */
    private void installMemoryCacheHook(@NonNull ClassLoader classLoader) {
        try {
            Class<?> memoryCacheImplClass = loadClass(classLoader, GLIDE_LRU_RESOURCE_CACHE_CLASS);
            if (memoryCacheImplClass == null) {
                memoryCacheImplClass = loadClass(classLoader, GLIDE_LRU_RESOURCE_CACHE_CLASS_FALLBACK);
            }
            Class<?> memoryCacheInterface = loadClass(classLoader, GLIDE_MEMORY_CACHE_INTERFACE);
            if (memoryCacheInterface == null) {
                memoryCacheInterface = loadClass(classLoader, GLIDE_MEMORY_CACHE_INTERFACE_FALLBACK);
            }
            Class<?> keyInterface = loadKeyInterface(classLoader);
            if (memoryCacheInterface == null || keyInterface == null) {
                mModule.log(Log.WARN, TAG, "MemoryCache hook skipped: interfaces not resolved");
                return;
            }
            // Hook the interface declaration itself; LSPosed will dispatch to whichever concrete
            // implementation Glide uses (LruResourceCache by default).
            Method getMethod = findMemoryCacheGetMethod(memoryCacheInterface);
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
                    + memoryCacheInterface.getName() + '#' + getMethod.getName()
                    + " impl=" + (memoryCacheImplClass != null ? memoryCacheImplClass.getName() : "?"));
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "MemoryCache hook install failed: " + Log.getStackTraceString(throwable));
        }
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
     *     <li>As a fallback, we run {@link #containsReplacementToken(String)} over
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

    private static @Nullable Class<?> loadClass(@NonNull ClassLoader classLoader, @NonNull String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static @Nullable Class<?> loadKeyInterface(@NonNull ClassLoader classLoader) {
        Class<?> keyInterface = loadClass(classLoader, GLIDE_KEY_INTERFACE);
        if (keyInterface == null) {
            keyInterface = loadClass(classLoader, GLIDE_KEY_INTERFACE_FALLBACK);
        }
        return keyInterface;
    }

    private static @Nullable Method findSingleArgMethodWithKey(@NonNull Class<?> ownerClass, @NonNull Class<?> keyInterface) {
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

    private static @Nullable Method findMemoryCacheGetMethod(@NonNull Class<?> memoryCacheInterface) {
        // MemoryCache.get(Key) is the only single-arg method that returns a non-primitive type
        // and is not the Resource removal callback.
        for (Method method : memoryCacheInterface.getDeclaredMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (Void.TYPE.equals(returnType) || returnType.isPrimitive()) {
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
}
