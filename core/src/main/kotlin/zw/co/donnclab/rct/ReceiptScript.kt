package zw.co.donnclab.rct

/** Converts resolved documents to host-defined output: SDK calls, bytes, images, or a UI model. */
fun interface ReceiptAdapter<T> {
    fun render(receipt: Receipt): T
}

/** Printer-neutral output. Media references remain host-owned strings. */
data class Receipt(val name: String, val meta: RctMeta?, val blocks: List<ReceiptBlock>) {
    fun <T> render(adapter: ReceiptAdapter<T>): T = adapter.render(this)
}

data class ReceiptTextStyle(
    val size: RctFontSize = RctFontSize.MIDDLE,
    val font: Float? = null,
    val bold: Boolean = false,
    val underline: Boolean = false
)

sealed interface ReceiptBlock {
    data class Text(val text: String, val align: RctAlign, val style: ReceiptTextStyle) : ReceiptBlock
    data class KeyValue(val key: String, val value: String, val style: ReceiptTextStyle) : ReceiptBlock
    data class Divider(val pattern: String, val length: Int) : ReceiptBlock
    data class Row(val columns: List<String>) : ReceiptBlock
    data class Spacer(val lines: Int = 1) : ReceiptBlock
    data class Table(
        val headers: List<String>, val rows: List<List<String>>,
        val weights: List<Float>?, val alignments: List<RctColumnAlignment>?,
        val headerStyle: ReceiptTextStyle, val rowStyle: ReceiptTextStyle
    ) : ReceiptBlock
    data class Image(val source: String, val width: Int?, val height: Int?) : ReceiptBlock
    data class QrCode(val content: String, val size: Int) : ReceiptBlock
    data class Barcode(val content: String, val width: Int, val height: Int, val format: BarcodeFormat) : ReceiptBlock
}

/**
 * Compile once and render many times. Template loading is synchronous and host-owned;
 * fetch remote sources before invoking this engine. No Android or printer SDK is required.
 */
