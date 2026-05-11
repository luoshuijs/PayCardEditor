package com.luoshui.paycardeditor.hook.image;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.luoshui.paycardeditor.hook.HookReflectionUtils;
import com.luoshui.paycardeditor.hook.dexkit.DexKitMethodLocator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Adapter layer over Glide's obfuscated {@code DiskLruCacheWrapper} and the underlying
 * {@code com.bumptech.glide.disklrucache.DiskLruCache} (R8 names {@code n3.e} and
 * {@code j3.a} respectively at the time of writing).
 *
 * <p>Every method here treats the disk cache instances as opaque {@link Object}s and
 * navigates them by reflection because:
 * <ul>
 *     <li>Glide v4 ships under R8 obfuscation inside the host APK; method names like
 *         {@code commitEdit()} become single-letter aliases that drift between releases.</li>
 *     <li>{@link DexKitMethodLocator} reliably locates the wrapper class itself
 *         (string fingerprint {@code "DiskLruCacheWrapper"}), but not the dozen helper
 *         methods we need to drive cache writes — those are matched by signature shape
 *         here instead.</li>
 * </ul>
 *
 * <p><b>API surface (all static):</b>
 * <ul>
 *     <li>{@link #resolveDiskLruCacheClass} — find the wrapper's underlying DiskLruCache
 *         class, used both for the {@code DiskLruCache.remove} hook and as a probe in
 *         {@link #hasDiskLruCacheApi}.</li>
 *     <li>{@link #resolveSafeKey} — turn a Glide cache key (DataCacheKey / ResourceCacheKey
 *         / EngineKey) into the SHA1-ish "safe key" the disk LRU uses as filename root.</li>
 *     <li>{@link #overwrite} — atomically replace the disk entry for a safe key with the
 *         contents of a source file, going through the editor commit / rename protocol.</li>
 *     <li>{@link #resolveCommittedCacheFile} — return the canonical {@code <safeKey>.0}
 *         file path, never the dirty {@code .tmp}.</li>
 * </ul>
 *
 * <p>Extracted from a 925-line monolith ({@code ImageCacheHookRegistrar}); the file was
 * over the project's 800-line per-module budget and mixed pure reflection with hook
 * installation. Keeping the reflection logic isolated lets the hook installer focus on
 * its actual responsibility and makes the reflection rules amenable to targeted testing.
 */
public final class DiskLruCacheReflector {

    private DiskLruCacheReflector() {
    }

    /**
     * Find the {@code DiskLruCache} backend class hidden inside the wrapper, either as a
     * declared field type or the return type of a zero-arg accessor. Probed by the API
     * shape that {@link #hasDiskLruCacheApi} checks for, so we don't depend on the R8
     * name {@code j3.a}.
     */
    public static @Nullable Class<?> resolveDiskLruCacheClass(@NonNull Class<?> diskCacheWrapperClass) {
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

    /**
     * Returns the safe-key string the wrapper would have computed for {@code diskCacheKey},
     * by walking the wrapper's fields looking for the SafeKeyGenerator instance and
     * invoking its single {@code String → String}-shaped method on the key.
     */
    static @Nullable String resolveSafeKey(@NonNull Object diskCacheWrapper, @Nullable Object diskCacheKey) {
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

    /**
     * Replaces the disk entry identified by {@code safeKey} with the contents of
     * {@code sourceFile}, using the editor commit protocol so concurrent readers never
     * observe a partial write. Returns the canonical {@code <safeKey>.0} file path so
     * callers can hand it straight to Glide.
     */
    static @Nullable File overwrite(@NonNull Object diskCacheWrapper, @NonNull String safeKey, @NonNull File sourceFile) throws Throwable {
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
        // After commit (or direct write) the only file Glide must ever observe is the
        // canonical ".0" entry. Returning the dirty ".tmp" file is unsafe because
        // DiskLruCache renames it away during commit; concurrent decoders would hit a
        // missing / partial file and trigger a re-fetch (visible in logs as cascading
        // replacements before this protocol was tightened).
        return resolveCommittedCacheFile(diskCacheWrapper, safeKey, backend, directFile);
    }

    /**
     * Resolves the canonical committed cache file ({@code safeKey + ".0"}) without ever
     * returning a dirty {@code .tmp} editor file. Falls back to the data file path probed
     * directly from the wrapper for legacy Glide builds where the snapshot getter is not
     * exposed.
     */
    static @Nullable File resolveCommittedCacheFile(@NonNull Object diskCacheWrapper, @NonNull String safeKey) throws Throwable {
        Object backend = resolveDiskCacheBackend(diskCacheWrapper);
        File directFile = resolveDiskCacheDataFile(diskCacheWrapper, safeKey);
        return resolveCommittedCacheFile(diskCacheWrapper, safeKey, backend, directFile);
    }

    private static @Nullable File resolveCommittedCacheFile(
            @NonNull Object diskCacheWrapper,
            @NonNull String safeKey,
            @Nullable Object backend,
            @Nullable File directFile
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
     * Probes whether {@code candidateClass} exposes the three DiskLruCache surface methods
     * we care about: a {@code remove(String) -> boolean}, a snapshot getter, and an editor
     * opener. Used both to identify the underlying DiskLruCache class on the wrapper and
     * to validate fields/return-types when walking the wrapper structure.
     */
    static boolean hasDiskLruCacheApi(@NonNull Class<?> candidateClass) {
        return HookReflectionUtils.findMethodBySignature(candidateClass, Boolean.TYPE, String.class) != null
                && findSnapshotGetterMethod(candidateClass) != null
                && findEditorMethod(candidateClass) != null;
    }

    private static @Nullable Object resolveDiskCacheBackend(@NonNull Object diskCacheWrapper) throws Throwable {
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

    private static @Nullable File resolveDiskCacheDataFile(@NonNull Object diskCacheWrapper, @NonNull String safeKey) throws IllegalAccessException {
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

    private static @Nullable Object openDiskCacheEditor(@NonNull Object diskCacheBackend, @NonNull String safeKey) throws Throwable {
        Method editorMethod = findEditorMethod(diskCacheBackend.getClass());
        if (editorMethod == null) {
            return null;
        }
        editorMethod.setAccessible(true);
        return editorMethod.invoke(diskCacheBackend, safeKey);
    }

    private static @Nullable File getDiskCacheSnapshotFile(@NonNull Object diskCacheBackend, @NonNull String safeKey) throws Throwable {
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

    /**
     * Finds the {@code DiskLruCache.get(String) -> Snapshot} method by shape: takes a
     * single String, returns an object that itself exposes a {@code File getFile(int)}
     * shape (the Snapshot's accessor for the entry's data file).
     */
    private static @Nullable Method findSnapshotGetterMethod(@NonNull Class<?> diskCacheBackendClass) {
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

    /**
     * Finds the {@code DiskLruCache.edit(String) -> Editor} method by shape: takes a
     * single String, returns a non-void/non-boolean type whose own methods include
     * {@code File getFile(int)} plus the two zero-arg "commit" / "abort" methods Glide's
     * Editor exposes (single-letter names {@code e} and {@code b} respectively under R8).
     */
    private static @Nullable Method findEditorMethod(@NonNull Class<?> diskCacheBackendClass) {
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

    private static @Nullable File getEditorFile(@NonNull Object editor) throws Throwable {
        Method fileMethod = HookReflectionUtils.findMethodBySignature(editor.getClass(), File.class, Integer.TYPE);
        if (fileMethod == null) {
            return null;
        }
        fileMethod.setAccessible(true);
        Object file = fileMethod.invoke(editor, 0);
        return file instanceof File ? (File) file : null;
    }

    private static @Nullable File getSnapshotFile(@NonNull Object snapshot) throws Throwable {
        Method fileMethod = HookReflectionUtils.findMethodBySignature(snapshot.getClass(), File.class, Integer.TYPE);
        if (fileMethod == null) {
            return null;
        }
        fileMethod.setAccessible(true);
        Object file = fileMethod.invoke(snapshot, 0);
        return file instanceof File ? (File) file : null;
    }

    private static void commitDiskCacheEditor(@NonNull Object editor) throws Throwable {
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

    private static void abortDiskCacheEditor(@NonNull Object editor) {
        try {
            Method cleanupMethod = HookReflectionUtils.findNoArgMethod(editor.getClass(), Void.TYPE, "b");
            if (cleanupMethod != null) {
                cleanupMethod.setAccessible(true);
                cleanupMethod.invoke(editor);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copyFile(@NonNull File sourceFile, @NonNull File targetFile) throws Throwable {
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
}
