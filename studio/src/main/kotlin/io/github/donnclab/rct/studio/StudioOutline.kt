package io.github.donnclab.rct.studio

import io.github.donnclab.rct.tooling.RctSourceDocument
import io.github.donnclab.rct.tooling.RctSourceNode
import io.github.donnclab.rct.tooling.RctStatementForm
import io.github.donnclab.rct.tooling.RctBindingForm

internal data class StudioSection(val title: String, val nodes: List<RctSourceNode>, val container: RctSourceNode?)

/** Match core's reference/inline ordering, without changing the original source. */
internal fun studioSections(document: RctSourceDocument): List<StudioSection> {
    val roots = document.nodes.filter { it.parentId == null }
    fun inline(name: String) = roots.firstOrNull { it.statement == "$name:" }
    fun children(node: RctSourceNode?) = if (node == null) emptyList() else outlineChildren(document, node)
    val header = inline("header")
    val footer = inline("footer")
    val body = inline("body")
    val ordinary = roots.filterNot {
        it.statement.startsWith("receipt ") || it.statement.startsWith("use ") ||
            it.statement in setOf("meta:", "header:", "footer:")
    }.flatMap { if (it.statement == "body:") children(it) else listOf(it) }
    return listOf(
        StudioSection("Header", roots.filter { it.statement.startsWith("use header ") } + children(header), header),
        StudioSection("Body", ordinary, body),
        StudioSection("Footer", children(footer) + roots.filter { it.statement.startsWith("use footer ") }, footer)
    )
}

internal fun outlineChildren(document: RctSourceDocument, node: RctSourceNode): List<RctSourceNode> =
    document.nodes.filter { it.parentId == node.id }

private fun fieldName(value: String): String = value.replace('.', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }

internal fun outlineTitle(document: RctSourceDocument, node: RctSourceNode): String {
    val props = RctStatementForm.read(node.statement).properties.associate { it.key to it.value }
    return when (node.statement.substringBefore(' ').removeSuffix(":")) {
        "kv" -> props["label"]?.takeIf { it.isNotBlank() } ?: "Label and value"
        "left", "right", "text", "center" -> props["value"]?.let { value -> RctBindingForm.read(value)?.path?.let(::fieldName) ?: value }?.takeIf { it.isNotBlank() } ?: "Text"
        "table" -> outlineChildren(document, node).firstOrNull { it.statement.startsWith("for ") }
            ?.let { RctStatementForm.read(it.statement).properties.firstOrNull { property -> property.key == "path" }?.value }
            ?.let { "${fieldName(it)} table" } ?: "Table"
        "use" -> props["id"]?.let(::fieldName) ?: blockTitle(node.statement)
        else -> blockTitle(node.statement)
    }
}

internal fun outlineSubtitle(document: RctSourceDocument, node: RctSourceNode): String {
    val props = RctStatementForm.read(node.statement).properties.associate { it.key to it.value }
    return when (node.statement.substringBefore(' ').removeSuffix(":")) {
        "kv" -> props["value"]?.let { RctBindingForm.read(it)?.path?.let(::fieldName) ?: it }.orEmpty()
        "left", "right", "text", "center" -> listOfNotNull("Text", props["align"]?.let { if (it == "text") "Left" else fieldName(it) },
            if (props["bold"] == "true") "Bold" else null).joinToString(" · ")
        "use" -> "Shared ${if (node.statement.startsWith("use header")) "header" else "footer"}"
        else -> blockSummary(document, node)
    }
}
