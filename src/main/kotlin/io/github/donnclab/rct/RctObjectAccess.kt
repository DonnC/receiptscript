package io.github.donnclab.rct

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal object RctObjectAccess {
    private val getterCache = ConcurrentHashMap<Class<*>, Map<String, Accessor>>()

    fun read(target: Any, key: String): Any? {
        val accessor = getterCache.getOrPut(target.javaClass) {
            buildAccessors(target.javaClass)
        }[key] ?: return null
        return accessor.read(target)
    }

    private fun buildAccessors(type: Class<*>): Map<String, Accessor> {
        val accessors = linkedMapOf<String, Accessor>()

        type.methods
            .filter { it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) }
            .forEach { method ->
                propertyNameFor(method)?.let { name ->
                    if (!Modifier.isPublic(type.modifiers)) method.isAccessible = true
                    accessors.putIfAbsent(name, MethodAccessor(method))
                }
            }

        type.fields.filter { !Modifier.isStatic(it.modifiers) }.forEach { field ->
            if (!Modifier.isPublic(type.modifiers)) field.isAccessible = true
            accessors.putIfAbsent(field.name, FieldAccessor(field))
        }

        return accessors
    }

    private fun propertyNameFor(method: Method): String? {
        val name = method.name
        return when {
            name == "getClass" -> null
            name.startsWith("get") && name.length > 3 -> name.substring(3).replaceFirstChar { it.lowercase() }
            name.startsWith("is") && name.length > 2 -> name.substring(2).replaceFirstChar { it.lowercase() }
            else -> null
        }
    }

    private sealed interface Accessor {
        fun read(target: Any): Any?
    }

    private data class MethodAccessor(
        private val method: Method
    ) : Accessor {
        override fun read(target: Any): Any? = method.invoke(target)
    }

    private data class FieldAccessor(
        private val field: Field
    ) : Accessor {
        override fun read(target: Any): Any? = field.get(target)
    }
}
