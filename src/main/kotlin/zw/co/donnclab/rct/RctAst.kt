package zw.co.donnclab.rct

data class RctDocument(
    val name: String,
    val meta: RctMeta?,
    val body: List<RctNode>,
    val header: List<RctNode> = emptyList(),
    val footer: List<RctNode> = emptyList(),
    val headerReferences: List<RctFragmentReference> = emptyList(),
    val footerReferences: List<RctFragmentReference> = emptyList()
)

data class RctFragmentReference(
    val id: String
)

data class RctMeta(
    val width: Int? = null,
    val paper: String? = null,
    val copy: String? = null,
    val templateVersion: Int? = null,
    val font: Float? = null,
    val fontScaleMode: RctFontScaleMode = RctFontScaleMode.RELATIVE,
    val extra: Map<String, String> = emptyMap()
)

sealed interface RctNode

data class RctTextNode(
    val text: RctTemplateString,
    val align: RctAlign = RctAlign.LEFT,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val fontSize: RctFontSize = RctFontSize.MIDDLE,
    val customFontSize: Float? = null
) : RctNode

data class RctKeyValueNode(
    val key: RctTemplateString,
    val value: RctTemplateString,
    val fontSize: RctFontSize = RctFontSize.MIDDLE,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val customFontSize: Float? = null
) : RctNode

data class RctDividerNode(
    val style: RctDividerStyle = RctDividerStyle.SOLID,
    val pattern: String = "-",
    val length: Int? = null
) : RctNode

data class RctSpacerNode(
    val lines: Int = 1
) : RctNode

data class RctRowNode(
    val columns: List<RctTemplateString>
) : RctNode

data class RctTableNode(
    val headers: List<RctTemplateString> = emptyList(),
    val columnWeights: List<Float>? = null,
    val columnAlignments: List<RctColumnAlignment>? = null,
    val children: List<RctNode>
) : RctNode

data class RctImageNode(
    val source: RctTemplateString,
    val width: Int? = null,
    val height: Int? = null
) : RctNode

data class RctQrNode(
    val content: RctTemplateString,
    val size: Int = 300
) : RctNode

data class RctBarcodeNode(
    val content: RctTemplateString,
    val width: Int = 300,
    val height: Int = 60,
    val format: BarcodeFormat = BarcodeFormat.CODE128
) : RctNode

enum class BarcodeFormat(val id: String) {
    CODE128("code128"), CODE39("code39"), EAN13("ean13"), EAN8("ean8"), UPCA("upca"), ITF("itf")
}

data class RctIfNode(
    val path: String,
    val children: List<RctNode>
) : RctNode

data class RctForEachNode(
    val itemName: String,
    val collectionPath: String,
    val children: List<RctNode>
) : RctNode

enum class RctAlign {
    LEFT,
    CENTER,
    RIGHT
}

enum class RctFontSize {
    SMALL,
    MIDDLE,
    BIG
}

enum class RctFontScaleMode {
    RELATIVE,
    FIXED
}

enum class RctDividerStyle {
    SOLID
}

enum class RctColumnAlignment {
    START,
    CENTER,
    END
}

data class RctTemplateString(
    val parts: List<RctTemplatePart>
) {
    init {
        require(parts.isNotEmpty()) { "Template string must contain at least one part." }
    }
}

sealed interface RctTemplatePart

data class RctLiteralPart(
    val text: String
) : RctTemplatePart

data class RctBindingPart(
    val path: String,
    val filters: List<RctFilterCall> = emptyList()
) : RctTemplatePart

data class RctFilterCall(
    val name: String,
    val args: List<String> = emptyList()
)
