package com.luoshui.paycardeditor.hook;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.github.libxposed.api.XposedModule;

final class HookInstallerSupport {

    private static final String TAG = "PayCardEditorHook";

    private final XposedModule mModule;
    private final List<HookRecord> mInstalledHookRecords;

    HookInstallerSupport(@NonNull XposedModule module, @NonNull List<HookRecord> installedHookRecords) {
        mModule = module;
        mInstalledHookRecords = installedHookRecords;
    }

    void prepareMethod(@NonNull Method method, @NonNull String label) {
        method.setAccessible(true);
        try {
            boolean deoptimized = mModule.deoptimize(method);
            mModule.log(Log.DEBUG, TAG, label + " deoptimize=" + deoptimized);
        } catch (Throwable ignored) {
            mModule.log(Log.DEBUG, TAG, label + " deoptimize skipped");
        }
    }

    void runHookSideEffect(@NonNull String source, @NonNull HookSideEffect action) {
        try {
            action.run();
        } catch (UnsupportedOperationException exception) {
            mModule.log(Log.ERROR, TAG, "suppressed read-only host failure in " + source + ": " + Log.getStackTraceString(exception));
        } catch (Throwable throwable) {
            mModule.log(Log.ERROR, TAG, "suppressed hook side effect failure in " + source + ": " + Log.getStackTraceString(throwable));
        }
    }

    void recordInstalledHook(@NonNull String label, @NonNull Method method) {
        synchronized (mInstalledHookRecords) {
            mInstalledHookRecords.add(new HookRecord(label, method));
        }
    }

    int getInstalledHookCount() {
        synchronized (mInstalledHookRecords) {
            return mInstalledHookRecords.size();
        }
    }

    @NonNull
    List<?> copyCards(@NonNull Collection<?> cards) {
        return new ArrayList<>(cards);
    }

    static final class HookRecord {
        final String label;
        final Method method;

        HookRecord(@NonNull String label, @NonNull Method method) {
            this.label = label;
            this.method = method;
        }
    }

    @FunctionalInterface
    interface HookSideEffect {
        void run() throws Throwable;
    }
}
