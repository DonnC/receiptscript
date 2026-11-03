package io.github.donnclab.rct.tooling

import io.github.donnclab.rct.ReceiptColumnAlign

/** A source-backed statement. Handles belong to one document revision, not to rendered blocks. */
public class RctSourceNode internal constructor(
    public val id: Int,
    public val parentId: Int?,
    public val line: Int,
    public val depth: Int,
    public val statement: String,
    internal val start: Int,
    internal val statementEnd: Int,
    internal var end: Int,
    internal val revision: Any
)

/**
 * Lossless authoring over core-validated source. Untouched bytes, including comments and line
 * endings, are retained. Every operation returns a new validated document or throws without
 * modifying this one. Invalid drafts should be kept separately by the editor.
 *
 * This is a source index, not another grammar: acceptance always belongs to [RctLinter].
 */
public class RctSourceDocument private constructor(
    public val source: String,
    public val nodes: List<RctSourceNode>,
    private val revision: Any
) {
    private val newline: String = if (source.contains("\r\n")) "\r\n" else "\n"

    public fun replaceStatement(node: RctSourceNode, statement: String): RctSourceDocument {
        checkNode(node)
        require('\n' !in statement && '\r' !in statement) { "Use replaceBlock for multiline edits." }
        return parse(source.replaceRange(node.start, node.statementEnd, "  ".repeat(node.depth) + statement))
    }

    /** Replaces a complete block; [block] is unindented relative to the selected statement. */
    public fun replaceBlock(node: RctSourceNode, block: String): RctSourceDocument {
        checkNode(node)
        return parse(source.replaceRange(node.start, node.end, indent(block, node.depth)))
    }

    public fun blockSource(node: RctSourceNode): String {
        checkNode(node)
        return source.substring(node.start, node.end).lines().joinToString("\n") {
            it.removePrefix("  ".repeat(node.depth))
        }.trimEnd('\n', '\r')
    }

    /** Appends at the root or inside [parent]. Grammar restrictions are enforced by core. */
    public fun append(block: String, parent: RctSourceNode? = null): RctSourceDocument {
        parent?.let(::checkNode)
        require(parent == null || parent.statement.endsWith(':')) { "This block cannot contain children." }
        val offset = parent?.end ?: source.length
        val prefix = if (offset > 0 && source[offset - 1] != '\n') newline else ""
        val inserted = prefix + indent(block, parent?.depth?.plus(1) ?: 0)
        return parse(source.replaceRange(offset, offset, inserted))
    }

    public fun remove(node: RctSourceNode): RctSourceDocument {
        checkNode(node)
        return parse(source.removeRange(node.start, node.end))
    }

    public fun duplicate(node: RctSourceNode): RctSourceDocument {
        checkNode(node)
        val block = source.substring(node.start, node.end)
        val prefix = if (node.end > 0 && source[node.end - 1] != '\n') newline else ""
        return parse(source.replaceRange(node.end, node.end, prefix + block))
    }

    /** Reorders the supplied sibling blocks in their existing slots, leaving other source intact. */
    public fun reorder(order: List<RctSourceNode>): RctSourceDocument {
        order.forEach(::checkNode)
        require(order.map { it.id }.distinct().size == order.size) { "A block can only appear once." }
        require(order.map { it.parentId }.distinct().size <= 1) { "Reorder blocks within one container." }
        if (order.size < 2) return this
        val slots = order.sortedBy { it.start }
        var edited = source
        slots.indices.reversed().forEach { index ->
            val block = source.substring(order[index].start, order[index].end)
            val terminated = if (block.endsWith('\n') || slots[index].end == source.length) block else block + newline
            edited = edited.replaceRange(slots[index].start, slots[index].end, terminated)
        }
        return parse(edited)
    }

    /** Local contents in core print order; shared references remain separate repository entries. */
    public fun sectionSource(section: String): String {
        require(section in setOf("header", "body", "footer"))
        val roots = nodes.filter { it.parentId == null }
        fun contents(node: RctSourceNode): String = blockSource(node).lines().drop(1).joinToString("\n") { it.removePrefix("  ") }
        return if (section == "body") roots.filter(::isBodyRoot).joinToString("\n") {
            if (it.statement == "body:") contents(it) else blockSource(it)
        } else roots.firstOrNull { it.statement == "$section:" }?.let(::contents).orEmpty()
    }

    /** Edits one first-class section. Null [references] preserves its existing shared references. */
    public fun withSection(section: String, contents: String, references: List<String>? = null): RctSourceDocument {
        require(section in setOf("header", "body", "footer"))
        require(section != "body" || references.isNullOrEmpty()) { "Body sections cannot reference a shared header/footer." }
        val roots = nodes.filter { it.parentId == null }
        val removed = roots.filter { node ->
            (if (section == "body") isBodyRoot(node) else node.statement == "$section:") ||
                (references != null && node.statement.startsWith("use $section "))
        }
        var edited = source
        removed.sortedByDescending { it.start }.forEach { edited = edited.removeRange(it.start, it.end) }
        if (edited.isNotEmpty() && !edited.endsWith('\n')) edited += newline
        if (contents.isNotBlank()) edited += "$section:" + newline + indent(contents, 1)
        references?.forEach { id -> edited += "use $section \"$id\"" + newline }
        return parse(edited)
    }

    private fun isBodyRoot(node: RctSourceNode): Boolean = !node.statement.startsWith("receipt ") &&
        !node.statement.startsWith("use ") && node.statement !in setOf("meta:", "header:", "footer:")

    /** Updates only a table's configuration lines. Null restores the core default. */
    public fun withTableOptions(
        node: RctSourceNode,
        headers: List<String>?,
        weights: List<Float>?,
        alignments: List<ReceiptColumnAlign>?
    ): RctSourceDocument {
        checkNode(node)
        require(node.statement == "table:") { "Select a table to configure columns." }
        require(weights == null || weights.all { it.isFinite() && it > 0 }) { "Column weights must be positive finite numbers." }
        val options = mapOf(
            "headers" to headers?.takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"$it\"" },
            "weights" to weights?.takeIf { it.isNotEmpty() }?.joinToString(" "),
            "alignments" to alignments?.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.name.lowercase() }
        )
        return withOptions(node, options)
    }

    /** Updates only the supplied metadata keys. Values are RCT literals; null removes a key. */
    public fun withMetadata(values: Map<String, String?>): RctSourceDocument {
        values.forEach { (key, value) ->
            require(Regex("[A-Za-z][A-Za-z0-9_]*").matches(key)) { "Invalid metadata key: $key" }
            require(value == null || ('\n' !in value && '\r' !in value)) { "Metadata values must occupy one line." }
        }
        val meta = nodes.firstOrNull { it.depth == 0 && it.statement == "meta:" }
        if (meta != null) return withOptions(meta, values)
        val entries = values.filterValues { it != null }
        if (entries.isEmpty()) return this
        val declaration = nodes.first()
        val offset = declaration.end
        val prefix = if (offset > 0 && source[offset - 1] != '\n') newline else ""
        val block = "meta:" + newline + entries.entries.joinToString(newline) { "  ${it.key}: ${it.value}" } + newline
        return parse(source.replaceRange(offset, offset, prefix + block))
    }

    private fun withOptions(node: RctSourceNode, options: Map<String, String?>): RctSourceDocument {
        val patches = mutableListOf<Triple<Int, Int, String>>()
        val additions = StringBuilder()
        options.forEach { (key, value) ->
            val existing = nodes.filter { it.parentId == node.id && it.statement.startsWith("$key:") }
            existing.forEachIndexed { index, child ->
                val lineEnd = source.indexOf('\n', child.statementEnd).let { if (it < 0) source.length else it + 1 }
                patches += Triple(child.start, lineEnd,
                    if (value == null || index != 0) "" else "  ".repeat(child.depth) + "$key: $value" + newline)
            }
            if (existing.isEmpty() && value != null) additions.append("  ".repeat(node.depth + 1)).append("$key: $value").append(newline)
        }
        if (additions.isNotEmpty()) {
            val position = source.indexOf('\n', node.statementEnd).let { if (it < 0) source.length else it + 1 }
            val prefix = if (position > 0 && source[position - 1] != '\n') newline else ""
            patches += Triple(position, position, prefix + additions.toString())
        }
        var edited = source
        patches.sortedByDescending { it.first }.forEach { (start, end, value) -> edited = edited.replaceRange(start, end, value) }
        return parse(edited)
    }

    /** Moves one sibling position. Crossing section boundaries is deliberately not implicit. */
    public fun move(node: RctSourceNode, direction: Int): RctSourceDocument {
        checkNode(node)
        require(direction == -1 || direction == 1)
        val siblings = nodes.filter { it.parentId == node.parentId }
        val target = siblings.getOrNull(siblings.indexOf(node) + direction) ?: return this
        val first = if (direction < 0) target else node
        val second = if (direction < 0) node else target
        fun terminated(value: String): String = if (value.endsWith('\n')) value else value + newline
        val a = source.substring(first.start, first.end)
        val b = source.substring(second.start, second.end)
        val gap = source.substring(first.end, second.start)
        return parse(source.replaceRange(first.start, second.end, terminated(b) + gap + a))
    }

    private fun checkNode(node: RctSourceNode) {
        require(node.revision === revision) { "This block belongs to an older document revision." }
    }

    private fun indent(block: String, depth: Int): String = block.replace("\r\n", "\n")
        .trimEnd('\n', '\r').lines().joinToString(newline) {
            if (it.isBlank()) it else "  ".repeat(depth) + it
        } + newline

    public companion object {
        /** Parses with the same validation rules used by the engine. */
        public fun parse(source: String): RctSourceDocument {
            val issues = RctLinter.lint(source).filter { it.severity == RctLintSeverity.ERROR }
            require(issues.isEmpty()) { issues.joinToString("\n") { "Line ${it.line}: ${it.message}" } }
            val revision = Any()
            val nodes = mutableListOf<RctSourceNode>()
            val stack = mutableListOf<RctSourceNode>()
            var offset = 0
            source.splitToSequence('\n').forEachIndexed { index, raw ->
                val line = raw.removeSuffix("\r")
                val statement = line.trim()
                val next = (offset + raw.length + 1).coerceAtMost(source.length)
                if (statement.isNotEmpty() && !statement.startsWith('#')) {
                    val depth = line.takeWhile { it == ' ' }.length / 2
                    while (stack.isNotEmpty() && stack.last().depth >= depth) {
                        stack.removeAt(stack.lastIndex).end = offset
                    }
                    val node = RctSourceNode(nodes.size, stack.lastOrNull()?.id, index + 1, depth,
                        statement, offset, offset + line.length, source.length, revision)
                    nodes += node
                    stack += node
                }
                offset = next
            }
            return RctSourceDocument(source, nodes, revision)
        }
    }
}

