package com.luoshui.paycardeditor.hook.debug;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;

import com.luoshui.paycardeditor.BuildConfig;
import com.luoshui.paycardeditor.hook.HookCatalog;
import com.luoshui.paycardeditor.hook.HookInstallerSupport;
import com.luoshui.paycardeditor.hook.card.RemoteCardSnapshotStore;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

import io.github.libxposed.api.XposedModule;

public final class HookDebugReporter {

    private static final String MISSING_PLACEHOLDER = "(void*)0";

    private final XposedModule mModule;
    private final RemoteCardSnapshotStore mSnapshotStore;
    private final HookInstallerSupport mSupport;

    public HookDebugReporter(
            @NonNull XposedModule module,
            @NonNull RemoteCardSnapshotStore snapshotStore,
            @NonNull HookInstallerSupport support
    ) {
        mModule = module;
        mSnapshotStore = snapshotStore;
        mSupport = support;
    }

    /**
     * Publishes the troubleshoot snapshot. The hook list is derived entirely from
     * {@link HookInstallerSupport#snapshotInstalledMethodsByCatalog()}, which means the
     * troubleshoot page can never disagree with what the installer actually hooked:
     * every entry in {@link HookCatalog} shows up exactly once, ordered by the enum
     * declaration order, with the resolved method when present and a {@code (void*)0}
     * placeholder when the installer failed to hook it.
     */
    public void publishTroubleshootState(@NonNull String apkPath) {
        try {
            String debugStatus = buildDebugStatus(apkPath);
            String hookMethods = buildHookMethodList();
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
    private String buildHookMethodList() {
        Map<HookCatalog, Method> installed = mSupport.snapshotInstalledMethodsByCatalog();
        HookCatalog[] entries = HookCatalog.values();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < entries.length; index++) {
            HookCatalog entry = entries[index];
            Method method = installed.get(entry);
            if (index > 0) {
                // Blank line between blocks; TroubleshootFragment.buildHookMethodSpans splits
                // on "\n\n" to color each entry independently.
                builder.append("\n\n");
            }
            builder.append('[')
                    .append(index + 1)
                    .append(']')
                    .append(entry.getLabel())
                    .append('\n')
                    .append(method != null ? method.getDeclaringClass().getName() : MISSING_PLACEHOLDER)
                    .append('\n')
                    .append("= ")
                    .append(method != null ? buildMethodDescriptor(method) : MISSING_PLACEHOLDER);
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
}
