package io.github.donnclab.rct.escpos

import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import io.github.donnclab.rct.*

/**
 * Encodes text-only receipts as plain lines for printers or displays that accept raw text in a
 * single-byte [charset]. Images, QR codes and barcodes are rejected.
 */
public class PlainTextEncoder(
    private val columns: Int = 32,
    private val charset: Charset = Charsets.US_ASCII
) {
    init {
        require(columns in 4..512 && charset.newEncoder().maxBytesPerChar() <= 1f)
    }

    public fun encode(receipt: ReceiptDocument): ByteArray {
        val columns = minOf(this.columns, receipt.meta?.width ?: this.columns)
        require(columns > 0) { "Receipt width must be positive" }
        val lines = receipt.blocks.flatMap { block -> when (block) {
            is ReceiptBlock.Text -> TextLayout.wrap(block.text, columns).map {
                TextLayout.align(it, columns, when (block.align) {
                    ReceiptAlign.LEFT -> ReceiptColumnAlign.START
                    ReceiptAlign.CENTER -> ReceiptColumnAlign.CENTER
                    ReceiptAlign.RIGHT -> ReceiptColumnAlign.END
                })
            }
            is ReceiptBlock.KeyValue -> TextLayout.keyValue(block.key, block.value, columns)
            is ReceiptBlock.Table -> TextLayout.table(
                (if (block.headers.isEmpty()) emptyList() else listOf(block.headers)) + block.rows,
                block.weights, block.alignments, columns
            )
            is ReceiptBlock.Row -> TextLayout.table(listOf(block.columns), null, null, columns)
            is ReceiptBlock.Divider -> {
                val length = block.length.coerceIn(0, columns)
                val pattern = block.pattern.ifBlank { "-" }
                listOf(TextLayout.align(buildString { while (this.length < length) append(pattern.take(length - this.length)) }, columns, ReceiptColumnAlign.CENTER))
            }
            is ReceiptBlock.Spacer -> {
                require(block.lines in 0..10000)
                List(block.lines) { "" }
            }
            else -> throw IllegalArgumentException("Plain text cannot render ${block.javaClass.simpleName}; choose a graphics driver")
        } }
        require(lines.none { line -> line.any { it.code < 32 || it.code in 127..159 } }) { "Text contains a printer control character" }
        val buffer = try {
            charset.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT).onMalformedInput(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(lines.joinToString("\n", postfix = "\n")))
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Text cannot be encoded as ${charset.name()}", error)
        }
        return ByteArray(buffer.remaining()).also(buffer::get)
    }
}
