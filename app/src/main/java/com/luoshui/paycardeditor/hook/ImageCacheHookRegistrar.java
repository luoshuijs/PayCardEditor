package com.luoshui.paycardeditor.hook;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.core.HookEnvironment;
import com.luoshui.paycardeditor.model.CardSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

final class ImageCacheHookRegistrar {

    private static final String TAG = "PayCardEditorHook";
    private static final String LOCAL_ASSET_URI_PREFIX = "content://"
            + HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY
            + "/"
            + HookEnvironment.PATH_CARD_ASSETS
            + "/";

    private final XposedModule mModule;
    private final HookInstallerSupport mSupport;
    private final Map<String, CacheReplacementTarget> mDynamicSourceKeyMap = new HashMap<>();
    private final Map<String, CacheReplacementTarget> mProtectedSafeKeyMap = new HashMap<>();
    private final Map<String, Object> mSafeKeyWriteLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> mLastReplacementTimestampMap = new ConcurrentHashMap<>();
    private volatile long mCacheReplacementMapLoadedAt = 0L;
    private volatile Map<String, CacheReplacementTarget> mCacheReplacementMap = Collections.emptyMap();

    ImageCacheHookRegistrar(@NonNull XposedModule module, @NonNull HookInstallerSupport support) {
        mModule = module;
        mSupport = support;
    }

    void installImageHooks(@NonNull DexKitHookTargets dexKitTargets) {
        installGlideTokenHooks(dexKitTargets);
        installGlideDiskCacheHooks(dexKitTargets);
    }

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

