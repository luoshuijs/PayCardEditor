package com.luoshui.paycardeditor.hook

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight reflection cache utility optimized for card reading scenarios.
 * Caches Field and Method lookups to avoid repeated class hierarchy scans.
 */
internal object ReflectionCacheUtils {

    private data class FieldCacheKey(val clazz: Class<*>, val name: String)
    private data class MethodCacheKey(val clazz: Class<*>, val name: String)

    private val fieldCache = ConcurrentHashMap<FieldCacheKey, Optional<Field>>()
    private val methodCache = ConcurrentHashMap<MethodCacheKey, Optional<Method>>()

    /**
     * Find a field by name, searching up the class hierarchy.
     * Returns null if not found. Result is cached for subsequent calls.
     */
    fun findFieldIfExists(clazz: Class<*>, fieldName: String): Field? {
        val key = FieldCacheKey(clazz, fieldName)
        return fieldCache.computeIfAbsent(key) { k ->
            val field = findFieldInternal(k.clazz, k.name)
            if (field != null) Optional.of(field) else Optional.empty()
        }.orElse(null)
    }

    /**
     * Find a zero-argument method by name, searching up the class hierarchy.
     * Returns null if not found. Result is cached for subsequent calls.
     */
    fun findZeroArgMethodIfExists(clazz: Class<*>, methodName: String): Method? {
        val key = MethodCacheKey(clazz, methodName)
        return methodCache.computeIfAbsent(key) { k ->
            val method = findZeroArgMethodInternal(k.clazz, k.name)
            if (method != null) Optional.of(method) else Optional.empty()
        }.orElse(null)
    }

    /**
     * Get a String field value, returning empty string if field not found or on error.
     */
    fun getStringFieldOrEmpty(instance: Any, fieldName: String): String {
        return runCatching {
            val field = findFieldIfExists(instance.javaClass, fieldName) ?: return ""
            field.get(instance) as? String ?: ""
        }.getOrDefault("")
    }

    /**
     * Get an Object field value, returning null if field not found or on error.
     */
    fun getObjectFieldOrNull(instance: Any, fieldName: String): Any? {
        return runCatching {
            val field = findFieldIfExists(instance.javaClass, fieldName) ?: return null
            field.get(instance)
        }.getOrNull()
    }

    /**
     * Get a boolean field value, returning false if field not found or on error.
     */
    fun getBooleanFieldOrFalse(instance: Any, fieldName: String): Boolean {
        return runCatching {
            val field = findFieldIfExists(instance.javaClass, fieldName) ?: return false
            field.getBoolean(instance)
        }.getOrDefault(false)
    }

    /**
     * Call a zero-argument method returning String, returning empty string if method not found or on error.
     */
    fun callStringMethodOrEmpty(instance: Any, methodName: String): String {
        return runCatching {
            val method = findZeroArgMethodIfExists(instance.javaClass, methodName) ?: return ""
            method.invoke(instance) as? String ?: ""
        }.getOrDefault("")
    }

    /**
     * Call a zero-argument method returning Boolean, returning false if method not found or on error.
     */
    fun callBooleanMethodOrFalse(instance: Any, methodName: String): Boolean {
        return runCatching {
            val method = findZeroArgMethodIfExists(instance.javaClass, methodName) ?: return false
            method.invoke(instance) as? Boolean ?: false
        }.getOrDefault(false)
    }

    /**
     * Call a zero-argument method returning Any, returning null if method not found or on error.
     */
    fun callObjectMethodOrNull(instance: Any, methodName: String): Any? {
        return runCatching {
            val method = findZeroArgMethodIfExists(instance.javaClass, methodName) ?: return null
            method.invoke(instance)
        }.getOrNull()
    }

    /**
     * Set an object field value. Returns true if successful, false otherwise.
     */
    fun setObjectField(instance: Any, fieldName: String, value: Any?): Boolean {
        return runCatching {
            val field = findFieldIfExists(instance.javaClass, fieldName) ?: return false
            field.set(instance, value)
            true
        }.getOrDefault(false)
    }

    private fun findFieldInternal(start: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = start
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: Throwable) {
                // Continue to superclass
            }
            current = current.superclass
        }
        return null
    }

    private fun findZeroArgMethodInternal(start: Class<*>, methodName: String): Method? {
        var current: Class<*>? = start
        while (current != null) {
            try {
                val method = current.declaredMethods.firstOrNull { 
                    it.name == methodName && it.parameterCount == 0 
                }
                if (method != null) {
                    method.isAccessible = true
                    return method
                }
            } catch (_: Throwable) {
                // Continue to superclass
            }
            current = current.superclass
        }
        return null
    }

    /**
     * Clear all caches. Useful for testing or when class definitions change.
     */
    fun clearCaches() {
        fieldCache.clear()
        methodCache.clear()
    }
}