class ReceiptScript(
    templates: Map<String, String> = emptyMap(),
    private val loadTemplate: (String) -> String? = templates::get,
    private val strictBindings: Boolean = true,
    private val parser: RctParser = DefaultRctParser(),
    private val engine: RctTemplateEngine = DefaultRctTemplateEngine()
) {
    fun compile(source: String): CompiledReceiptTemplate {
        val issues = RctLinter.lint(source).filter { it.severity == RctLintSeverity.ERROR }
        require(issues.isEmpty()) { issues.joinToString("\n") { "Line ${it.line}: ${it.message}" } }
        val document = parser.parse(source)
        validateNodes(document.header + document.body + document.footer)
        return CompiledReceiptTemplate(document, ::render)
    }

    fun template(id: String): CompiledReceiptTemplate = compile(
        requireNotNull(loadTemplate(id)) { "Unknown template: $id" }
    )

    private fun render(document: RctDocument, data: Any?): Receipt {
        fun fragments(references: List<RctFragmentReference>): List<RctNode> = references.flatMap { reference ->
            val source = requireNotNull(loadTemplate(reference.id)) { "Missing fragment: ${reference.id}" }
            parser.parseFragment(source).also(::validateNodes)
        }
        val header = fragments(document.headerReferences) + document.header
        val footer = document.footer + fragments(document.footerReferences)
        val context = RctRenderContext(data)
        val blocks = renderNodes(header + document.body, context, document.meta) +
            if (footer.isEmpty()) emptyList() else listOf(ReceiptBlock.Spacer(2)) + renderNodes(footer, context, document.meta)
        return Receipt(document.name, document.meta, blocks)
    }

    private fun text(template: RctTemplateString, context: RctRenderContext): String {
        if (strictBindings) template.parts.filterIsInstance<RctBindingPart>().forEach { binding ->
            require(engine.resolveValue(binding.path, context) != null || binding.filters.any { it.name == "default" }) {
                "Missing binding: ${binding.path}"
            }
        }
        return engine.render(template, context)
    }

    private fun style(meta: RctMeta?, size: RctFontSize = RctFontSize.MIDDLE, inline: Float? = null,
                      bold: Boolean = false, underline: Boolean = false): ReceiptTextStyle {
        val font = inline ?: meta?.font?.let { base ->
            if (meta.fontScaleMode == RctFontScaleMode.FIXED) base else when (size) {
                RctFontSize.SMALL -> (base - 3).coerceAtLeast(1f)
                RctFontSize.BIG -> base + 3
                RctFontSize.MIDDLE -> base
            }
        }
        return ReceiptTextStyle(size, font, bold, underline)
    }

    private fun renderNodes(nodes: List<RctNode>, context: RctRenderContext, meta: RctMeta?): List<ReceiptBlock> =
        nodes.flatMap { node -> when (node) {
            is RctTextNode -> listOf(ReceiptBlock.Text(text(node.text, context), node.align,
                style(meta, node.fontSize, node.customFontSize, node.bold, node.underline)))
            is RctKeyValueNode -> listOf(ReceiptBlock.KeyValue(text(node.key, context), text(node.value, context),
                style(meta, node.fontSize, node.customFontSize, node.bold, node.underline)))
            is RctDividerNode -> listOf(ReceiptBlock.Divider(node.pattern, node.length ?: meta?.width ?: 34))
            is RctSpacerNode -> listOf(ReceiptBlock.Spacer(node.lines))
            is RctRowNode -> listOf(ReceiptBlock.Row(node.columns.map { text(it, context) }))
            is RctImageNode -> listOf(ReceiptBlock.Image(text(node.source, context), node.width, node.height))
            is RctQrNode -> listOf(ReceiptBlock.QrCode(text(node.content, context), node.size))
            is RctBarcodeNode -> listOf(ReceiptBlock.Barcode(text(node.content, context), node.width, node.height, node.format))
            is RctIfNode -> if (engine.isTruthy(node.path, context)) renderNodes(node.children, context, meta) else emptyList()
            is RctForEachNode -> contexts(node, context).flatMap { renderNodes(node.children, it, meta) }
            is RctTableNode -> {
                val headers = node.headers.map { text(it, context) }
                val rows = tableRows(node.children, context)
                val count = headers.size.takeIf { it > 0 } ?: rows.firstOrNull()?.size ?: 0
                require(rows.all { it.size == count } && (node.columnWeights == null || node.columnWeights.size == count) &&
                    (node.columnAlignments == null || node.columnAlignments.size == count)) { "Table columns must match" }
                listOf(ReceiptBlock.Table(headers, rows, node.columnWeights, node.columnAlignments,
                    style(meta, RctFontSize.SMALL, bold = true), style(meta, RctFontSize.SMALL)))
            }
        } }

    private fun tableRows(nodes: List<RctNode>, context: RctRenderContext): List<List<String>> = nodes.flatMap { node ->
        when (node) {
            is RctRowNode -> listOf(node.columns.map { text(it, context) })
            is RctIfNode -> if (engine.isTruthy(node.path, context)) tableRows(node.children, context) else emptyList()
            is RctForEachNode -> contexts(node, context).flatMap { tableRows(node.children, it) }
            else -> throw IllegalArgumentException("Tables only support rows, if and for blocks")
        }
    }

    private fun contexts(node: RctForEachNode, context: RctRenderContext): List<RctRenderContext> {
        val value = engine.resolveValue(node.collectionPath, context)
        val items = when (value) {
            null -> emptyList()
            is Iterable<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> throw IllegalArgumentException("Collection required: ${node.collectionPath}")
        }
        return items.mapIndexed { index, item -> context.copy(locals = context.locals + mapOf(
            node.itemName to item, "@item" to item, "@index" to index, "@first" to (index == 0),
            "@last" to (index == items.lastIndex), "@count" to items.size
        )) }
    }

    private fun validateNodes(nodes: List<RctNode>) {
        val filters = setOf("upper", "lower", "default", "maskPan", "mask", "money", "date", "datetime", "padLeft", "padRight", "padCenter")
        nodes.forEach { node ->
            val templates = when (node) {
                is RctTextNode -> listOf(node.text)
                is RctKeyValueNode -> listOf(node.key, node.value)
                is RctRowNode -> node.columns
                is RctTableNode -> node.headers
                is RctImageNode -> listOf(node.source)
                is RctQrNode -> listOf(node.content)
                is RctBarcodeNode -> listOf(node.content)
                else -> emptyList()
            }
            templates.flatMap { it.parts }.filterIsInstance<RctBindingPart>().flatMap { it.filters }.forEach {
                require(it.name in filters) { "Unknown filter: ${it.name}" }
            }
            when (node) {
                is RctTableNode -> {
                    require(node.columnWeights?.all { it.isFinite() && it > 0 } != false) { "Table weights must be positive" }
                    validateNodes(node.children)
                }
                is RctIfNode -> validateNodes(node.children)
                is RctForEachNode -> validateNodes(node.children)
                is RctQrNode -> require(node.size > 0) { "QR size must be positive" }
                is RctBarcodeNode -> require(node.width > 0 && node.height > 0) { "Barcode dimensions must be positive" }
                is RctImageNode -> require((node.width ?: 1) > 0 && (node.height ?: 1) > 0) { "Image dimensions must be positive" }
                else -> Unit
            }
        }
    }
}

/** A parsed template with no transaction data retained between renders. */
class CompiledReceiptTemplate internal constructor(
    val document: RctDocument,
    private val renderDocument: (RctDocument, Any?) -> Receipt
) {
    fun render(data: Any?): Receipt = renderDocument(document, data)
}