    Class<?> resolveDiskLruCacheClass(@NonNull Class<?> diskCacheWrapperClass) {
        for (Field field : diskCacheWrapperClass.getDeclaredFields()) {
            if (hasDiskLruCacheApi(field.getType())) {
                return field.getType();
            }
        }
        for (Method method : diskCacheWrapperClass.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && hasDiskLruCacheApi(method.getReturnType())) {
                return method.getReturnType();
            }
        }
        return null;
    }

    private void installDiskCacheRemoveHook(@NonNull Class<?> diskCacheWrapperClass) {
        try {
            Class<?> diskLruCacheClass = resolveDiskLruCacheClass(diskCacheWrapperClass);
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

    private boolean hasDiskLruCacheApi(@NonNull Class<?> candidateClass) {
        return HookReflectionUtils.findMethodBySignature(candidateClass, Boolean.TYPE, String.class) != null
                && findSnapshotGetterMethod(candidateClass) != null
                && findEditorMethod(candidateClass) != null;
    }

    private CacheReplacementTarget resolveCacheReplacementTarget(Object diskCacheKey) {
        String remoteUrl = extractRemoteUrl(diskCacheKey);
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            return null;
        }
        if (remoteUrl != null) {
            CacheReplacementTarget replacementTarget = getCacheReplacementMap(context).get(normalizeRemoteUrl(remoteUrl));
            if (replacementTarget != null && !replacementTarget.assetId.isEmpty() && !replacementTarget.remoteUrl.isEmpty()) {
                return replacementTarget;
            }
        }
        String sourceKeyToken = extractSourceKeyToken(diskCacheKey);
        if (sourceKeyToken == null || sourceKeyToken.isEmpty()) {
            return null;
        }
        synchronized (mDynamicSourceKeyMap) {
            CacheReplacementTarget dynamicSourceKeyTarget = mDynamicSourceKeyMap.get(sourceKeyToken);
            if (dynamicSourceKeyTarget != null) {
                return dynamicSourceKeyTarget;
            }
        }
        return getCacheReplacementMap(context).get(sourceKeyToken);
    }

    private File ensureDiskCacheEntryReplaced(@NonNull Object diskCacheWrapper, Object diskCacheKey, @NonNull CacheReplacementTarget replacementTarget) throws Throwable {
        File mirrorFile = ensurePinnedAssetMirror(replacementTarget.assetId, replacementTarget.assetUri);
        String safeKey = resolveDiskCacheSafeKey(diskCacheWrapper, diskCacheKey);
        if (safeKey == null || safeKey.isEmpty()) {
            mModule.log(Log.WARN, TAG, "Unable to resolve disk cache safe key for key=" + diskCacheKey);
            return mirrorFile;
        }
        // Serialize concurrent replace operations against the same safeKey so that one thread
        // never observes another thread's half-written .tmp dirty file. Glide is multi-threaded
        // and both the get/put hooks may race against each other for the same key.
        Object lock = mSafeKeyWriteLocks.computeIfAbsent(safeKey, key -> new Object());
        synchronized (lock) {
            File committedFile = resolveCommittedCacheFile(diskCacheWrapper, safeKey);
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
            File cacheFile = overwriteDiskCacheFile(diskCacheWrapper, safeKey, mirrorFile);
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

    private String resolveDiskCacheSafeKey(@NonNull Object diskCacheWrapper, Object diskCacheKey) {
        if (diskCacheKey == null) {
            return null;
        }
        for (Field field : diskCacheWrapper.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(diskCacheWrapper);
                if (value == null) {
                    continue;
                }
                for (Method method : value.getClass().getDeclaredMethods()) {
                    if (!String.class.equals(method.getReturnType()) || method.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (!parameterType.isInstance(diskCacheKey) && !parameterType.isAssignableFrom(diskCacheKey.getClass())) {
                        continue;
                    }
                    method.setAccessible(true);
                    Object result = method.invoke(value, diskCacheKey);
                    if (result instanceof String && !((String) result).isEmpty()) {
                        return (String) result;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private File overwriteDiskCacheFile(@NonNull Object diskCacheWrapper, @NonNull String safeKey, @NonNull File sourceFile) throws Throwable {
        Object backend = resolveDiskCacheBackend(diskCacheWrapper);
        File directFile = resolveDiskCacheDataFile(diskCacheWrapper, safeKey);
        if (backend != null) {
            Object editor = openDiskCacheEditor(backend, safeKey);
            if (editor != null) {
                boolean committed = false;
                try {
                    File dirtyFile = getEditorFile(editor);
                    if (dirtyFile == null) {
                        throw new IllegalStateException("Disk cache editor returned null file for safeKey=" + safeKey);
                    }
                    copyFile(sourceFile, dirtyFile);
                    commitDiskCacheEditor(editor);
                    committed = true;
                } finally {
                    if (!committed) {
                        abortDiskCacheEditor(editor);
                    }
                }
            } else if (directFile != null) {
                copyFile(sourceFile, directFile);
            }
        } else if (directFile != null) {
            copyFile(sourceFile, directFile);
        }
        // After commit (or direct write) the only file Glide must ever observe is the canonical
        // ".0" entry. Returning the dirty ".tmp" file is unsafe because DiskLruCache renames it
        // away during commit, so other threads decoding from the returned File may hit a missing
        // / partial file and trigger a re-fetch (visible in logs as cascading replacements).
        return resolveCommittedCacheFile(diskCacheWrapper, safeKey, backend, directFile);
    }

    private Object resolveDiskCacheBackend(@NonNull Object diskCacheWrapper) throws Throwable {
        for (Field field : diskCacheWrapper.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(diskCacheWrapper);
            if (value != null && hasDiskLruCacheApi(value.getClass())) {
                return value;
            }
        }
        for (Method method : diskCacheWrapper.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || !hasDiskLruCacheApi(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            Object value = method.invoke(diskCacheWrapper);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private File resolveDiskCacheDataFile(@NonNull Object diskCacheWrapper, @NonNull String safeKey) throws IllegalAccessException {
        for (Field field : diskCacheWrapper.getClass().getDeclaredFields()) {
            if (!File.class.equals(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            File directory = (File) field.get(diskCacheWrapper);
            if (directory != null) {
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                return new File(directory, safeKey + ".0");
            }
        }
        return null;
    }

    private Object openDiskCacheEditor(@NonNull Object diskCacheBackend, @NonNull String safeKey) throws Throwable {
        Method editorMethod = findEditorMethod(diskCacheBackend.getClass());
        if (editorMethod == null) {
            return null;
        }
        editorMethod.setAccessible(true);
        return editorMethod.invoke(diskCacheBackend, safeKey);
    }

    private File getDiskCacheSnapshotFile(@NonNull Object diskCacheBackend, @NonNull String safeKey) throws Throwable {
        Method snapshotMethod = findSnapshotGetterMethod(diskCacheBackend.getClass());
        if (snapshotMethod == null) {
            return null;
        }
        snapshotMethod.setAccessible(true);
        Object snapshot = snapshotMethod.invoke(diskCacheBackend, safeKey);
        if (snapshot == null) {
            return null;
        }
        return getSnapshotFile(snapshot);
    }

    private Method findSnapshotGetterMethod(@NonNull Class<?> diskCacheBackendClass) {
        for (Method method : diskCacheBackendClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 1 || !String.class.equals(method.getParameterTypes()[0])) {
                continue;
            }
            if (HookReflectionUtils.findMethodBySignature(method.getReturnType(), File.class, Integer.TYPE) != null) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private Method findEditorMethod(@NonNull Class<?> diskCacheBackendClass) {
        for (Method method : diskCacheBackendClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 1 || !String.class.equals(method.getParameterTypes()[0])) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (Void.TYPE.equals(returnType) || Boolean.TYPE.equals(returnType)) {
                continue;
            }
            if (HookReflectionUtils.findMethodBySignature(returnType, File.class, Integer.TYPE) != null
                    && HookReflectionUtils.findNoArgMethod(returnType, Void.TYPE, "e") != null
                    && HookReflectionUtils.findNoArgMethod(returnType, Void.TYPE, "b") != null) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private File getEditorFile(@NonNull Object editor) throws Throwable {
        Method fileMethod = HookReflectionUtils.findMethodBySignature(editor.getClass(), File.class, Integer.TYPE);
        if (fileMethod == null) {
            return null;
        }
        fileMethod.setAccessible(true);
        Object file = fileMethod.invoke(editor, 0);
        return file instanceof File ? (File) file : null;
    }

    private File getSnapshotFile(@NonNull Object snapshot) throws Throwable {
        Method fileMethod = HookReflectionUtils.findMethodBySignature(snapshot.getClass(), File.class, Integer.TYPE);
        if (fileMethod == null) {
            return null;
        }
        fileMethod.setAccessible(true);
        Object file = fileMethod.invoke(snapshot, 0);
        return file instanceof File ? (File) file : null;
    }

    private void commitDiskCacheEditor(@NonNull Object editor) throws Throwable {
        Method commitMethod = HookReflectionUtils.findNoArgMethod(editor.getClass(), Void.TYPE, "e");
        Method cleanupMethod = HookReflectionUtils.findNoArgMethod(editor.getClass(), Void.TYPE, "b");
        if (commitMethod == null || cleanupMethod == null) {
            throw new IllegalStateException("Unable to resolve disk cache editor commit methods");
        }
        commitMethod.setAccessible(true);
        cleanupMethod.setAccessible(true);
        commitMethod.invoke(editor);
        cleanupMethod.invoke(editor);
    }

    private void abortDiskCacheEditor(@NonNull Object editor) {
        try {
            Method cleanupMethod = HookReflectionUtils.findNoArgMethod(editor.getClass(), Void.TYPE, "b");
            if (cleanupMethod != null) {
                cleanupMethod.setAccessible(true);
                cleanupMethod.invoke(editor);
            }
        } catch (Throwable ignored) {
        }
    }

    private void copyFile(@NonNull File sourceFile, @NonNull File targetFile) throws Throwable {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("Unable to create cache directory: " + parent.getAbsolutePath());
        }
        try (InputStream inputStream = new java.io.FileInputStream(sourceFile);
             OutputStream outputStream = new FileOutputStream(targetFile, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) >= 0) {
                if (count > 0) {
                    outputStream.write(buffer, 0, count);
                }
            }
            outputStream.flush();
        }
    }

    /**
     * Resolves the canonical committed cache file (`safeKey + ".0"`) without ever returning a
     * dirty `.tmp` editor file. Falls back to {@link #resolveDiskCacheDataFile} for legacy
     * Glide builds where the snapshot getter is not exposed.
     */
    private File resolveCommittedCacheFile(@NonNull Object diskCacheWrapper, @NonNull String safeKey) throws Throwable {
        Object backend = resolveDiskCacheBackend(diskCacheWrapper);
        File directFile = resolveDiskCacheDataFile(diskCacheWrapper, safeKey);
        return resolveCommittedCacheFile(diskCacheWrapper, safeKey, backend, directFile);
    }

    private File resolveCommittedCacheFile(
            @NonNull Object diskCacheWrapper,
            @NonNull String safeKey,
            Object backend,
            File directFile
    ) throws Throwable {
        if (backend != null) {
            try {
                File snapshotFile = getDiskCacheSnapshotFile(backend, safeKey);
                if (snapshotFile != null && snapshotFile.exists()) {
                    return snapshotFile;
                }
            } catch (Throwable ignored) {
            }
        }
        if (directFile != null && directFile.exists()) {
            return directFile;
        }
        return directFile;
    }

    /**
     * Exposes the current replacement map to other hook registrars (e.g. memory cache) without
     * forcing them to plumb a {@link Context} themselves. Returns the cached map (possibly
     * empty) when no host context is yet available so that hot-path callers stay non-blocking.
     */
    @NonNull
    Map<String, CacheReplacementTarget> snapshotReplacementMap() {
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            return mCacheReplacementMap;
        }
        return getCacheReplacementMap(context);
    }

    private Map<String, CacheReplacementTarget> getCacheReplacementMap(@NonNull Context context) {
        long now = System.currentTimeMillis();
        Map<String, CacheReplacementTarget> cached = mCacheReplacementMap;
        if (now - mCacheReplacementMapLoadedAt < 5000L) {
            return cached;
        }
        synchronized (this) {
            if (now - mCacheReplacementMapLoadedAt < 5000L) {
                return mCacheReplacementMap;
            }
            Map<String, CacheReplacementTarget> loaded = loadCacheReplacementMap(context);
            mCacheReplacementMap = loaded;
            mCacheReplacementMapLoadedAt = now;
            return loaded;
        }
    }

    private Map<String, CacheReplacementTarget> loadCacheReplacementMap(@NonNull Context context) {
        try {
            Bundle ruleBundle = context.getContentResolver().call(
                    Uri.parse("content://" + HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY),
                    HookEnvironment.METHOD_GET_BANK_RULES,
                    null,
                    Bundle.EMPTY
            );
            Bundle snapshotBundle = context.getContentResolver().call(
                    Uri.parse("content://" + HookEnvironment.SNAPSHOT_PROVIDER_AUTHORITY),
                    HookEnvironment.METHOD_GET_CARD_SNAPSHOTS,
                    null,
                    Bundle.EMPTY
            );
            String rulesJson = ruleBundle != null ? ruleBundle.getString(HookEnvironment.EXTRA_RULES_JSON, "[]") : "[]";
            String snapshotsJson = snapshotBundle != null ? snapshotBundle.getString(HookEnvironment.EXTRA_SNAPSHOTS_JSON, "[]") : "[]";
            List<BankCardRule> rules = BankCardRule.Companion.parseList(rulesJson == null ? "[]" : rulesJson);
            JSONArray snapshotsArray = new JSONArray(snapshotsJson == null || snapshotsJson.isBlank() ? "[]" : snapshotsJson);
            Map<String, CacheReplacementTarget> result = new HashMap<>();
            for (int index = 0; index < snapshotsArray.length(); index++) {
                JSONObject item = snapshotsArray.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                CardSnapshot snapshot = CardSnapshot.Companion.fromJson(item);
                if (!snapshot.getSupportsCustomCardArt()) {
                    continue;
                }
                BankCardRule matchedRule = null;
                for (BankCardRule rule : rules) {
                    if (rule.matches(snapshot)) {
                        matchedRule = rule;
                        break;
                    }
                }
                if (matchedRule == null || matchedRule.getReplaceCardArt().isBlank()) {
                    continue;
                }
                String assetUriText = matchedRule.getReplaceCardArt();
                if (!isLocalAssetUri(assetUriText)) {
                    continue;
                }
                Uri assetUri = Uri.parse(assetUriText);
                List<String> pathSegments = assetUri.getPathSegments();
                if (pathSegments.size() != 2 || !HookEnvironment.PATH_CARD_ASSETS.equals(pathSegments.get(0))) {
                    continue;
                }
                String assetId = pathSegments.get(1);
                if (assetId == null || assetId.isEmpty()) {
                    continue;
                }
                String remoteUrl = firstNonBlankReplacementKey(snapshot);
                if (remoteUrl.isEmpty()) {
                    continue;
                }
                CacheReplacementTarget replacementTarget = new CacheReplacementTarget(remoteUrl, assetId, assetUri);
                addReplacementLookupKeys(result, snapshot, replacementTarget);
            }
            mModule.log(Log.INFO, TAG, "Loaded image cache replacement map entries=" + result.size());
            return result;
        } catch (Throwable throwable) {
            mModule.log(Log.WARN, TAG, "load cache replacement map failed: " + Log.getStackTraceString(throwable));
            return Collections.emptyMap();
        }
    }

    private void addReplacementLookupKeys(
            @NonNull Map<String, CacheReplacementTarget> result,
            @NonNull CardSnapshot snapshot,
            @NonNull CacheReplacementTarget replacementTarget
    ) {
        LinkedHashSet<String> lookupKeys = new LinkedHashSet<>();
        addLookupKeyVariants(lookupKeys, snapshot.getCardArt());
        addLookupKeyVariants(lookupKeys, snapshot.getPersonalCardFace());
        addLookupKeyVariants(lookupKeys, snapshot.getIssuedListBgHd());
        addLookupKeyVariants(lookupKeys, snapshot.getIssuedListBg());
        addLookupKeyVariants(lookupKeys, snapshot.getLogo());
        addLookupKeyVariants(lookupKeys, snapshot.getLogoWithName());
        addLookupKeyVariants(lookupKeys, snapshot.getProductId());
        addLookupKeyVariants(lookupKeys, snapshot.getCid());
        addLookupKeyVariants(lookupKeys, snapshot.getVcUid());
        addLookupKeyVariants(lookupKeys, snapshot.getAid());
        for (String lookupKey : lookupKeys) {
            result.put(lookupKey, replacementTarget);
        }
    }

    private void rememberDynamicSourceKey(@NonNull String rawInput, @NonNull String sourceKeyToken) {
        Context context = HookProcessContext.INSTANCE.resolve();
        if (context == null) {
            return;
        }
        CacheReplacementTarget replacementTarget = findReplacementTargetForRawInput(context, rawInput);
        if (replacementTarget == null) {
            return;
        }
        synchronized (mDynamicSourceKeyMap) {
            mDynamicSourceKeyMap.put(sourceKeyToken, replacementTarget);
        }
    }

    private CacheReplacementTarget findReplacementTargetForRawInput(@NonNull Context context, @NonNull String rawInput) {
        String normalizedInput = normalizeRemoteUrl(rawInput);
        Map<String, CacheReplacementTarget> replacementMap = getCacheReplacementMap(context);
        CacheReplacementTarget direct = replacementMap.get(normalizedInput);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, CacheReplacementTarget> entry : replacementMap.entrySet()) {
            String lookupKey = entry.getKey();
            if (lookupKey == null || lookupKey.isEmpty()) {
                continue;
            }
            if (normalizedInput.equals(lookupKey) || normalizedInput.contains(lookupKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String firstNonBlankReplacementKey(@NonNull CardSnapshot snapshot) {
        LinkedHashSet<String> lookupKeys = new LinkedHashSet<>();
        addLookupKeyVariants(lookupKeys, snapshot.getCardArt());
        addLookupKeyVariants(lookupKeys, snapshot.getPersonalCardFace());
        addLookupKeyVariants(lookupKeys, snapshot.getIssuedListBgHd());
        addLookupKeyVariants(lookupKeys, snapshot.getIssuedListBg());
        addLookupKeyVariants(lookupKeys, snapshot.getLogo());
        addLookupKeyVariants(lookupKeys, snapshot.getLogoWithName());
        addLookupKeyVariants(lookupKeys, snapshot.getProductId());
        addLookupKeyVariants(lookupKeys, snapshot.getCid());
        addLookupKeyVariants(lookupKeys, snapshot.getVcUid());
        addLookupKeyVariants(lookupKeys, snapshot.getAid());
        return lookupKeys.isEmpty() ? "" : lookupKeys.getFirst();
    }

    private void addLookupKeyVariants(@NonNull Set<String> lookupKeys, String rawValue) {
        String normalized = normalizeRemoteUrl(rawValue);
        if (normalized.isEmpty() || isLocalAssetUri(normalized)) {
            return;
        }
        lookupKeys.add(normalized);
        if (normalized.startsWith("http://")) {
            lookupKeys.add("https://" + normalized.substring("http://".length()));
        } else if (normalized.startsWith("https://")) {
            lookupKeys.add("http://" + normalized.substring("https://".length()));
        }
        String lastSegment = extractLastPathSegment(normalized);
        if (!lastSegment.isEmpty()) {
            lookupKeys.add(lastSegment);
            int extensionIndex = lastSegment.lastIndexOf('.');
            if (extensionIndex > 0) {
                lookupKeys.add(lastSegment.substring(0, extensionIndex));
            }
        }
    }

    private String extractLastPathSegment(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            Uri uri = Uri.parse(value);
            String segment = uri.getLastPathSegment();
            if (segment != null && !segment.isEmpty()) {
                return segment;
            }
        } catch (Throwable ignored) {
        }
        int queryIndex = value.indexOf('?');
        String trimmed = queryIndex >= 0 ? value.substring(0, queryIndex) : value;
        int slashIndex = trimmed.lastIndexOf('/');
        return slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
    }

    private String extractRemoteUrl(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int httpsStart = text.indexOf("https://");
        int httpStart = text.indexOf("http://");
        int start = httpsStart >= 0 && (httpStart < 0 || httpsStart < httpStart) ? httpsStart : httpStart;
        if (start < 0) {
            return null;
        }
        int end = start;
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (Character.isWhitespace(ch) || ch == ',' || ch == '}' || ch == ')' || ch == ']') {
                break;
            }
            if (ch == '\'' || ch == '"') {
                break;
            }
            if (ch == '{' || ch == '[' || ch == '(') {
                end++;
                continue;
            }
            if (ch == '\\') {
                break;
            }
            if (Character.isISOControl(ch)) {
                break;
            }
            if (ch == '>' || ch == '<' || ch == ';' || ch == '#') {
                break;
            }
            if (ch == '&' && end + 1 < text.length() && text.charAt(end + 1) == '#') {
                break;
            }
            if (Character.isLetterOrDigit(ch) || ":/?&=._-%~+@!$,*".indexOf(ch) >= 0) {
                end++;
                continue;
            }
            break;
        }
        String candidate = text.substring(start, end);
        return normalizeRemoteUrl(candidate);
    }

    private String extractSourceKeyToken(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int start = text.indexOf("sourceKey=");
        if (start < 0) {
            return null;
        }
        start += "sourceKey=".length();
        int end = start;
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (ch == ',' || ch == '}') {
                break;
            }
            end++;
        }
        return normalizeCacheKeyToken(text.substring(start, end));
    }

    private String normalizeCacheKeyToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeRemoteUrl(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (!normalized.isEmpty()) {
            char last = normalized.charAt(normalized.length() - 1);
            if (last == ',' || last == ')' || last == ']' || last == '}' || last == '"' || last == '\'') {
                normalized = normalized.substring(0, normalized.length() - 1);
                continue;
            }
            break;
        }
        return normalized;
    }

    private File ensurePinnedAssetMirror(@NonNull String assetId, @NonNull Uri uri) throws Throwable {
        Context context = HookProcessContext.INSTANCE.resolve();
        File mirrorDirectory = new File(context.getCacheDir(), "paycardeditor_pinned_assets");
        if (!mirrorDirectory.exists() && !mirrorDirectory.mkdirs() && !mirrorDirectory.isDirectory()) {
            throw new IllegalStateException("Unable to create pinned asset mirror directory: " + mirrorDirectory.getAbsolutePath());
        }
        File mirrorFile = new File(mirrorDirectory, assetId + ".png");
        File tempFile = new File(mirrorDirectory, assetId + ".tmp");
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Provider returned null stream for " + uri);
            }
            try (OutputStream outputStream = new FileOutputStream(tempFile, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = inputStream.read(buffer)) >= 0) {
                    if (count > 0) {
                        outputStream.write(buffer, 0, count);
                    }
                }
                outputStream.flush();
            }
        }
        if (mirrorFile.exists() && !mirrorFile.delete()) {
            throw new IllegalStateException("Unable to replace pinned asset mirror: " + mirrorFile.getAbsolutePath());
        }
        if (!tempFile.renameTo(mirrorFile)) {
            throw new IllegalStateException("Unable to finalize pinned asset mirror: " + mirrorFile.getAbsolutePath());
        }
        return mirrorFile;
    }

    private boolean isLocalAssetUri(@NonNull String value) {
        return value.startsWith(LOCAL_ASSET_URI_PREFIX);
    }

    static final class CacheReplacementTarget {
        final String remoteUrl;
        final String assetId;
        final Uri assetUri;

        CacheReplacementTarget(@NonNull String remoteUrl, @NonNull String assetId, @NonNull Uri assetUri) {
            this.remoteUrl = remoteUrl;
            this.assetId = assetId;
            this.assetUri = assetUri;
        }
    }
}
