package com.luoshui.paycardeditor.hook;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.BuildConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;

final class HookDebugReporter {

    private final XposedModule mModule;
    private final RemoteCardSnapshotStore mSnapshotStore;
    private final HookInstallerSupport mSupport;

    HookDebugReporter(
            @NonNull XposedModule module,
            @NonNull RemoteCardSnapshotStore snapshotStore,
            @NonNull HookInstallerSupport support
    ) {
        mModule = module;
        mSnapshotStore = snapshotStore;
        mSupport = support;
    }

    /**
     * Publishes the troubleshoot snapshot. Every input is either constant for the host or
     * derivable without a {@link android.content.Context}, so a single call at install time
     * is enough — no host-Application-attach barrier to wait on.
     */
    void publishTroubleshootState(
            @NonNull String apkPath,
            @NonNull Class<?> cardInfoClass,
            @NonNull Class<?> cardInfoManagerClass,
            @NonNull Class<?> cacheLauncherClass,
            @NonNull DexKitHookTargets dexKitTargets,
            @NonNull ImageCacheHookRegistrar imageCacheHooks
    ) {
        try {
            String debugStatus = buildDebugStatus(apkPath);
            String hookMethods = buildHookMethodList(cardInfoClass, cardInfoManagerClass, cacheLauncherClass, dexKitTargets, imageCacheHooks);
            mSnapshotStore.updateTroubleshootState(debugStatus, hookMethods);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private String buildDebugStatus(@NonNull String apkPath) {
        StringBuilder builder = new StringBuilder();
        builder.append("PID: ").append(Process.myPid())
                .append(", UID: ").append(Process.myUid())
                .append(", ISA: ").append(resolveIsaName())
                .append('\n');
        builder.append("Xposed API version: ").append(mModule.getApiVersion()).append('\n');
        builder.append("module: ").append(apkPath).append('\n');
        builder.append(mModule.getClass().getName())
                .append(' ')
                .append(BuildConfig.VERSION_NAME)
                .append(" (")
                .append(BuildConfig.VERSION_CODE)
                .append(")\n");
        builder.append(mModule.getFrameworkName())
                .append(' ')
                .append(mModule.getFrameworkVersion())
                .append(" (")
                .append(mModule.getFrameworkVersionCode())
                .append(")\n");
        builder.append("Hook counter: ").append(mSupport.getInstalledHookCount());
        return builder.toString();
    }

    @NonNull
    private String buildHookMethodList(
            @NonNull Class<?> cardInfoClass,
            @NonNull Class<?> cardInfoManagerClass,
            @NonNull Class<?> cacheLauncherClass,
            @NonNull DexKitHookTargets dexKitTargets,
            @NonNull ImageCacheHookRegistrar imageCacheHooks
    ) throws NoSuchMethodException {
        List<HookCandidate> candidates = new ArrayList<>();
        candidates.add(new HookCandidate("CardInfo.updateInfo", cardInfoClass, cardInfoClass.getDeclaredMethod("updateInfo", cardInfoClass)));
        candidates.add(new HookCandidate("CardInfoManager.put(CardInfo)", cardInfoManagerClass, HookReflectionUtils.findOverload(cardInfoManagerClass, "put", parameterTypes ->
                parameterTypes.length == 1 && cardInfoClass.getName().equals(parameterTypes[0].getName()))));
        candidates.add(new HookCandidate("CardInfoManager.put(List)", cardInfoManagerClass, HookReflectionUtils.findOverload(cardInfoManagerClass, "put", parameterTypes ->
                parameterTypes.length == 1 && List.class.isAssignableFrom(parameterTypes[0]))));
        candidates.add(new HookCandidate("CardInfoManager.getAll", cardInfoManagerClass, cardInfoManagerClass.getDeclaredMethod("getAll", cacheLauncherClass)));
        candidates.add(new HookCandidate("CardInfoManager.getBankCards", cardInfoManagerClass, cardInfoManagerClass.getDeclaredMethod("getBankCards", cacheLauncherClass)));
        candidates.add(new HookCandidate("CardInfoManager.getIssuedTransCards", cardInfoManagerClass, cardInfoManagerClass.getDeclaredMethod("getIssuedTransCards", cacheLauncherClass)));
        candidates.add(new HookCandidate("CardInfoManager.getMifareCards", cardInfoManagerClass, cardInfoManagerClass.getDeclaredMethod("getMifareCards", cacheLauncherClass)));
        candidates.add(new HookCandidate("BankCardInfo.mergeVirtualCardInfo", resolveDeclaringClass(dexKitTargets.getBankVirtualCardMerge()), dexKitTargets.getBankVirtualCardMerge()));
        candidates.add(new HookCandidate("BankCardInfo.mergeQueryPanInfo", resolveDeclaringClass(dexKitTargets.getBankQueryPanMerge()), dexKitTargets.getBankQueryPanMerge()));
        candidates.add(new HookCandidate("CardInfo.updateBackground", resolveDeclaringClass(dexKitTargets.getTransitUpdateBackground(), cardInfoClass), dexKitTargets.getTransitUpdateBackground()));
        candidates.add(new HookCandidate("Md5FileNameGenerator.generate", resolveDeclaringClass(dexKitTargets.getGlideTokenGenerate()), dexKitTargets.getGlideTokenGenerate()));
        candidates.add(new HookCandidate("DiskLruCacheWrapper.get", resolveDeclaringClass(dexKitTargets.getGlideDiskCacheGet(), dexKitTargets.getGlideDiskCacheWrapperClass()), dexKitTargets.getGlideDiskCacheGet()));
        candidates.add(new HookCandidate("DiskLruCacheWrapper.put", resolveDeclaringClass(dexKitTargets.getGlideDiskCachePut(), dexKitTargets.getGlideDiskCacheWrapperClass()), dexKitTargets.getGlideDiskCachePut()));
        Method diskRemoveMethod = null;
        Class<?> diskCacheWrapperClass = dexKitTargets.getGlideDiskCacheWrapperClass();
        if (diskCacheWrapperClass != null) {
            Class<?> diskLruCacheClass = imageCacheHooks.resolveDiskLruCacheClass(diskCacheWrapperClass);
            if (diskLruCacheClass != null) {
                diskRemoveMethod = HookReflectionUtils.findMethodBySignature(diskLruCacheClass, Boolean.TYPE, String.class);
            }
        }
        candidates.add(new HookCandidate("DiskLruCache.remove", resolveDeclaringClass(diskRemoveMethod), diskRemoveMethod));
        candidates.add(new HookCandidate("MifareModel.queryDoorCardInfo", resolveDeclaringClass(dexKitTargets.getMifareQuery()), dexKitTargets.getMifareQuery()));
        // Engine memory-cache lookup hook: located by shape inside the Engine class. Delegate
        // to MemoryCacheHookRegistrar so the troubleshoot page and the installer agree on
        // which method gets hooked (no duplicated selection rules).
        Class<?> engineClass = dexKitTargets.getGlideEngineClass();
        Method engineLoadFromCacheMethod = engineClass != null
                ? MemoryCacheHookRegistrar.findLoadFromCacheMethod(engineClass)
                : null;
        candidates.add(new HookCandidate(
                "Engine.loadFromCache",
                engineLoadFromCacheMethod != null ? engineLoadFromCacheMethod.getDeclaringClass() : engineClass,
                engineLoadFromCacheMethod
        ));
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            HookCandidate candidate = candidates.get(index);
            if (index > 0) {
                // Blank line between blocks; TroubleshootFragment.buildHookMethodSpans splits
                // on "\n\n" to color each entry independently.
                builder.append("\n\n");
            }
            builder.append('[')
                    .append(index + 1)
                    .append(']')
                    .append(candidate.label)
                    .append('\n')
                    .append(candidate.ownerClass != null ? candidate.ownerClass.getName() : "(void*)0")
                    .append('\n')
                    .append("= ")
                    .append(candidate.method != null ? buildMethodDescriptor(candidate.method) : "(void*)0");
        }
        return builder.toString();
    }

    @NonNull
    private String resolveIsaName() {
        String rawAbi = null;
        if (Build.SUPPORTED_ABIS.length > 0) {
            rawAbi = Build.SUPPORTED_ABIS[0];
        }
        if (rawAbi == null || rawAbi.isEmpty()) {
            rawAbi = System.getProperty("os.arch", "unknown");
        }
        String abi = rawAbi.toLowerCase(Locale.ROOT);
        if (abi.contains("arm64") || abi.contains("aarch64")) {
            return "arm64";
        }
        if (abi.contains("armeabi") || abi.contains("arm")) {
            return "arm";
        }
        if (abi.contains("x86_64")) {
            return "x86_64";
        }
        if (abi.contains("x86")) {
            return "x86";
        }
        return rawAbi;
    }

    private Class<?> resolveDeclaringClass(Method method) {
        return method != null ? method.getDeclaringClass() : null;
    }

    private Class<?> resolveDeclaringClass(Method method, Class<?> fallbackClass) {
        return method != null ? method.getDeclaringClass() : fallbackClass;
    }

    @NonNull
    private String buildMethodDescriptor(@NonNull Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append('L')
                .append(method.getDeclaringClass().getName().replace('.', '/'))
                .append(";->")
                .append(method.getName())
                .append('(');
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(toDexType(parameterType));
        }
        builder.append(')')
                .append(toDexType(method.getReturnType()));
        return builder.toString();
    }

    @NonNull
    private String toDexType(@NonNull Class<?> type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (Void.TYPE.equals(type)) {
            return "V";
        }
        if (Boolean.TYPE.equals(type)) {
            return "Z";
        }
        if (Byte.TYPE.equals(type)) {
            return "B";
        }
        if (Character.TYPE.equals(type)) {
            return "C";
        }
        if (Short.TYPE.equals(type)) {
            return "S";
        }
        if (Integer.TYPE.equals(type)) {
            return "I";
        }
        if (Long.TYPE.equals(type)) {
            return "J";
        }
        if (Float.TYPE.equals(type)) {
            return "F";
        }
        if (Double.TYPE.equals(type)) {
            return "D";
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static final class HookCandidate {
        final String label;
        final Class<?> ownerClass;
        final Method method;

        HookCandidate(@NonNull String label, Class<?> ownerClass, Method method) {
            this.label = label;
            this.ownerClass = ownerClass;
            this.method = method;
        }
    }
}
