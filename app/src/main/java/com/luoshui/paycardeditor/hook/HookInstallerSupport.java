package com.luoshui.paycardeditor.hook;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedModule;

public final class HookInstallerSupport {

    private static final String TAG = "PayCardEditorHook";

    private final XposedModule mModule;
    private final List<HookRecord> mInstalledHookRecords;

    HookInstallerSupport(@NonNull XposedModule module, @NonNull List<HookRecord> installedHookRecords) {
        mModule = module;
        mInstalledHookRecords = installedHookRecords;
    }

    public void prepareMethod(@NonNull Method method, @NonNull String label) {
        method.setAccessible(true);
        try {
            boolean deoptimized = mModule.deoptimize(method);
            mModule.log(Log.DEBUG, TAG, label + " deoptimize=" + deoptimized);
        } catch (Throwable ignored) {
            mModule.log(Log.DEBUG, TAG, label + " deoptimize skipped");
        }
    }

    public void runHookSideEffect(@NonNull String source, @NonNull HookSideEffect action) {
        try {
            action.run();
        } catch (UnsupportedOperationException exception) {
            mModule.log(Log.ERROR, TAG, "suppressed read-only host failure in " + source + ": " + Log.getStackTraceString(exception));
        } catch (Throwable throwable) {
            mModule.log(Log.ERROR, TAG, "suppressed hook side effect failure in " + source + ": " + Log.getStackTraceString(throwable));
        }
    }

    public void recordInstalledHook(@NonNull HookCatalog entry, @NonNull Method method) {
        synchronized (mInstalledHookRecords) {
            mInstalledHookRecords.add(new HookRecord(entry, method));
        }
    }

    public int getInstalledHookCount() {
        synchronized (mInstalledHookRecords) {
            return mInstalledHookRecords.size();
        }
    }

    /**
     * Snapshot of "which hook was installed against which method", keyed by the
     * catalog entry. Used by {@link com.luoshui.paycardeditor.hook.debug.HookDebugReporter}
     * to render the troubleshoot page without maintaining its own candidate list.
     *
     * <p>The map only contains entries for hooks that were actually installed.
     * Missing keys mean the hook was not installed and should render as
     * {@code (void*)0} on the troubleshoot page.</p>
     */
    @NonNull
    public Map<HookCatalog, Method> snapshotInstalledMethodsByCatalog() {
        synchronized (mInstalledHookRecords) {
            Map<HookCatalog, Method> snapshot = new HashMap<>(mInstalledHookRecords.size());
            for (HookRecord record : mInstalledHookRecords) {
                snapshot.put(record.entry, record.method);
            }
            return snapshot;
        }
    }

    @NonNull
    public List<?> copyCards(@NonNull Collection<?> cards) {
        return new ArrayList<>(cards);
    }

    static final class HookRecord {
        final HookCatalog entry;
        final Method method;

        HookRecord(@NonNull HookCatalog entry, @NonNull Method method) {
            this.entry = entry;
            this.method = method;
        }
    }

    @FunctionalInterface
    public interface HookSideEffect {
        void run() throws Throwable;
    }
}