/** Insertion examples are engine syntax, verified against the core parser in unit tests. */
public data class RctBlockExample(public val title: String, public val source: String)

public object RctBlockPalette {
    public val entries: List<RctBlockExample> = listOf(
        RctBlockExample("Text", "center \"Receipt title\""),
        RctBlockExample("Label and value", "kv \"TOTAL\" \"{{txn.total | money:txn.currency}}\""),
        RctBlockExample("Divider", "divider \"_\""),
        RctBlockExample("Space", "spacer 1"),
        RctBlockExample("Row", "row \"First\" \"Second\""),
        RctBlockExample("Table", "table:\n  headers: \"Item\" \"Qty\" \"Total\"\n  weights: 2 1 1\n  alignments: start center end\n  for item in items:\n    row \"{{item.name}}\" \"{{item.qty}}\" \"{{item.total}}\""),
        RctBlockExample("Image", "image \"{{brand.logo}}\" 88 88"),
        RctBlockExample("QR code", "qr \"{{links.payment}}\" 230"),
        RctBlockExample("Barcode", "barcode \"123456789012\" 300 60 \"code128\""),
        RctBlockExample("Show when", "if txn.message:\n  left \"{{txn.message}}\""),
        RctBlockExample("Repeat for", "for item in items:\n  left \"{{item.name}}\""),
        RctBlockExample("Header", "header:\n  center bold \"{{merchant.name}}\""),
        RctBlockExample("Body", "body:\n  left \"Receipt\""),
        RctBlockExample("Footer", "footer:\n  center \"Thank you!\""),
        RctBlockExample("Shared header", "use header \"merchant-header\""),
        RctBlockExample("Shared footer", "use footer \"customer-footer\"")
    )
}
