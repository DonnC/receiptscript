package io.github.donnclab.rct

// The template syntax tree. Internal: hosts work with ReceiptDocument and ReceiptBlock instead.

internal data class RctDocument(
    val name: String,
    val meta: ReceiptMeta?,
    val body: List<RctNode>,
    val header: List<RctNode> = emptyList(),
    val footer: List<RctNode> = emptyList(),
    val headerReferences: List<RctFragmentReference> = emptyList(),
    val footerReferences: List<RctFragmentReference> = emptyList()
)

internal data class RctFragmentReference(
    val id: String
)

internal sealed interface RctNode

internal data class RctTextNode(
    val text: RctTemplateString,
    val align: ReceiptAlign = ReceiptAlign.LEFT,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val fontSize: ReceiptFontSize = ReceiptFontSize.MIDDLE,
    val customFontSize: Float? = null
) : RctNode

internal data class RctKeyValueNode(
    val key: RctTemplateString,
    val value: RctTemplateString,
    val fontSize: ReceiptFontSize = ReceiptFontSize.MIDDLE,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val customFontSize: Float? = null
) : RctNode

internal data class RctDividerNode(
    val style: RctDividerStyle = RctDividerStyle.SOLID,
    val pattern: String = "-",
    val length: Int? = null
) : RctNode

internal data class RctSpacerNode(
    val lines: Int = 1
) : RctNode

internal data class RctRowNode(
    val columns: List<RctTemplateString>
) : RctNode

internal data class RctTableNode(
    val headers: List<RctTemplateString> = emptyList(),
    val columnWeights: List<Float>? = null,
    val columnAlignments: List<ReceiptColumnAlign>? = null,
    val children: List<RctNode>
) : RctNode

internal data class RctImageNode(
    val source: RctTemplateString,
    val width: Int? = null,
    val height: Int? = null
) : RctNode

internal data class RctQrNode(
    val content: RctTemplateString,
    val size: Int = 300
) : RctNode

internal data class RctBarcodeNode(
    val content: RctTemplateString,
    val width: Int = 300,
    val height: Int = 60,
    val format: ReceiptBarcodeFormat = ReceiptBarcodeFormat.CODE128
) : RctNode

internal data class RctIfNode(
    val path: String,
    val children: List<RctNode>
) : RctNode

internal data class RctForEachNode(
    val itemName: String,
    val collectionPath: String,
    val children: List<RctNode>
) : RctNode

internal enum class RctDividerStyle {
    SOLID
}

internal data class RctTemplateString(
    val parts: List<RctTemplatePart>
) {
    init {
        require(parts.isNotEmpty()) { "Template string must contain at least one part." }
    }
}

internal sealed interface RctTemplatePart

internal data class RctLiteralPart(
    val text: String
) : RctTemplatePart

internal data class RctBindingPart(
    val path: String,
    val filters: List<RctFilterCall> = emptyList()
) : RctTemplatePart

internal data class RctFilterCall(
    val name: String,
    val args: List<String> = emptyList()
)
