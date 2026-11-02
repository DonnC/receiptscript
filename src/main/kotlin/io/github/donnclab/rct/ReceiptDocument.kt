package io.github.donnclab.rct

/**
 * A template bound to data: an ordered list of printer-neutral [ReceiptBlock]s.
 *
 * This is the platform-independent result of [RctCompiler]. On Android you normally work with
 * [Receipt], which carries the same [blocks] plus the rendered bitmap bands.
 */
public data class ReceiptDocument(
    /** The name declared by `receipt "<name>"` in the template. */
    public val name: String,
    /** Layout defaults declared in the template's `meta:` section, if any. */
    public val meta: ReceiptMeta?,
    /** Content in print order: header, body, then footer. */
    public val blocks: List<ReceiptBlock>
)

/**
 * One printable element. A `ReceiptDriver` that maps blocks to a printer SDK
 * handles each subtype in a `when` expression; the compiler checks that every type is covered.
 *
 * Every string has already been bound to data and passed through its filters.
 */
public sealed interface ReceiptBlock {
    /** A paragraph of text. May contain line breaks; drivers wrap it to the paper width. */
    public data class Text(val text: String, val align: ReceiptAlign, val style: ReceiptTextStyle) : ReceiptBlock

    /** A label on the left and a value on the right, for example `TOTAL ........ 10.00`. */
    public data class KeyValue(val key: String, val value: String, val style: ReceiptTextStyle) : ReceiptBlock

    /** A horizontal rule made by repeating [pattern] up to [length] characters. `"_"` means a solid line. */
    public data class Divider(val pattern: String, val length: Int) : ReceiptBlock

    /** A single row of equal-width columns, outside a table. */
    public data class Row(val columns: List<String>) : ReceiptBlock

    /** Vertical space measured in text lines. */
    public data class Spacer(val lines: Int = 1) : ReceiptBlock

    /**
     * Columns with optional headers. [weights] and [alignments] are either null or have one entry
     * per column; null means equal widths and centred cells.
     */
    public data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val weights: List<Float>?,
        val alignments: List<ReceiptColumnAlign>?,
        val headerStyle: ReceiptTextStyle,
        val rowStyle: ReceiptTextStyle
    ) : ReceiptBlock

    /** An image reference, resolved by your `ReceiptImageResolver`. Sizes are in dots. */
    public data class Image(val source: String, val width: Int?, val height: Int?) : ReceiptBlock

    /** A QR code of [content], [size] dots square. */
    public data class QrCode(val content: String, val size: Int) : ReceiptBlock

    /** A one-dimensional barcode, [width] by [height] dots. */
    public data class Barcode(
        val content: String,
        val width: Int,
        val height: Int,
        val format: ReceiptBarcodeFormat
    ) : ReceiptBlock
}

/** Text styling. [font] is an explicit size in dots and, when set, takes precedence over [size]. */
public data class ReceiptTextStyle(
    val size: ReceiptFontSize = ReceiptFontSize.MIDDLE,
    val font: Float? = null,
    val bold: Boolean = false,
    val underline: Boolean = false
)

/** Layout defaults from a template's `meta:` section. Unknown keys are kept in [extra]. */
public data class ReceiptMeta(
    /** Character columns used for dividers and native text layout. */
    val width: Int? = null,
    val paper: String? = null,
    val copy: String? = null,
    val templateVersion: Int? = null,
    /** Base font size in dots. */
    val font: Float? = null,
    val fontScaleMode: ReceiptFontScaleMode = ReceiptFontScaleMode.RELATIVE,
    val extra: Map<String, String> = emptyMap()
)

public enum class ReceiptAlign { LEFT, CENTER, RIGHT }

public enum class ReceiptColumnAlign { START, CENTER, END }

/** `small`, normal and `big` text. */
public enum class ReceiptFontSize { SMALL, MIDDLE, BIG }

/** Whether `small`/`big` scale relative to `meta.font` or keep it fixed. */
public enum class ReceiptFontScaleMode { RELATIVE, FIXED }

public enum class ReceiptBarcodeFormat(public val id: String) {
    CODE128("code128"), CODE39("code39"), EAN13("ean13"), EAN8("ean8"), UPCA("upca"), ITF("itf")
}
