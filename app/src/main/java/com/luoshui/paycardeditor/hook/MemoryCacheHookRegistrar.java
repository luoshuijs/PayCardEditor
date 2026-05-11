package com.luoshui.paycardeditor.hook;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

/**
 * Hooks Glide's in-memory cache lookup so that the very first frame after returning to a
 * card-list page does not flash the original (unreplaced) bitmap.
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
 * <h3>Why we hook Engine.loadFromCache instead of MemoryCache.get / ActiveResources.get</h3>
 * Glide's Engine class funnels both lookups through one private method (jadx-recovered shape:
 * {@code private EngineResource loadFromCache(EngineKey, boolean, long)}) that internally calls
 * {@code loadFromActiveResources} then {@code loadFromMemoryCache} and returns the first hit.
 * Hooking that single entry point is strictly stronger than hooking the two underlying caches
 * for three reasons:
 * <ul>
 *     <li><b>One hook covers both caches.</b> Engine is the only caller of either lookup, so
 *         returning {@code null} here makes Glide fall through to the disk layer where the
 *         file has already been overwritten.</li>
 *     <li><b>No abstract method to dodge.</b> {@code MemoryCache} is an interface, and LSPosed
 *         refuses to hook abstract members. Hooking the concrete implementation
 *         ({@code LruResourceCache}) would require identifying it through R8 names. Engine's
 *         {@code loadFromCache} is a private concrete method on a class we already locate
 *         via DexKit's {@code "Started new load"} string fingerprint.</li>
 *     <li><b>No interface derivation acrobatics.</b> We don't need to chase down
 *         {@code MemoryCache} or {@code Key} interfaces — the method's parameter signature
 *         {@code (Object, boolean, long)} is unique inside Engine and discriminates by shape
 *         alone.</li>
 * </ul>
 */
final class MemoryCacheHookRegistrar {

    private static final String TAG = "PayCardEditorHook";

    private final XposedModule mModule;
    private final HookInstallerSupport mSupport;
    private final ReplacementMapStore mReplacementStore;

    MemoryCacheHookRegistrar(
            @NonNull XposedModule module,
            @NonNull HookInstallerSupport support,
            @NonNull ReplacementMapStore replacementStore
    ) {
        mModule = module;
        mSupport = support;
        mReplacementStore = replacementStore;
    }

    /**
     * Installs the Engine memory-lookup hook. Engine class comes from
     * {@link DexKitHookTargets#getGlideEngineClass()} (fingerprinted by the string
     * {@code "Started new load"}); the method to hook is selected by shape rather than name.
     */
    void installMemoryHooks(@NonNull DexKitHookTargets dexKitTargets) {
        Class<?> engineClass = dexKitTargets.getGlideEngineClass();
        if (engineClass == null) {
            mModule.log(Log.WARN, TAG, "Engine memory lookup hook skipped: Glide Engine class not resolved by DexKit");
            return;
        }
        Method loadFromCacheMethod = findLoadFromCacheMethod(engineClass);
        if (loadFromCacheMethod == null) {
            mModule.log(Log.WARN, TAG, "Engine memory lookup hook skipped: no (Key, boolean, long) candidate in " + engineClass.getName());
            return;
        }
        try {
            mSupport.prepareMethod(loadFromCacheMethod, "Engine.loadFromCache");
            mModule.hook(loadFromCacheMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object key = chain.getArg(0);
                        if (shouldBypassMemoryCache(key)) {
                            // Returning null tells Engine the lookup missed; the caller then
                            // falls through to the disk-cache layer (already rewritten by
                            // ImageCacheHookRegistrar) and finally redecodes a fresh bitmap.
                            return null;
                        }
                        return chain.proceed();
                    });
            mSupport.recordInstalledHook("Engine.loadFromCache", loadFromCacheMethod);
            mModule.log(Log.INFO, TAG, "Engine memory lookup hook installed: "
                    + engineClass.getName() + '#' + loadFromCacheMethod.getName()
                    + describeSignature(loadFromCacheMethod));
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "Engine memory lookup hook install failed: " + Log.getStackTraceString(throwable));
        }
    }

    /**
     * Locates Glide's {@code loadFromCache(EngineKey, boolean, long)} on the Engine class by
     * shape: a private, non-static, non-primitive-returning method taking exactly three
     * parameters where param[1] is {@code boolean} and param[2] is {@code long}. The first
     * parameter type (EngineKey) is unique within Engine and doesn't need to be named
     * because no other private method on Engine shares this signature pattern in Glide v4.
     *
     * <p>Package-private so {@link HookDebugReporter} can surface the resolved method on the
     * troubleshoot page without duplicating the shape rules.
     */
    static @Nullable Method findLoadFromCacheMethod(@NonNull Class<?> engineClass) {
        Method best = null;
        for (Method method : engineClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 3) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes[1] != Boolean.TYPE) {
                continue;
            }
            if (paramTypes[2] != Long.TYPE) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isPrimitive() || returnType == Void.TYPE) {
                continue;
            }
            if (best != null) {
                // Multiple candidates would mean Glide's internal shape has shifted; bail out
                // rather than guess which one is the cache-lookup entry point.
                return null;
            }
            method.setAccessible(true);
            best = method;
        }
        return best;
    }

    /**
     * Returns true if the given Glide EngineKey references one of our replacement targets, in
     * which case the memory-cache lookup must miss so that Glide redecodes from the disk
     * cache where the file has already been overwritten.
     *
     * <p>We probe two surfaces of the key:
     * <ol>
     *     <li>The EngineKey stores the load model as a private {@code Object} field
     *         (typically the URL string). We reflectively read any {@code Object}-typed
     *         field whose value is a {@link String} and check it against the replacement
     *         map's lookup keys.</li>
     *     <li>As a fallback we run {@link #containsReplacementToken(String, Map)} over
     *         {@code String.valueOf(key)}, which still works on the inner cache keys
     *         (ResourceCacheKey / DataCacheKey) whose {@code toString} embeds a
     *         {@code sourceKey} that the disk hook already maps.</li>
     * </ol>
     */
    private boolean shouldBypassMemoryCache(@Nullable Object key) {
        if (key == null) {
            return false;
        }
        Map<String, CacheReplacementTarget> replacementMap = mReplacementStore.snapshot();
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
            @NonNull Map<String, CacheReplacementTarget> replacementMap
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

    private static @NonNull String describeSignature(@NonNull Method method) {
        StringBuilder builder = new StringBuilder("(");
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(params[i].getSimpleName());
        }
        builder.append(") -> ").append(method.getReturnType().getSimpleName());
        return builder.toString();
    }
}
