package com.luoshui.paycardeditor.hook;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

public final class HookReflectionUtils {

    private HookReflectionUtils() {
    }

    @NonNull
    public static Method findOverload(@NonNull Class<?> clazz, String methodName, @NonNull ParameterMatcher matcher) {
        for (Method method : clazz.getDeclaredMethods()) {
            if ((methodName == null || methodName.equals(method.getName())) && matcher.matches(method.getParameterTypes())) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Unable to locate overload for " + clazz.getName() + '#' + methodName);
    }

    public static Method findDiskCacheMethod(@NonNull Class<?> clazz, @NonNull Class<?> returnType, int parameterCount) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (returnType.equals(method.getReturnType()) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    public static Method findMethodBySignature(@NonNull Class<?> clazz, @NonNull Class<?> returnType, @NonNull Class<?>... parameterTypes) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!returnType.equals(method.getReturnType())) {
                continue;
            }
            Class<?>[] declaredParameterTypes = method.getParameterTypes();
            if (declaredParameterTypes.length != parameterTypes.length) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < declaredParameterTypes.length; index++) {
                if (!parameterTypes[index].equals(declaredParameterTypes[index])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    public static Method findNoArgMethod(@NonNull Class<?> clazz, @NonNull Class<?> returnType, @NonNull String preferredName) {
        Method preferred = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || !returnType.equals(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            if (preferredName.equals(method.getName())) {
                return method;
            }
            if (preferred == null) {
                preferred = method;
            }
        }
        return preferred;
    }

    @NonNull
    public static String describeMethods(@NonNull Class<?> clazz) {
        StringBuilder builder = new StringBuilder();
        Method[] methods = clazz.getDeclaredMethods();
        for (int index = 0; index < methods.length; index++) {
            Method method = methods[index];
            if (index > 0) {
                builder.append(';');
            }
            builder.append(method.getName())
                    .append('(');
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int parameterIndex = 0; parameterIndex < parameterTypes.length; parameterIndex++) {
                if (parameterIndex > 0) {
                    builder.append(',');
                }
                builder.append(parameterTypes[parameterIndex].getName());
            }
            builder.append("):")
                    .append(method.getReturnType().getName());
        }
        return builder.toString();
    }

    @FunctionalInterface
    public interface ParameterMatcher {
        boolean matches(Class<?>[] parameterTypes);
    }
}
