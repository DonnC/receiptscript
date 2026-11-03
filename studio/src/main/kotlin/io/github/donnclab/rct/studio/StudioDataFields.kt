package io.github.donnclab.rct.studio

import io.github.donnclab.rct.tooling.*

internal data class StudioDataField(val path: String, val type: String)

/** All sample paths plus the lexical item aliases available to the selected block. */
internal fun studioDataFields(data: Map<String, Any?>, document: RctSourceDocument? = null, node: RctSourceNode? = null): List<StudioDataField> {
    val result = linkedMapOf<String, StudioDataField>()
    fun visit(value: Any?, path: String) {
        if (path.isNotEmpty()) result[path] = StudioDataField(path, when (value) {
            is Map<*, *> -> "Object"; is List<*> -> "Array"; is Number -> "Number"; is Boolean -> "Boolean"; null -> "Null"; else -> "Text"
        })
        when (value) {
            is Map<*, *> -> value.forEach { (key, child) -> visit(child, if (path.isEmpty()) key.toString() else "$path.$key") }
            is List<*> -> if (!path.endsWith(']')) value.forEachIndexed { index, child -> visit(child, "$path[$index]") }
        }
    }
    visit(data, "")
    val ancestors = mutableListOf<RctSourceNode>()
    var parent = node?.parentId
    while (parent != null) {
        val current = document?.nodes?.firstOrNull { it.id == parent } ?: break
        ancestors.add(0, current)
        parent = current.parentId
    }
    val aliases = mutableMapOf<String, List<Any?>>()
    fun resolve(value: Any?, parts: List<String>): Any? = parts.fold(value) { cursor, part -> when (cursor) {
        is Map<*, *> -> cursor[part]
        is List<*> -> part.toIntOrNull()?.let(cursor::getOrNull)
        else -> null
    } }
    ancestors.filter { it.statement.startsWith("for ") }.forEach { loop ->
        val props = RctStatementForm.read(loop.statement).properties.associate { it.key to it.value }
        val parts = props.getValue("path").split('.')
        val scope = aliases[parts.first()]
        val collections = if (scope != null) scope.map { resolve(it, parts.drop(1)) } else listOf(resolve(data, parts))
        val items = collections.flatMap { (it as? List<*>)?.toList().orEmpty() }
        aliases[props.getValue("item")] = items
        items.forEach { visit(it, props.getValue("item")) }
    }
    return result.values.toList()
}
